package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfProblem
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandContent
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.ShareCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The PDF in the band package, protocol version 4 (T-PDF 3): it leaves with the song, is checked on arrival, and an app
 * still on version 3 says "newer version" instead of opening it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfBandPackageTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    private val pdfs = PdfStore(context)
    private val head = "%PDF-1.7\n".toByteArray()
    private val json = Json { ignoreUnknownKeys = true }

    private class FakeCounter(var pages: Int = 5, var fail: Exception? = null) : PdfPageCounter {
        override fun pageCount(file: File): Int = fail?.let { throw it } ?: pages
    }

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun link(repo: LibraryRepository, counter: PdfPageCounter = FakeCounter(), inbox: BandInbox = BandInbox(context)) =
        BandLink(context, identity, BandTrust(context), StageGate(), inbox, repo, pdfs, PdfInspector(counter))

    private fun pdfBytes(size: Int) = head + ByteArray(size - head.size) { (it % 251).toByte() }

    /** A song with a PDF of [size] bytes in the library; returns the shared song the senders hand to the link. */
    private fun songWithPdf(repo: LibraryRepository, title: String, size: Int = 2_000): SetlistCloud.SharedSong {
        val name = "$title.pdf"
        pdfs.file(name).writeBytes(pdfBytes(size))
        val id = repo.saveSong(Song(title = title, author = "Ana", text = "lyrics", pdf = name, pdfName = "Chart $title.pdf"))
        return SetlistCloud(repo, identity).songsPackage(listOf(id))!!.second.single()
    }

    private fun looseList(vararg titles: String) = SetlistCloud.looseSongs(titles.map { Song(title = it, author = "Ana", text = "x") })!!.first

    private fun wire(l: BandLink, repo: LibraryRepository, songs: List<SetlistCloud.SharedSong>) = l.packageBytes(looseList(*songs.map { it.title }.toTypedArray()), songs)

    // ---- version 4 and the version 3 app ----

    /** What the app published with version 3 reads of a package: its rule copied (`v > 3` says "update the app"). */
    @Serializable private data class V3Hello(val id: String, val v: Int = 2)
    @Serializable private data class V3Pack(val list: String, val songs: List<JsonElement> = emptyList(), val v: Int = 2)
    private fun v3SaysUpdate(packageJson: String) = json.decodeFromString<V3Pack>(packageJson).v > 3

    @Test fun `the protocol is version 5 and a Hello from this app is newer to apps on versions 3 and 4`() {
        assertEquals(5, BandProtocol.VERSION)
        assertEquals(BandProtocol.VERSION, identity.hello().v)
        assertTrue(json.decodeFromString<V3Hello>(json.encodeToString(BandProtocol.Hello.serializer(), identity.hello())).v > 4)
    }

    @Test fun `a package with a PDF carries v 4 and an app on version 3 says update`() {
        val repo = repo()
        val song = songWithPdf(repo, "Chart")
        val text = wire(link(repo), repo, listOf(song)).decodeToString()
        assertEquals("4", Json.parseToJsonElement(text).jsonObject["v"]?.jsonPrimitive?.content)
        assertTrue(v3SaysUpdate(text))
    }

    @Test fun `a package without a PDF still opens on an app on version 3`() {
        val repo = repo()
        val plain = SetlistCloud.SharedSong("Plain", "Ana", "", "lyrics")
        val text = wire(link(repo), repo, listOf(plain)).decodeToString()
        assertNull(Json.parseToJsonElement(text).jsonObject["v"])
        assertFalse(v3SaysUpdate(text))
        // a song whose PDF file is gone also goes as plain text, and the version stays low
        val lost = plain.copy(title = "Gone", pdfFile = "nowhere.pdf", pdfName = "x.pdf")
        assertFalse(v3SaysUpdate(wire(link(repo), repo, listOf(lost)).decodeToString()))
    }

    @Test fun `this app opens a version 3 package`() {
        val repo = repo()
        val l = link(repo)
        val v3 = """{"list":"${java.util.Base64.getEncoder().encodeToString(looseList("Old"))}","songs":[{"title":"Old","text":"x"}],"v":3}"""
        assertNotNull(l.openPackage(v3.toByteArray()))
        assertNotNull(l.openPackage(v3.replace("\"v\":3", "\"v\":5").toByteArray()))
        val v6 = v3.replace("\"v\":3", "\"v\":6")
        assertNull(l.openPackage(v6.toByteArray()))
    }

    // ---- leaving and arriving ----

    @Test fun `a PDF leaves with the song and arrives checked and saved under another name`() {
        val sender = repo(); val receiverInbox = BandInbox(context)
        val song = songWithPdf(sender, "Chart", size = 3_000)
        val bytes = wire(link(sender), sender, listOf(song))
        val receiver = link(repo(), FakeCounter(pages = 7), receiverInbox)
        assertTrue(receiver.accept("ana", "Ana", bytes))
        val got = receiverInbox.items.value.single().songs.single()
        assertEquals("", got.pdf)                       // the bytes are never kept in the inbox
        assertEquals("", got.pdfProblem)
        assertEquals("Chart Chart.pdf", got.pdfName)
        assertTrue(got.pdfFile.isNotEmpty() && got.pdfFile != "Chart.pdf")   // a generated name, never the sender's
        assertTrue(pdfs.file(got.pdfFile).readBytes().contentEquals(pdfBytes(3_000)))
    }

    @Test fun `a PDF that fails the check is dropped and the song arrives without it, with the reason`() {
        val sender = repo()
        val song = songWithPdf(sender, "Chart")
        val bytes = wire(link(sender), sender, listOf(song))
        val cases = listOf(
            FakeCounter(fail = SecurityException("password")) to PdfProblem.PASSWORD,
            FakeCounter(pages = 301) to PdfProblem.TOO_MANY_PAGES,
            FakeCounter(fail = java.io.IOException("damaged")) to PdfProblem.UNREADABLE,
        )
        for ((counter, problem) in cases) {
            val inbox = BandInbox(context).also { b -> b.items.value.forEach { b.remove(it.id) } }
            val before = File(context.filesDir, "pdfs").list().orEmpty().toSet()
            assertTrue(link(repo(), counter, inbox).accept("ana", "Ana", bytes))
            val got = inbox.items.value.last().songs.single()
            assertEquals(problem.name, got.pdfProblem)
            assertEquals("", got.pdfFile)
            assertEquals("lyrics", got.text)   // the song itself still arrives
            assertEquals(before, File(context.filesDir, "pdfs").list().orEmpty().toSet())   // nothing left of the refused file
        }
    }

    @Test fun `bytes that are not a PDF, or not even base64, are refused`() {
        val inbox = BandInbox(context)
        val l = link(repo(), inbox = inbox)
        val fake = java.util.Base64.getEncoder().encodeToString("MZ this is a program".toByteArray())
        val taken = l.takePdfs(listOf(
            SetlistCloud.SharedSong("A", pdf = fake, pdfName = "a.pdf"),
            SetlistCloud.SharedSong("B", pdf = "***not base64***", pdfName = "b.pdf"),
        ))
        assertEquals(listOf(PdfProblem.NOT_PDF.name, PdfProblem.NOT_PDF.name), taken.map { it.pdfProblem })
        assertTrue(taken.all { it.pdfFile.isEmpty() && it.pdf.isEmpty() })
    }

    @Test fun `a PDF over 8 MB is refused and so is what passes the package limit`() {
        val l = link(repo())
        val big = java.util.Base64.getEncoder().encodeToString(pdfBytes(PdfStore.MAX_BYTES.toInt() + 1))
        assertEquals(PdfProblem.TOO_BIG.name, l.takePdfs(listOf(SetlistCloud.SharedSong("Big", pdf = big))).single().pdfProblem)
        // two of 6 MB: the first fits the package, the second passes its limit and comes without
        val six = java.util.Base64.getEncoder().encodeToString(pdfBytes(6 * 1024 * 1024))
        val taken = l.takePdfs(listOf(SetlistCloud.SharedSong("One", pdf = six), SetlistCloud.SharedSong("Two", pdf = six)))
        assertEquals("", taken[0].pdfProblem)
        assertTrue(taken[0].pdfFile.isNotEmpty())
        assertEquals(PdfProblem.TOO_BIG.name, taken[1].pdfProblem)
    }

    @Test fun `what the sender says about files on this device is never believed`() {
        val l = link(repo())
        val taken = l.takePdfs(listOf(SetlistCloud.SharedSong("Spoof", pdfFile = "../../databases/secret", pdfName = "../../x", pdfProblem = "UNREADABLE")))
        val s = taken.single()
        assertEquals("", s.pdfFile)
        assertEquals("", s.pdfProblem)
        assertEquals("", s.pdfName)
        // a name that comes with a real PDF is only a name: no folders, short
        val named = l.takePdfs(listOf(SetlistCloud.SharedSong("N", pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes(500)), pdfName = "../../etc/${"x".repeat(300)}.pdf"))).single()
        assertTrue(named.pdfName.length <= BandContent.MAX_PDF_NAME)
        assertFalse(named.pdfName.contains('/'))
    }

    @Test fun `a package from someone who is not in the band is refused before any PDF is saved`() {
        val sender = repo()
        val bytes = wire(link(sender), sender, listOf(songWithPdf(sender, "Chart")))
        val inbox = BandInbox(context)
        val l = link(repo(), inbox = inbox)
        val before = File(context.filesDir, "pdfs").list().orEmpty().toSet()
        val receive = BandLink::class.java.getDeclaredMethod("receive", BandProtocol.Hello::class.java, String::class.java).apply { isAccessible = true }
        val stranger = BandProtocol.Hello(identity.id, "Stranger", "k")
        assertEquals(false, receive.invoke(l, stranger, ShareCode.encrypt(ByteArray(32), bytes)))
        assertTrue(inbox.items.value.isEmpty())
        assertEquals(before, File(context.filesDir, "pdfs").list().orEmpty().toSet())
    }

    // ---- limits ----

    @Test fun `only the PDFs that fit the package go, in order, and the rest go without`() {
        val repo = repo(); val l = link(repo)
        val songs = (1..3).map { songWithPdf(repo, "S$it", size = 6 * 1024 * 1024) }
        val (wire, any) = l.withPdfBytes(songs)
        assertTrue(any)
        assertEquals(listOf(true, false, false), wire.map { it.pdf.isNotEmpty() })
    }

    @Test fun `a package filled with PDFs to the limit still fits the line of 20 MB`() {
        val repo = repo(); val l = link(repo)
        val a = songWithPdf(repo, "A", size = 4 * 1024 * 1024)
        val rest = (BandContent.PDF_PACKAGE_BYTES - 4L * 1024 * 1024).toInt()
        val b = songWithPdf(repo, "B", size = rest)
        val plain = wire(l, repo, listOf(a, b))
        val encrypted = ShareCode.encrypt(ByteArray(32), plain)
        assertTrue("the line has ${encrypted.length} characters", encrypted.length <= BandProtocol.MAX_MESSAGE)
        assertEquals(2, l.withPdfBytes(listOf(a, b)).first.count { it.pdf.isNotEmpty() })
        // 6 MB of PDFs in one package: the number the screens say
        assertEquals(6L * 1024 * 1024, BandContent.PDF_PACKAGE_BYTES)
    }

    @Test fun `the internet and the QR carry no PDF yet - the song goes as text`() {
        val repo = repo()
        val song = songWithPdf(repo, "Chart")
        assertTrue(song.pdfFile.isNotEmpty())   // the sender knows the file...
        assertEquals("", song.forWire().pdfFile)   // ...and the other ways of sending never write it
        val cloud = SetlistCloud(repo, identity)
        val pair = cloud.pairPackage(looseList("Chart"), listOf(song))
        val opened = cloud.openPairPackage(pair)!!
        assertTrue(opened.songs.all { it.pdf.isEmpty() && it.pdfFile.isEmpty() && it.pdfName.isEmpty() })
        assertNull(Json.parseToJsonElement(SetlistCloud.unpack(byteArrayOf('P'.code.toByte(), '1'.code.toByte()), pair, SetlistCloud.MAX_PACKAGE)!!).jsonObject["v"])
    }
}
