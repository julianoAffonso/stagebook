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
}
