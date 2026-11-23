package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfProblem
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandContent
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.ui.share.ShareLimits
import br.app.stagebook.ui.share.ShareOptions
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText

/** The PDF over the internet (user, 22/11): compressed and encrypted as an attachment of the server, kept there 3 hours. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfInternetTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    private val pdfs = PdfStore(context)
    @get:Rule val compose = createComposeRule()

    private class Counter(var fail: Exception? = null) : PdfPageCounter {
        override fun pageCount(file: File): Int = fail?.let { throw it } ?: 2
    }

    private fun repo() = testRepo(empty = true)

    // a PDF whose bytes don't compress (random), the worst case for a message
    private fun pdfBytes(size: Int) = "%PDF-1.7\n".toByteArray() + java.util.Random(7).let { r -> ByteArray(size - 9).also(r::nextBytes) }

    private fun cloud(repo: LibraryRepository, counter: Counter = Counter()) = SetlistCloud(repo, identity, pdfs, PdfInspector(counter))

    private fun song(repo: LibraryRepository, size: Int): String {
        pdfs.file("c-$size.pdf").writeBytes(pdfBytes(size))
        return repo.saveSong(Song(title = "Chart", author = "Ana", text = "lyrics", pdf = "c-$size.pdf", pdfName = "Chart.pdf"))
    }

    /** The server's attachments in the tests: what went up, by address, and the topics they went next to. */
    private val files = LinkedHashMap<String, ByteArray>()
    private val fileTopics = mutableListOf<String>()
    private val fetched = mutableListOf<String>()

    private fun SetlistCloud.withServer(takes: Boolean = true) = apply {
        attachHook = { topic, body -> if (!takes) null else "https://ntfy.sh/file/${files.size}.bin".also { files[it] = body; fileTopics += topic } }
        fetchHook = { url -> fetched += url; files[url] }
    }

    /** What a send by code posts, in order. */
    private fun send(c: SetlistCloud, ids: List<String>): Pair<SetlistCloud.Sent, List<String>> {
        val posted = mutableListOf<String>()
        c.postHook = { _, body -> posted += body; true }
        val sent = runBlocking { c.sendSongs(ids)!! }
        return sent to posted
    }

    /** What the other side opens, fetching the attachments from the same test server. */
    private fun receive(c: SetlistCloud, sent: SetlistCloud.Sent, posted: List<String>): SetlistCloud.Received.Ok {
        val key = ShareCode.keys(sent.code).key
        return c.withServer().read(plainOf(sent.code, posted)) { url -> files[url]?.let { ShareCode.decrypt(key, it.decodeToString()) } } as SetlistCloud.Received.Ok
    }

    private fun plainOf(code: String, posted: List<String>) = ShareCode.keys(code).key.let { k -> posted.mapNotNull { ShareCode.decrypt(k, it) } }

    // ---- the limits ----

    @Test fun `the real numbers - each PDF up to 8 MB, a send up to 30 MB, kept 3 hours`() {
        assertTrue(BandContent.internetPdfsFit(listOf(BandContent.MAX_PDF_BYTES)))
        assertFalse(BandContent.internetPdfsFit(listOf(BandContent.MAX_PDF_BYTES + 1)))
        assertTrue(BandContent.internetPdfsFit(List(3) { BandContent.MAX_PDF_BYTES }))
        assertFalse(BandContent.internetPdfsFit(List(4) { BandContent.MAX_PDF_BYTES }))
        assertEquals(3, BandContent.INTERNET_PDF_HOURS)
    }

    @Test fun `the old fit rule is unchanged, and a PDF takes no message of its own`() {
        assertTrue(BandContent.internetFits(lists = 3, songs = 56))
        assertFalse(BandContent.internetFits(lists = 3, songs = 57))
    }

    // ---- the send and the receive ----

    @Test fun `a PDF goes encrypted as an attachment next to the channel, and comes back checked and saved`() {
        val repo = repo(); val c = cloud(repo).withServer()
        val size = 200_000
        val (sent, posted) = send(c, listOf(song(repo, size)))
        assertEquals(1, sent.pdfs)
        assertEquals("the card, the song, the list: the PDF takes no message", 3, posted.size)
        assertTrue(posted.all { it.length <= 4_000 })
        assertEquals(ShareCode.keys(sent.code).topic + "-f", fileTopics.single())
        assertFalse("the server sees no PDF", files.values.single().decodeToString().contains("%PDF"))
        val got = receive(cloud(repo()), sent, posted).songs.single()
        assertEquals("", got.pdfProblem)
        assertEquals("Chart.pdf", got.pdfName)
        assertTrue(pdfs.file(got.pdfFile).readBytes().contentEquals(pdfBytes(size)))
        assertEquals("", got.pdfAt)
    }

    @Test fun `a send with a PDF is version 5 and a send without one is not`() {
        val repo = repo(); val c = cloud(repo).withServer()
        val (withPdf, posted) = send(c, listOf(song(repo, 5_000)))
        assertEquals(5, (cloud(repo()).read(plainOf(withPdf.code, posted)) as SetlistCloud.Received.Ok).from!!.v)
        val plain = repo.saveSong(Song(title = "Plain", author = "Bia", text = "x"))
        val (without, posted2) = send(c, listOf(plain))
        assertEquals(2, (cloud(repo()).read(plainOf(without.code, posted2)) as SetlistCloud.Received.Ok).from!!.v)
    }

    @Test fun `a PDF past the limit, or one the server doesn't take, is left out and the songs still go`() {
        val repo = repo()
        val (sent, posted) = send(cloud(repo).withServer(), listOf(song(repo, (BandContent.MAX_PDF_BYTES + 1).toInt())))
        assertEquals(0, sent.pdfs)
        assertTrue(files.isEmpty())
        assertEquals("Chart", receive(cloud(repo()), sent, posted).songs.single().title)
        val (refused, posted2) = send(cloud(repo).withServer(takes = false), listOf(song(repo, 5_000)))
        assertEquals(0, refused.pdfs)
        assertEquals(3, posted2.size)
        assertEquals("", receive(cloud(repo()), refused, posted2).songs.single().pdfFile)
    }

    @Test fun `without the store no PDF goes`() {
        val repo = repo()
        val (sent, posted) = send(SetlistCloud(repo, identity).withServer(), listOf(song(repo, 5_000)))
        assertEquals(0, sent.pdfs)
        assertEquals(3, posted.size)
    }

    @Test fun `a PDF gone from the server comes as the reason, and one the check refuses says why`() {
        val repo = repo(); val c = cloud(repo).withServer()
        val (sent, posted) = send(c, listOf(song(repo, 10_000)))
        files.clear()   // past the 3 hours
        val gone = receive(cloud(repo()), sent, posted).songs.single()
        assertEquals(PdfProblem.EXPIRED.name, gone.pdfProblem)
        assertEquals("", gone.pdfFile)
        val (again, posted2) = send(c, listOf(song(repo, 10_000)))
        val locked = receive(cloud(repo(), Counter(SecurityException("password"))), again, posted2).songs.single()
        assertEquals(PdfProblem.PASSWORD.name, locked.pdfProblem)
    }

    @Test fun `an address that is not the server's own file is never fetched`() {
        val repo = repo(); val c = cloud(repo).withServer()
        val song = SetlistCloud.SharedSong("Spoof", "Ana", "", "x", pdfAt = "https://example.com/file/x.bin", pdfName = "x.pdf")
        val plain = listOf(
            SetlistCloud.pack(byteArrayOf('S'.code.toByte(), '1'.code.toByte()), sparseJson.encodeToString(SetlistCloud.SharedSong.serializer(), song)),
            br.app.stagebook.domain.QrPayload.pack(SetlistCloud.looseList(listOf("Spoof" to "Ana"))),
        )
        val got = (c.read(plain) as SetlistCloud.Received.Ok).songs.single()
        assertEquals("", got.pdfFile)
        assertTrue(fetched.isEmpty())
    }

    @Test fun `a version 4 send, its PDF in chunks, is still read`() {
        val repo = repo(); val c = cloud(repo)
        val bytes = pdfBytes(5_000)
        val parts = bytes.toList().chunked(2_400)
        val key = SetlistCloud.songKey("Chart", "Ana")
        val chunks = parts.mapIndexed { i, part ->
            SetlistCloud.pack(byteArrayOf('F'.code.toByte(), '1'.code.toByte()), """{"k":"$key","i":$i,"n":${parts.size},"d":"${java.util.Base64.getEncoder().encodeToString(part.toByteArray())}","name":"${if (i == 0) "Chart.pdf" else ""}"}""")
        }
        val plain = listOf(
            SetlistCloud.pack(byteArrayOf('S'.code.toByte(), '1'.code.toByte()), """{"title":"Chart","author":"Ana","text":"x"}"""),
        ) + chunks + br.app.stagebook.domain.QrPayload.pack(SetlistCloud.looseList(listOf("Chart" to "Ana")))
        val got = (c.read(plain) as SetlistCloud.Received.Ok).songs.single()
        assertTrue(pdfs.file(got.pdfFile).readBytes().contentEquals(bytes))
    }

    @Test fun `what a sender writes about files on this device is not believed`() {
        val repo = repo(); val c = cloud(repo)
        val song = SetlistCloud.SharedSong("Spoof", "Ana", "", "x", pdfFile = "../../databases/x", pdfProblem = "TOO_BIG", pdfName = "x.pdf")
        val plain = listOf(
            SetlistCloud.pack(byteArrayOf('S'.code.toByte(), '1'.code.toByte()), sparseJson.encodeToString(SetlistCloud.SharedSong.serializer(), song)),
            br.app.stagebook.domain.QrPayload.pack(SetlistCloud.looseList(listOf("Spoof" to "Ana"))),
        )
        val got = (c.read(plain) as SetlistCloud.Received.Ok).songs.single()
        assertEquals("", got.pdfFile)
        assertEquals("", got.pdfProblem)
    }

    // ---- the dialog ----

    @Test @Config(qualifiers = "en")
    fun `the send sheet says the numbers when the PDFs do not fit and the songs still go`() {
        val sizes = listOf(9_000_000L)
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = sizes)) } }
        compose.onNodeWithText("The PDFs add up to 9 MB, and the internet takes up to 30 MB per send (8 MB each).", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun `the send sheet says the numbers in Portuguese`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = listOf(9_000_000L))) } }
        compose.onNodeWithText("Os PDFs somam 9 MB, e pela internet vão até 30 MB por envio (8 MB cada).", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun `the send sheet warns that the PDFs last only 3 hours on the server`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = listOf(2_000_000L))) } }
        compose.onNodeWithText("Os PDFs vão junto, compactados. O servidor guarda eles só por 3 horas: quem abrir depois recebe as músicas sem o PDF.").assertExists()
    }

    @Test fun `sending to the band with PDFs says a member far away gets them through the internet for 3 hours`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = listOf(20_000L))) } }
        compose.onNodeWithText("recebe pela internet, onde eles ficam só por 3 horas", substring = true).assertExists()
    }

    @Test fun `without PDFs the band option says nothing about them`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 1, songs = 1)) } }
        compose.onAllNodesWithText("sem o PDF", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("3 horas", substring = true).assertCountEquals(0)
    }

    @Test fun `PDFs that add up to more than a band send takes say how much and that some go without`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 1, songs = 2, pdfSizes = listOf(7_000_000L, 6_000_000L))) } }
        compose.onNodeWithText("somam 13 MB", substring = true).assertExists()
    }

    // ---- a band member's inbox over the internet ----

    private fun loose(vararg titles: String) = SetlistCloud.looseSongs(titles.map { Song(title = it, author = "Ana", text = "x") })!!.first
    private val pairMark = byteArrayOf('P'.code.toByte(), '1'.code.toByte())
    private fun versionOf(pkg: ByteArray) = kotlinx.serialization.json.Json.parseToJsonElement(SetlistCloud.unpack(pairMark, pkg, SetlistCloud.MAX_PACKAGE)!!)
        .let { (it as kotlinx.serialization.json.JsonObject)["v"]?.let { v -> (v as kotlinx.serialization.json.JsonPrimitive).content.toInt() } ?: 2 }

    @Test fun `a PDF rides in the pair package as an attachment, fetched and saved on the other side, and the package is version 5`() {
        val repo = repo(); val c = cloud(repo)
        val bytes = pdfBytes(300_000)
        val shared = c.songsPackage(listOf(song(repo, 300_000)))!!.second
        val pkg = c.pairPackage(loose("Chart"), shared, mapOf(shared.single() to "https://ntfy.sh/file/a.bin"))
        assertEquals(5, versionOf(pkg))
        val got = c.openPairPackage(pkg) { url -> if (url == "https://ntfy.sh/file/a.bin") bytes else null }!!.songs.single()
        assertTrue(pdfs.file(got.pdfFile).readBytes().contentEquals(bytes))
        assertEquals("Chart.pdf", got.pdfName)
        assertEquals("", got.pdfAt)
        val gone = c.openPairPackage(pkg) { null }!!.songs.single()
        assertEquals(PdfProblem.EXPIRED.name, gone.pdfProblem)
        // a package with no attachment stays as before
        assertEquals(2, versionOf(c.pairPackage(loose("Chart"), shared)))
    }

    @Test fun `the PDFs of one pair package keep to its budget`() {
        val repo = repo(); val c = cloud(repo)
        val small = song(repo, 20_000)
        val shared = c.songsPackage(listOf(small))!!.second
        assertEquals(1, c.pairPdfs(shared).size)
        assertEquals(15L * 1024 * 1024, BandContent.PAIR_PDF_BYTES)
    }

    // an older app's package carried its PDF inside, in base64: still read, with the same checks
    private fun inlinePackage(c: SetlistCloud, pdf: String, text: String? = null): ByteArray {
        val real = SetlistCloud.unpack(pairMark, c.pairPackage(loose("Chart"), listOf(SetlistCloud.SharedSong("Chart", "Ana", "", "x"))), SetlistCloud.MAX_PACKAGE)!!
        return SetlistCloud.pack(pairMark, kotlinx.serialization.json.buildJsonObject {
            kotlinx.serialization.json.Json.parseToJsonElement(real).let { it as kotlinx.serialization.json.JsonObject }.forEach { (k, v) ->
                if (k != "songs") put(k, v) else put(k, kotlinx.serialization.json.JsonArray((v as kotlinx.serialization.json.JsonArray).map { sg ->
                    kotlinx.serialization.json.buildJsonObject {
                        (sg as kotlinx.serialization.json.JsonObject).forEach { (a, b) -> if (a == "text" && text != null) put(a, kotlinx.serialization.json.JsonPrimitive(text)) else put(a, b) }
                        put("pdf", kotlinx.serialization.json.JsonPrimitive(pdf)); put("pdfName", kotlinx.serialization.json.JsonPrimitive("x.pdf"))
                    }
                }))
            }
        }.toString())
    }

    @Test fun `an older pair package with a PDF inside is still read, and a fake or oversized one says why`() {
        val repo = repo(); val c = cloud(repo)
        val ok = c.openPairPackage(inlinePackage(c, java.util.Base64.getEncoder().encodeToString(pdfBytes(5_000))))!!.songs.single()
        assertTrue(ok.pdfFile.isNotEmpty())
        val fake = c.openPairPackage(inlinePackage(c, java.util.Base64.getEncoder().encodeToString("MZ not a pdf".toByteArray())))!!.songs.single()
        assertEquals("NOT_PDF", fake.pdfProblem)
        val before = java.io.File(context.filesDir, "pdfs").list()?.size ?: 0
        val over = c.openPairPackage(inlinePackage(c, java.util.Base64.getEncoder().encodeToString(pdfBytes(64_000))))!!.songs.single()
        assertEquals("TOO_BIG", over.pdfProblem)
        // a song whose text is not allowed (a control character) is turned away: its PDF is not kept either
        val turned = c.openPairPackage(inlinePackage(c, java.util.Base64.getEncoder().encodeToString(pdfBytes(5_000)), text = "a\u0000b"))!!.songs.single()
        assertEquals("", turned.pdfFile)
        assertEquals(before, java.io.File(context.filesDir, "pdfs").list()?.size ?: 0)
    }
}
