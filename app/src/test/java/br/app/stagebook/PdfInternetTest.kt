package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
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

/** The PDF over the internet (T-PDF 4): the count of messages, the reason when it doesn't fit, and the chunks that do. */
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

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    // a PDF whose bytes don't compress (random), the worst case for a message
    private fun pdfBytes(size: Int) = "%PDF-1.7\n".toByteArray() + java.util.Random(7).let { r -> ByteArray(size - 9).also(r::nextBytes) }

    private fun cloud(repo: LibraryRepository, counter: Counter = Counter()) = SetlistCloud(repo, identity, pdfs, PdfInspector(counter))

    private fun song(repo: LibraryRepository, size: Int): String {
        pdfs.file("c-$size.pdf").writeBytes(pdfBytes(size))
        return repo.saveSong(Song(title = "Chart", author = "Ana", text = "lyrics", pdf = "c-$size.pdf", pdfName = "Chart.pdf"))
    }

    /** What a send by code posts, in order. */
    private fun send(c: SetlistCloud, ids: List<String>): Pair<SetlistCloud.Sent, List<String>> {
        val posted = mutableListOf<String>()
        c.postHook = { _, body -> posted += body; true }
        val sent = runBlocking { c.sendSongs(ids)!! }
        return sent to posted
    }

    private fun plainOf(code: String, posted: List<String>) = ShareCode.keys(code).key.let { k -> posted.mapNotNull { ShareCode.decrypt(k, it) } }

    // ---- the count ----

    @Test fun `the real numbers - what one list and one song leave for the PDF`() {
        assertEquals(60, BandContent.MAX_INTERNET_MESSAGES)
        assertEquals(57, BandContent.internetMessagesFree(lists = 1, songs = 1))
        assertEquals(43, BandContent.internetMessagesFree(lists = 1, songs = 15))
        // 57 messages of 2,400 bytes: 136,800 bytes (133 KB) for one song; the next byte doesn't fit
        assertTrue(BandContent.internetFits(1, 1, listOf(57L * BandContent.INTERNET_PDF_CHUNK)))
        assertFalse(BandContent.internetFits(1, 1, listOf(57L * BandContent.INTERNET_PDF_CHUNK + 1)))
        assertEquals(1, BandContent.internetPdfMessages(1L))
        assertEquals(2, BandContent.internetPdfMessages(2_401L))
    }

    @Test fun `each PDF has its own chunks, so two small ones can need more than one big one`() {
        assertEquals(2, BandContent.internetPdfMessages(listOf(10L, 10L)))
        assertEquals(1, BandContent.internetPdfMessages(20L))
    }

    @Test fun `the old fit rule is unchanged without PDFs`() {
        assertTrue(BandContent.internetFits(lists = 3, songs = 56))
        assertFalse(BandContent.internetFits(lists = 3, songs = 57))
    }

    @Test fun `a message with a full chunk of a hard to squeeze PDF fits the server's 4 KB`() {
        val repo = repo(); val c = cloud(repo)
        val (sent, posted) = send(c, listOf(song(repo, BandContent.INTERNET_PDF_CHUNK * 3)))
        assertTrue(posted.all { it.length <= 4_000 })   // the 4,000 characters the sender checks (the server takes 4,096 bytes)
        assertEquals(1, sent.pdfs)
        // the card, the song, 3 chunks, the list
        assertEquals(1 + 1 + 3 + 1, posted.size)
    }

    // ---- the send and the receive ----

    @Test fun `a PDF that fits goes in chunks and is put back together, checked and saved, on the other side`() {
        val repo = repo(); val c = cloud(repo)
        val size = 20_000
        val (sent, posted) = send(c, listOf(song(repo, size)))
        val got = (cloud(repo()).read(plainOf(sent.code, posted)) as SetlistCloud.Received.Ok).songs.single()
        assertEquals("", got.pdfProblem)
        assertEquals("Chart.pdf", got.pdfName)
        assertTrue(got.pdfFile.isNotEmpty())
        assertTrue(pdfs.file(got.pdfFile).readBytes().contentEquals(pdfBytes(size)))
        assertEquals("", got.pdf)
    }

    @Test fun `a send with a PDF is version 4 and a send without one is not`() {
        val repo = repo(); val c = cloud(repo)
        val (withPdf, posted) = send(c, listOf(song(repo, 5_000)))
        val hello = { p: List<String>, code: String -> (cloud(repo()).read(plainOf(code, p)) as SetlistCloud.Received.Ok).from!!.v }
        assertEquals(4, hello(posted, withPdf.code))
        val plain = repo.saveSong(Song(title = "Plain", author = "Bia", text = "x"))
        val (without, posted2) = send(c, listOf(plain))
        assertEquals(2, hello(posted2, without.code))
    }

    @Test fun `a PDF that does not fit the 60 messages is left out and the songs still go`() {
        val repo = repo(); val c = cloud(repo)
        val tooBig = 57 * BandContent.INTERNET_PDF_CHUNK + 1
        val (sent, posted) = send(c, listOf(song(repo, tooBig)))
        assertEquals(0, sent.pdfs)
        assertEquals(1 + 1 + 1, posted.size)   // the card, the song, the list: no chunk
        assertTrue(posted.size <= BandContent.MAX_INTERNET_MESSAGES)
        val got = (cloud(repo()).read(plainOf(sent.code, posted)) as SetlistCloud.Received.Ok).songs.single()
        assertEquals("", got.pdfFile)
        assertEquals("Chart", got.title)
    }

    @Test fun `without the store no PDF goes`() {
        val repo = repo()
        val id = song(repo, 5_000)
        val (sent, posted) = send(SetlistCloud(repo, identity), listOf(id))
        assertEquals(0, sent.pdfs)
        assertEquals(3, posted.size)
    }

    @Test fun `a missing chunk, or a file that is not a PDF, never gives a PDF`() {
        val repo = repo(); val c = cloud(repo)
        val (sent, posted) = send(c, listOf(song(repo, 10_000)))
        val plain = plainOf(sent.code, posted)
        // the 3rd message is a chunk: drop it
        val missing = plain.filterIndexed { i, _ -> i != 3 }
        assertEquals("", (cloud(repo()).read(missing) as SetlistCloud.Received.Ok).songs.single().pdfFile)
        // everything arrives but the other side's check says a password
        val locked = cloud(repo(), Counter(SecurityException("password")))
        val got = (locked.read(plain) as SetlistCloud.Received.Ok).songs.single()
        assertEquals(PdfProblem.PASSWORD.name, got.pdfProblem)
        assertEquals("", got.pdfFile)
    }

    @Test fun `what a sender writes about files on this device is not believed`() {
        val repo = repo(); val c = cloud(repo)
        val song = SetlistCloud.SharedSong("Spoof", "Ana", "", "x", pdfFile = "../../databases/x", pdfProblem = "TOO_BIG", pdfName = "x.pdf")
        val plain = listOf(
            SetlistCloud.pack(byteArrayOf('S'.code.toByte(), '1'.code.toByte()), kotlinx.serialization.json.Json { encodeDefaults = false }.encodeToString(SetlistCloud.SharedSong.serializer(), song)),
            br.app.stagebook.domain.QrPayload.pack(SetlistCloud.looseList(listOf("Spoof" to "Ana"))),
        )
        val got = (c.read(plain) as SetlistCloud.Received.Ok).songs.single()
        assertEquals("", got.pdfFile)
        assertEquals("", got.pdfProblem)
    }

    // ---- the dialog ----

    @Test @Config(qualifiers = "en")
    fun `the send sheet says the numbers when the PDFs do not fit and the songs still go`() {
        val sizes = listOf(500_000L)   // 209 messages
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = sizes)) } }
        compose.onNodeWithText("The PDFs need 209 messages of 4 KB, and the internet server takes 60 per send (57 are left after the songs).", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun `the send sheet says the numbers in Portuguese`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = listOf(500_000L))) } }
        compose.onNodeWithText("Os PDFs precisam de 209 mensagens de 4 KB, e o servidor da internet aceita 60 por envio (sobram 57 depois das músicas).", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "en")
    fun `the send sheet says the PDFs go along when they fit`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = listOf(24_000L))) } }
        compose.onNodeWithText("The PDFs go along: 13 of the 60 messages the server takes.").assertExists()
    }

    @Test fun `sending to the band with PDFs says a member far away gets the song without them`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 1, songs = 1, pdfSizes = listOf(20_000L))) } }
        compose.onNodeWithText("recebe a música sem o PDF", substring = true).assertExists()
    }

    @Test fun `without PDFs the band option says nothing about them`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 1, songs = 1)) } }
        compose.onAllNodesWithText("sem o PDF", substring = true).assertCountEquals(0)
    }

    @Test fun `PDFs that add up to more than a band send takes say how much and that some go without`() {
        compose.setContent { StagebookTheme { ShareOptions(1, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 1, songs = 2, pdfSizes = listOf(7_000_000L, 6_000_000L))) } }
        compose.onNodeWithText("somam 13 MB", substring = true).assertExists()
    }
}
