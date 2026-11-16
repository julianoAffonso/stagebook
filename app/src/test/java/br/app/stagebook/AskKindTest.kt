package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.AskKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A request to the band says which copy it wants: lyrics only, the chart with chords, or the PDF (user, 15/11). The device that
 * answers sends that copy, or nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AskKindTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pdfs = PdfStore(context)
    private val chords = "{key: G}\n[G]La la [D]la\n[C]La la la"

    private class Pages : PdfPageCounter { override fun pageCount(file: File): Int = 2 }

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun link(repo: LibraryRepository) = BandLink(context, DeviceIdentity(context), BandTrust(context), StageGate(), BandInbox(context), repo, pdfs, PdfInspector(Pages()))

    @Test fun `the kind of a song is its PDF, else its chords, else its lyrics`() {
        assertEquals(AskKind.PDF, AskKind.of(Song(title = "A", text = chords, pdf = "a.pdf")))
        assertEquals(AskKind.CHORDS, AskKind.of(Song(title = "B", text = chords)))
        assertEquals(AskKind.LYRICS, AskKind.of(Song(title = "C", text = "La la\nLa la")))
    }

    @Test fun `a song has what is asked for only when it does`() {
        val withChords = Song(title = "B", text = chords)
        val plain = Song(title = "C", text = "La la\nLa la")
        assertTrue(AskKind.has(withChords, AskKind.CHORDS, false))
        assertFalse(AskKind.has(plain, AskKind.CHORDS, false))
        assertTrue("lyrics are in a chart too", AskKind.has(withChords, AskKind.LYRICS, false))
        assertFalse(AskKind.has(withChords, AskKind.PDF, false))
        assertTrue(AskKind.has(withChords, AskKind.PDF, true))
        assertTrue("a request from before the kinds takes what there is", AskKind.has(plain, AskKind.ANY, false))
    }

    @Test fun `lyrics only goes without the chords, the chart goes as it is`() {
        val lyrics = AskKind.textFor(chords, AskKind.LYRICS)
        assertFalse(lyrics.contains("[G]"))
        assertTrue(lyrics.contains("La la"))
        assertEquals(chords, AskKind.textFor(chords, AskKind.CHORDS))
        assertEquals(chords, AskKind.textFor(chords, AskKind.ANY))
    }

    @Test fun `the profile asks for chords when it shows them and lyrics when it does not`() {
        assertEquals(AskKind.CHORDS, AskKind.default(true))
        assertEquals(AskKind.LYRICS, AskKind.default(false))
    }

    @Test fun `a device answers each request with the copy asked for, or with nothing`() {
        val repo = repo()
        val l = link(repo)
        pdfs.file("c.pdf").writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(2_000) { 7 })
        repo.saveSong(Song(title = "Cifrada", author = "Ana", text = chords))
        repo.saveSong(Song(title = "Letra", author = "Ana", text = "La la la\nLa la la\nLa la la"))
        repo.saveSong(Song(title = "Partitura", author = "Ana", text = "só letra\nsó letra", pdf = "c.pdf", pdfName = "Partitura.pdf"))
        fun answer(title: String, kind: String) = l.shapedAnswer(listOf(BandLink.Wanted(title, "Ana", kind)))
        // the chart with chords: only the one that has them
        assertEquals(1, answer("Cifrada", AskKind.CHORDS).size)
        assertTrue(answer("Letra", AskKind.CHORDS).isEmpty())
        // lyrics only: the chords are left out of what goes
        val lyrics = answer("Cifrada", AskKind.LYRICS).single()
        assertFalse(lyrics.text.contains("[G]"))
        assertTrue(lyrics.text.contains("La la"))
        // the PDF: its bytes go inside, only for the song that has one
        val pdf = answer("Partitura", AskKind.PDF).single()
        assertTrue(pdf.pdf.isNotEmpty())
        assertEquals("Partitura.pdf", pdf.pdfName)
        assertTrue(answer("Cifrada", AskKind.PDF).isEmpty())
        // a request without a kind (an older app) goes as it did: the song as it is, no PDF
        assertEquals(chords, answer("Cifrada", AskKind.ANY).single().text)
        assertTrue(answer("Partitura", AskKind.ANY).single().pdf.isEmpty())
        // and over the internet the same filter picks the songs
        assertEquals(1, l.songsFor(listOf(BandLink.Wanted("Partitura", "Ana", AskKind.PDF))).size)
        assertTrue(l.songsFor(listOf(BandLink.Wanted("Cifrada", "Ana", AskKind.PDF))).isEmpty())
    }

    /** What this device posts to a member's inbox for a request that came over the internet, as that member's app reads it. */
    private fun answeredOnline(repo: LibraryRepository, wanted: List<BandLink.Wanted>): List<br.app.stagebook.data.SetlistCloud.SharedSong> = runBlocking {
        val identity = DeviceIdentity(context)
        val cloud = br.app.stagebook.data.SetlistCloud(repo, identity, pdfs, PdfInspector(Pages()))
        val band = br.app.stagebook.data.BandInternet(context, cloud, BandTrust(context), identity, BandInbox(context), br.app.stagebook.data.Connectivity(context), link(repo))
        val key = ByteArray(32) { it.toByte() }
        val posted = mutableListOf<String>()
        cloud.postHook = { _, body -> posted += body; true }
        band.answerRequest(key, "ana", wanted)
        cloud.getHook = { posted.mapIndexed { i, body -> """{"event":"message","id":"$i","message":"$body"}""" } }
        cloud.fetchPair(key, null)!!.packages.flatMap { it.third }
    }

    @Test fun `over the internet a request is answered with the copy asked for too - lyrics only goes without the chords`() {
        val repo = repo()
        pdfs.file("net.pdf").writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(2_000) { 7 })
        repo.saveSong(Song(title = "Cifrada", author = "Ana", text = chords))
        repo.saveSong(Song(title = "Letra", author = "Ana", text = "La la la\nLa la la\nLa la la"))
        repo.saveSong(Song(title = "Partitura", author = "Ana", text = chords, pdf = "net.pdf", pdfName = "Partitura.pdf"))
        fun answer(title: String, kind: String) = answeredOnline(repo, listOf(BandLink.Wanted(title, "Ana", kind)))
        // lyrics only: the chords are left out of what goes (they went along)
        val lyrics = answer("Cifrada", AskKind.LYRICS).single()
        assertFalse("the chords went along: ${lyrics.text}", lyrics.text.contains("[G]"))
        assertTrue(lyrics.text.contains("La la"))
        // the chart, and a request from before the kinds: the song as it is
        assertEquals(chords, answer("Cifrada", AskKind.CHORDS).single().text)
        assertEquals(chords, answer("Cifrada", AskKind.ANY).single().text)
        // chords asked for and the song has none: no answer
        assertTrue(answer("Letra", AskKind.CHORDS).isEmpty())
        // the PDF goes only when the PDF was asked for: with the lyrics it would take their place on the stage
        val asLyrics = answer("Partitura", AskKind.LYRICS).single()
        assertEquals("", asLyrics.pdfFile)
        assertFalse(asLyrics.text.contains("[G]"))
        val asPdf = answer("Partitura", AskKind.PDF).single()
        assertTrue("the PDF asked for arrives and is saved", asPdf.pdfFile.isNotEmpty() && pdfs.file(asPdf.pdfFile).isFile)
        // the same song asked for twice goes once, as the first copy asked for
        val twice = answeredOnline(repo, listOf(BandLink.Wanted("Cifrada", "Ana", AskKind.LYRICS), BandLink.Wanted("Cifrada", "Ana", AskKind.CHORDS)))
        assertFalse(twice.single().text.contains("[G]"))
    }

    @Test fun `an answer with a PDF is checked and saved here, one that is not a PDF is left out with the reason`() {
        val repo = repo()
        val l = link(repo)
        val real = "%PDF-1.7\n".toByteArray() + ByteArray(2_000) { 3 }
        fun answer(bytes: ByteArray) = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(br.app.stagebook.data.SetlistCloud.SharedSong.serializer()),
            listOf(br.app.stagebook.data.SetlistCloud.SharedSong("Partitura", "Ana", "", "x\nx\nx", pdf = java.util.Base64.getEncoder().encodeToString(bytes), pdfName = "p.pdf")),
        ).toByteArray()
        val ok = l.readAnswer(answer(real)).single()
        assertTrue(ok.pdfFile.isNotEmpty() && pdfs.file(ok.pdfFile).isFile)
        val bad = l.readAnswer(answer("MZ not a pdf".toByteArray())).single()
        assertEquals("", bad.pdfFile)
        assertEquals("NOT_PDF", bad.pdfProblem)
    }

    @Test fun `a song that is only a PDF answers a request for the PDF, never one for text or with no kind`() {
        val repo = repo()
        val l = link(repo)
        pdfs.file("so.pdf").writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(500) { 7 })
        repo.saveSong(Song(title = "Só PDF", author = "Ana", text = "", pdf = "so.pdf", pdfName = "So.pdf"))
        fun answer(kind: String) = l.shapedAnswer(listOf(BandLink.Wanted("Só PDF", "Ana", kind)))
        assertTrue("it went as an empty song, and the PDF was never asked for", answer(AskKind.ANY).isEmpty())
        assertTrue(answer(AskKind.LYRICS).isEmpty())
        assertTrue(answer(AskKind.CHORDS).isEmpty())
        assertTrue(answer(AskKind.PDF).single().pdf.isNotEmpty())
        // over the internet the same: nothing that would arrive empty
        assertTrue(l.songsFor(listOf(BandLink.Wanted("Só PDF", "Ana"))).isEmpty())
    }

    @Test fun `a PDF asked for that can't go is left out of the answer, not sent as an empty song`() {
        val repo = repo()
        val l = link(repo)
        pdfs.file("some.pdf").writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(500) { 7 })
        val id = repo.saveSong(Song(title = "Partitura", author = "Ana", text = "", pdf = "some.pdf"))
        val wanted = listOf(BandLink.Wanted("Partitura", "Ana", AskKind.PDF))
        // another answer with PDFs is being made: this one goes without them
        assertTrue(l.shapedAnswer(wanted, pdfs = false).isEmpty())
        // the file is gone meanwhile
        pdfs.file("some.pdf").delete()
        assertTrue(l.shapedAnswer(wanted).isEmpty())
        assertEquals(id, repo.findSong("Partitura", "Ana")?.id)
    }

    @Test fun `a PDF nobody asked for is not taken from an answer, and unused files are deleted`() {
        val repo = repo()
        val l = link(repo)
        val bytes = java.util.Base64.getEncoder().encodeToString("%PDF-1.7\n".toByteArray() + ByteArray(800) { 3 })
        fun answer() = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(br.app.stagebook.data.SetlistCloud.SharedSong.serializer()),
            listOf(br.app.stagebook.data.SetlistCloud.SharedSong("Letra", "Ana", "", "la la\nla la", pdf = bytes, pdfName = "x.pdf")),
        ).toByteArray()
        val before = pdfs.file("x").parentFile!!.listFiles()?.size ?: 0
        // lyrics were asked for: the PDF that came along is not kept, and the lyrics stay the song
        val unsolicited = l.readAnswer(answer()) { false }.single()
        assertEquals("", unsolicited.pdfFile)
        assertEquals(before, pdfs.file("x").parentFile!!.listFiles()?.size ?: 0)
        // asked for as a PDF: saved; and when the answer ends up not used, its file goes
        val asked = l.readAnswer(answer()) { true }.single()
        assertTrue(pdfs.file(asked.pdfFile).isFile)
        l.dropPdfs(listOf(asked))
        assertFalse(pdfs.file(asked.pdfFile).exists())
    }
}
