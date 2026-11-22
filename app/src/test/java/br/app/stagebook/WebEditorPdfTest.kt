package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.data.web.WebEditorServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The editor on the computer with a PDF (T-PDF 5): the same card, the same check and the same rules as the app's editor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorPdfTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var repo: LibraryRepository
    private val pdfs = PdfStore(context)
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)

    private class Pages(var fail: Exception? = null) : PdfPageCounter {
        override fun pageCount(file: File): Int = fail?.let { throw it } ?: 6
    }
    private val counter = Pages()

    @Before fun setUp() {
        repo = testRepo()
    }

    private fun api() = WebEditorApi(repo, pdfs = { pdfs }, inspector = PdfInspector(counter), workDir = { context.cacheDir })

    private fun call(api: WebEditorApi, method: String, path: String, body: String = "", file: File? = null) =
        api.handle(HttpRequest(method, path.substringBefore('?'), path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') }, body = body.toByteArray(), file = file), device)!!

    private fun upload(bytes: ByteArray = "%PDF-1.4\n".toByteArray() + ByteArray(3_000)) = File(context.cacheDir, "up-${System.nanoTime()}.part").apply { writeBytes(bytes) }

    @Test fun `a PDF sent from the computer is linked to the song with its name, pages and size, and the lyrics stay`() {
        val id = repo.saveSong(Song(title = "Cifra", text = "[G]letra guardada"))
        val api = api()
        val r = call(api, "PUT", "/api/songs/$id/pdf?name=Cifra%20do%20Vento.pdf", file = upload())
        assertEquals(200, r.status)
        assertTrue(r.text(), "\"pdfPages\":6" in r.text())
        val song = repo.song(id)!!
        assertTrue(pdfs.file(song.pdf).isFile)
        assertEquals("[G]letra guardada", song.text)
        // the page opens the song with what the card shows
        val data = call(api, "GET", "/api/songs/$id").text()
        assertTrue(data, "\"pdfPages\":6" in data && "\"pdfBytes\":3009" in data && "\"pdf\":\"${song.pdf}\"" in data)
        // saving from the page (which sends the hidden lyrics) keeps the PDF
        val saved = call(api, "PUT", "/api/songs/$id", """{"id":"$id","title":"Cifra","text":"[G]letra guardada"}""")
        assertEquals(200, saved.status)
        assertEquals(song.pdf, repo.song(id)!!.pdf)
    }

    @Test fun `replacing deletes the old file and the old marks, removing brings the lyrics back`() {
        val id = repo.saveSong(Song(title = "Cifra", text = "[G]letra"))
        val api = api()
        call(api, "PUT", "/api/songs/$id/pdf?name=a.pdf", file = upload())
        val first = repo.song(id)!!.pdf
        repo.updateSong(id) { it.copy(pdfMarks = listOf(br.app.stagebook.data.model.PdfMark(0, listOf(br.app.stagebook.data.model.PdfPoint(0.2f, 0.2f))))) }
        call(api, "PUT", "/api/songs/$id/pdf?name=b.pdf", file = upload())
        val second = repo.song(id)!!
        assertFalse(pdfs.file(first).exists())
        assertTrue(second.pdfMarks.isEmpty())
        assertEquals("b.pdf", second.pdfName)
        assertEquals(204, call(api, "DELETE", "/api/songs/$id/pdf").status)
        val back = repo.song(id)!!
        assertEquals("", back.pdf)
        assertEquals("[G]letra", back.text)
        assertFalse(pdfs.file(second.pdf).exists())
    }

    @Test fun `a refused PDF answers with the reason and keeps nothing`() {
        val id = repo.saveSong(Song(title = "Cifra", text = "x"))
        val api = api()
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val before = dir.list().orEmpty().toSet()
        assertTrue("pdf_not_pdf" in call(api, "PUT", "/api/songs/$id/pdf?name=foto.pdf", file = upload("MZ not a pdf".toByteArray())).text())
        counter.fail = SecurityException("password")
        val locked = call(api, "PUT", "/api/songs/$id/pdf?name=a.pdf", file = upload())
        assertEquals(409, locked.status)
        assertTrue("pdf_password" in locked.text())
        counter.fail = java.io.IOException("damaged")
        assertTrue("pdf_unreadable" in call(api, "PUT", "/api/songs/$id/pdf?name=a.pdf", file = upload()).text())
        assertEquals("", repo.song(id)!!.pdf)
        assertEquals(before, dir.list().orEmpty().toSet())
    }

    private fun withPdf(): Pair<WebEditorApi, String> {
        val id = repo.saveSong(Song(title = "Cifra", text = "[G]letra [D]guardada"))
        val api = WebEditorApi(repo, pdfs = { pdfs }, inspector = PdfInspector(counter), workDir = { context.cacheDir }, pageImage = { _, n -> byteArrayOf(0xFF.toByte(), 0xD8.toByte(), n.toByte()) })
        assertEquals(200, call(api, "PUT", "/api/songs/$id/pdf?name=a.pdf", file = upload()).status)
        return api to id
    }

    @Test fun `the invert choice and the trims are set by their own call, kept on the song and read back with it`() {
        val (api, id) = withPdf()
        // as the page opens it: it follows Settings (off here), nothing trimmed
        assertTrue(call(api, "GET", "/api/songs/$id").text().let { "\"pdfInvert\":false" in it && "\"pdfCrops\":[]" in it })
        val body = """{"invert":true,"crops":[{"page":-1,"left":0.1,"top":0.0,"right":0.9,"bottom":1.0},{"page":2,"left":0.0,"top":0.5,"right":1.0,"bottom":1.0}]}"""
        assertEquals(200, call(api, "PUT", "/api/songs/$id/pdf-view", body).status)
        val song = repo.song(id)!!
        assertEquals(true, song.pdfInvert)
        assertEquals(listOf(-1, 2), song.pdfCrops.map { it.page })
        assertTrue(call(api, "GET", "/api/songs/$id").text().let { "\"pdfInvert\":true" in it && "\"page\":2" in it })
        // a call that sends only one of them leaves the other as it was
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"invert":false}""")
        assertEquals(false, repo.song(id)!!.pdfInvert)
        assertEquals(2, repo.song(id)!!.pdfCrops.size)
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"crops":[]}""")
        assertTrue(repo.song(id)!!.pdfCrops.isEmpty())
    }

    @Test fun `pages left off the stage are real pages, never all of them, and go with the PDF`() {
        val (api, id) = withPdf()
        assertEquals(200, call(api, "PUT", "/api/songs/$id/pdf-view", """{"hidden":[4,1,1,99,-2]}""").status)
        assertEquals(listOf(1, 4), repo.song(id)!!.pdfHidden)
        assertTrue(call(api, "GET", "/api/songs/$id").text().contains("\"pdfHidden\":[1,4]"))
        // all six hidden: refused, what was kept stays
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"hidden":[0,1,2,3,4,5]}""")
        assertEquals(listOf(1, 4), repo.song(id)!!.pdfHidden)
        call(api, "DELETE", "/api/songs/$id/pdf")
        assertTrue(repo.song(id)!!.pdfHidden.isEmpty())
    }

    @Test fun `a page from before these fields saves the song without wiping them`() {
        val (api, id) = withPdf()
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"invert":true,"crops":[{"page":-1,"left":0.2,"top":0.0,"right":1.0,"bottom":1.0}]}""")
        assertEquals(200, call(api, "PUT", "/api/songs/$id", """{"id":"$id","title":"Cifra","text":"[G]letra [D]guardada"}""").status)
        assertEquals(true, repo.song(id)!!.pdfInvert)
        assertEquals(1, repo.song(id)!!.pdfCrops.size)
    }

    @Test fun `a trim sent out of the page is held to it, a whole one is dropped, and one per page`() {
        val (api, id) = withPdf()
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"crops":[{"page":0,"left":-3,"top":0.95,"right":0.0,"bottom":9},{"page":1,"left":0,"top":0,"right":1,"bottom":1},{"page":0,"left":0.3,"top":0,"right":1,"bottom":1}]}""")
        val kept = repo.song(id)!!.pdfCrops
        assertEquals(1, kept.size)
        assertTrue(kept.single().let { it.left in 0f..0.9f && it.top in 0f..0.9f && it.right - it.left >= 0.099f && it.bottom - it.top >= 0.099f })
    }

    @Test fun `the view of a song without a PDF is not found, and replacing or removing the PDF takes the trims off`() {
        val plain = repo.saveSong(Song(title = "Outra", text = "x"))
        assertEquals(404, call(api(), "PUT", "/api/songs/$plain/pdf-view", """{"invert":true}""").status)
        val (api, id) = withPdf()
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"invert":true,"crops":[{"page":-1,"left":0.2,"top":0.0,"right":1.0,"bottom":1.0}]}""")
        call(api, "PUT", "/api/songs/$id/pdf?name=b.pdf", file = upload())
        assertTrue("another file: the trims of the old one's pages go", repo.song(id)!!.pdfCrops.isEmpty())
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"crops":[{"page":-1,"left":0.2,"top":0.0,"right":1.0,"bottom":1.0}]}""")
        call(api, "DELETE", "/api/songs/$id/pdf")
        assertTrue(repo.song(id)!!.pdfCrops.isEmpty() && repo.song(id)!!.pdfInvert == null)
    }

    @Test fun `a page of the PDF comes as an image for the crop window, and a song without one has none`() {
        val (api, id) = withPdf()
        val r = call(api, "GET", "/api/songs/$id/pdf-page?n=3")
        assertEquals(200, r.status)
        assertEquals("image/jpeg", r.contentType)
        assertEquals(3, r.body[2].toInt())
        val plain = repo.saveSong(Song(title = "Outra", text = "x"))
        assertEquals(404, call(api, "GET", "/api/songs/$plain/pdf-page?n=0").status)
    }

    @Test fun `the key of a PDF song steps from where it is, starting at C, and moves no chord`() {
        val api = api()
        fun step(key: String, n: Int) = call(api, "POST", "/api/key-step", """{"key":"$key","semitones":$n}""").text()
        assertTrue(step("", 1), "\"key\":\"C\"" in step("", 1))
        assertEquals(br.app.stagebook.domain.Chords.transposeKey("G", 1), kotlinx.serialization.json.Json.parseToJsonElement(step("G", 1)).let { (it as kotlinx.serialization.json.JsonObject)["key"]!!.let { k -> (k as kotlinx.serialization.json.JsonPrimitive).content } })
    }

    @Test fun `without a store or for a song that is not there the call says not found`() {
        val id = repo.saveSong(Song(title = "Cifra"))
        assertEquals(404, call(WebEditorApi(repo, workDir = { context.cacheDir }), "PUT", "/api/songs/$id/pdf?name=a.pdf", file = upload()).status)
        assertEquals(404, call(api(), "PUT", "/api/songs/nope/pdf?name=a.pdf", file = upload()).status)
        assertEquals(404, call(api(), "DELETE", "/api/songs/nope/pdf").status)
    }

    @Test fun `the page has the card and every text it uses comes from the app's strings in both languages`() {
        val html = context.assets.open("web/index.html").bufferedReader().readText()
        assertTrue("id=\"pdf-card\"" in html && "id=\"pdf-row\"" in html && "id=\"lyrics-block\"" in html)
        assertTrue("id=\"pdf-preview\"" in html && "id=\"pdf-invert\"" in html && "id=\"pdf-crop\"" in html && "id=\"crop-dialog\"" in html)
        for (lang in listOf("en", "pt-BR")) {
            val c = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(lang)) }
            val texts = WebEditorServer.texts(context.createConfigurationContext(c), R.string.editor_lyrics)
            listOf("pdfLabel", "pdfNone", "pdfAttach", "pdfReplace", "pdfRemove", "pdfMissing", "pdfNote", "pdfAskLyrics", "pdfRemoveConfirm", "pdfNotPdf", "pdfTooBig", "pdfPassword", "pdfTooManyPages", "pdfUnreadable", "pdfFacts1", "pdfFactsN",
                "pdfPreview", "pdfInvert", "pdfCrop", "cropTitle", "cropHint", "cropAll", "cropPage", "cropNone", "cropPrev", "cropNext", "cancel")
                .forEach { assertTrue("$lang $it", !texts[it].isNullOrBlank()) }
            // the card says the same sentence as the app's editor
            assertEquals(context.createConfigurationContext(c).getString(R.string.editor_pdf_note), texts["pdfNote"])
        }
    }

    @Test fun `a trim for a page the PDF cannot have is dropped, one for all pages stays`() {
        val (api, id) = withPdf()
        call(api, "PUT", "/api/songs/$id/pdf-view", """{"crops":[{"page":900,"left":0.2,"top":0,"right":1,"bottom":1},{"page":-5,"left":0.2,"top":0,"right":1,"bottom":1},{"page":-1,"left":0.1,"top":0,"right":1,"bottom":1}]}""")
        assertEquals(listOf(-1), repo.song(id)!!.pdfCrops.map { it.page })
    }
}
