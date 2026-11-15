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
        repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
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

    @Test fun `without a store or for a song that is not there the call says not found`() {
        val id = repo.saveSong(Song(title = "Cifra"))
        assertEquals(404, call(WebEditorApi(repo, workDir = { context.cacheDir }), "PUT", "/api/songs/$id/pdf?name=a.pdf", file = upload()).status)
        assertEquals(404, call(api(), "PUT", "/api/songs/nope/pdf?name=a.pdf", file = upload()).status)
        assertEquals(404, call(api(), "DELETE", "/api/songs/nope/pdf").status)
    }

    @Test fun `the page has the card and every text it uses comes from the app's strings in both languages`() {
        val html = context.assets.open("web/index.html").bufferedReader().readText()
        assertTrue("id=\"pdf-card\"" in html && "id=\"pdf-row\"" in html && "id=\"lyrics-block\"" in html)
        for (lang in listOf("en", "pt-BR")) {
            val c = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(lang)) }
            val texts = WebEditorServer.texts(context.createConfigurationContext(c), R.string.editor_lyrics)
            listOf("pdfLabel", "pdfNone", "pdfAttach", "pdfReplace", "pdfRemove", "pdfMissing", "pdfNote", "pdfAskLyrics", "pdfRemoveConfirm", "pdfNotPdf", "pdfTooBig", "pdfPassword", "pdfTooManyPages", "pdfUnreadable", "pdfFacts1", "pdfFactsN")
                .forEach { assertTrue("$lang $it", !texts[it].isNullOrBlank()) }
            // the card says the same sentence as the app's editor
            assertEquals(context.createConfigurationContext(c).getString(R.string.editor_pdf_note), texts["pdfNote"])
        }
    }
}
