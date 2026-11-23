package br.app.stagebook

import br.app.stagebook.data.PdfStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A PDF is made smaller once, when it comes in; sending it sends the stored file (user, 22/11: "ao enviar compacta de
 * novo?"). One that was here before is tried on its first send, in its place, and never again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfForSendingTest {
    private val context = appContext()

    @Test fun `a PDF from before is tried once on sending, then left alone and nothing is left next to it`() {
        val store = PdfStore(context)
        val pdf = store.file("old.pdf").apply { writeBytes("%PDF-1.4\nnot really a pdf".toByteArray()) }
        val before = pdf.readBytes()
        val marks = File(context.noBackupFilesDir, "pdf-compact")
        store.compactOnce(pdf)
        assertTrue("nothing to gain: the file is as it was", before.contentEquals(pdf.readBytes()))
        assertEquals(listOf("old.pdf"), marks.list()!!.toList())
        val stamp = File(marks, "old.pdf").lastModified()
        store.compactOnce(pdf)
        assertEquals("not tried again", stamp, File(marks, "old.pdf").lastModified())
        assertEquals("no half copy stays in the folder", listOf("old.pdf"), pdf.parentFile!!.list()!!.toList())
    }

    @Test fun `the mark of a PDF that is gone goes with the next one`() {
        val store = PdfStore(context)
        val marks = File(context.noBackupFilesDir, "pdf-compact").apply { mkdirs() }
        File(marks, "gone.pdf").createNewFile()
        store.compactOnce(store.file("here.pdf").apply { writeBytes("%PDF-1.4\n".toByteArray()) })
        assertEquals(listOf("here.pdf"), marks.list()!!.toList())
    }
}
