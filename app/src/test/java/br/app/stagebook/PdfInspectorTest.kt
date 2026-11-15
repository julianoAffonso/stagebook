package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.PdfAttach
import br.app.stagebook.data.PdfCheck
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfProblem
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.text
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** The check a PDF passes before the app keeps it (Robolectric doesn't draw PDFs: a fake counts the pages). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfInspectorTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val head = "%PDF-1.7\n".toByteArray()

    private fun file(bytes: ByteArray) = File.createTempFile("insp", ".pdf", context.cacheDir).apply { writeBytes(bytes); deleteOnExit() }

    private fun inspector(pages: Int = 3, fail: Exception? = null) = PdfInspector(object : PdfPageCounter {
        override fun pageCount(file: File): Int = fail?.let { throw it } ?: pages
    })

    private fun refusal(c: PdfCheck) = (c as PdfCheck.Refused).problem

    @Test fun `a good PDF says its pages and size`() {
        val f = file(head + ByteArray(100))
        val ok = inspector(pages = 12).check(f) as PdfCheck.Ok
        assertEquals(12, ok.pages)
        assertEquals(f.length(), ok.bytes)
    }

    @Test fun `a file without the PDF header is refused as not a PDF`() {
        assertEquals(PdfProblem.NOT_PDF, refusal(inspector().check(file("PK zip".toByteArray()))))
        assertEquals(PdfProblem.NOT_PDF, refusal(inspector().check(file(ByteArray(0)))))
        assertEquals(PdfProblem.NOT_PDF, refusal(inspector().check(file("%PD".toByteArray()))))
    }

    @Test fun `the size limit is 8 MB exactly`() {
        assertTrue(inspector().check(file(head + ByteArray(PdfInspector.MAX_BYTES.toInt() - head.size))) is PdfCheck.Ok)
        assertEquals(PdfProblem.TOO_BIG, refusal(inspector().check(file(head + ByteArray(PdfInspector.MAX_BYTES.toInt() - head.size + 1)))))
    }

    @Test fun `the page limit is 300 exactly`() {
        assertTrue(inspector(pages = 300).check(file(head)) is PdfCheck.Ok)
        assertEquals(PdfProblem.TOO_MANY_PAGES, refusal(inspector(pages = 301).check(file(head))))
    }

    @Test fun `a PDF with a password is refused`() {
        assertEquals(PdfProblem.PASSWORD, refusal(inspector(fail = SecurityException("password")).check(file(head))))
    }

    @Test fun `a damaged PDF or one with no pages is refused as unreadable`() {
        assertEquals(PdfProblem.UNREADABLE, refusal(inspector(fail = IOException("broken")).check(file(head))))
        assertEquals(PdfProblem.UNREADABLE, refusal(inspector(fail = IllegalStateException("renderer")).check(file(head))))
        assertEquals(PdfProblem.UNREADABLE, refusal(inspector(pages = 0).check(file(head))))
    }

    @Test fun `every reason has a sentence and the numbers are the real limits`() {
        PdfProblem.entries.forEach { assertTrue(it.text(context).isNotBlank()) }
        assertTrue(PdfProblem.TOO_BIG.text(context).contains("8"))
        assertTrue(PdfProblem.TOO_MANY_PAGES.text(context).contains("300"))
    }

    @Test fun `attach copies a chosen file, and a missing or false one is refused with the reason`() = runBlocking {
        val store = PdfStore(context)
        val saved = store.attach("song-2", android.net.Uri.fromFile(file(head + ByteArray(10))), inspector(pages = 4)) as PdfAttach.Saved
        assertEquals(4, saved.pages)
        assertTrue(store.file(saved.name).isFile)
        val missing = store.attach("song-2", android.net.Uri.fromFile(File(context.cacheDir, "nowhere.pdf")), inspector())
        assertEquals(PdfProblem.UNREADABLE, (missing as PdfAttach.Refused).problem)
        val fake = store.attach("song-2", android.net.Uri.fromFile(file("PK zip".toByteArray())), inspector())
        assertEquals(PdfProblem.NOT_PDF, (fake as PdfAttach.Refused).problem)
    }

    @Test fun `saveChecked keeps a good PDF and leaves nothing of a refused one`() = runBlocking {
        val store = PdfStore(context)
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val before = dir.list().orEmpty().toSet()
        val saved = store.saveChecked("song-1", head + ByteArray(10), inspector(pages = 2)) as PdfAttach.Saved
        assertEquals(2, saved.pages)
        assertTrue(store.file(saved.name).isFile)
        val refused = store.saveChecked("song-1", head + ByteArray(10), inspector(fail = SecurityException("pw")))
        assertEquals(PdfProblem.PASSWORD, (refused as PdfAttach.Refused).problem)
        assertEquals(PdfProblem.NOT_PDF, (store.saveChecked("song-1", "nope".toByteArray(), inspector()) as PdfAttach.Refused).problem)
        assertEquals(before + saved.name, dir.list().orEmpty().toSet())   // no .part left behind
        assertFalse(dir.list().orEmpty().any { it.endsWith(".part") })
    }
}
