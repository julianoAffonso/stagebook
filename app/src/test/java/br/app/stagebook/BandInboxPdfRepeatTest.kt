package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.SetlistCloud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The same send arriving twice (user, 15/11 audit): one item, and the PDFs the repeat brought are not left on the disk. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BandInboxPdfRepeatTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dir = File(context.filesDir, "pdfs").apply { mkdirs() }

    private fun song(file: String) = SetlistCloud.SharedSong("Partitura", "Ana", "", "", pdfName = "p.pdf", pdfFile = file)
    private fun pdf(name: String) = File(dir, name).apply { writeBytes("%PDF-1.7".toByteArray()) }

    @Test fun `a repeat of the send is not a second item and its own PDF file goes`() {
        val inbox = BandInbox(context)
        val list = SetlistCloud.looseList(listOf("Partitura" to "Ana"))
        val first = pdf("first-1.pdf"); val again = pdf("again-2.pdf")
        inbox.add("voz", "Voz", list, listOf(song(first.name)))
        inbox.add("voz", "Voz", list, listOf(song(again.name)))
        assertEquals("one item, not two", 1, inbox.items.value.size)
        assertTrue("the first send keeps its PDF", first.exists())
        assertFalse("the repeat's PDF is not left behind", again.exists())
    }

    @Test fun `a different send keeps both PDFs`() {
        val inbox = BandInbox(context)
        val a = pdf("a-1.pdf"); val b = pdf("b-2.pdf")
        inbox.add("voz", "Voz", SetlistCloud.looseList(listOf("Partitura" to "Ana")), listOf(song(a.name)))
        inbox.add("voz", "Voz", SetlistCloud.looseList(listOf("Outra" to "Ana")), listOf(song(b.name).copy(title = "Outra")))
        assertEquals(2, inbox.items.value.size)
        assertTrue(a.exists() && b.exists())
    }
}
