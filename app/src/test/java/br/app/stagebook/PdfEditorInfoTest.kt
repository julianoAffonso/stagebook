package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.EditorQueue
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.editor.SongEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the editor's card says about the song's PDF (audit 16/11): the screen reads it while composing, and every read went to
 * the disk for the file. It is looked for off the main thread when the PDF changes, and the card is made of what was found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfEditorInfoTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pdfs = PdfStore(context)

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private class Pages(val n: Int = 4) : PdfPageCounter {
        override fun pageCount(file: File): Int = n
    }

    private fun bytes(size: Int) = "%PDF-1.4\n".toByteArray() + ByteArray(size)
    private fun picked(name: String, size: Int = 2_000) = android.net.Uri.fromFile(File(context.cacheDir, name).apply { writeBytes(bytes(size)) })

    private fun open(song: Song? = null): Pair<SongEditorViewModel, LibraryRepository> {
        val scope = testScope()
        val repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } }; repo.restore(emptyList(), emptyList()) }
        val args = mutableMapOf<String, Any?>("fromImport" to false)
        song?.let { args["songId"] = repo.saveSong(it) }
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(args), ImportQueue(repo), TrackStore(context), EditorQueue(), pdfs, PdfInspector(Pages()))
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        return vm to repo
    }

    private fun await(what: String, check: () -> Boolean) = runBlocking { withTimeout(5_000) { while (!check()) kotlinx.coroutines.delay(10) } }.let { assertTrue(what, check()) }

    @Test fun `a saved song's card gets its file, size and pages once, and reading the card does not go to the disk again`() {
        pdfs.file("salva.pdf").writeBytes(bytes(3_000))
        val (vm, _) = open(Song(title = "Chart", text = "x", pdf = "salva.pdf", pdfName = "Chart.pdf"))
        await("the file, its size and its pages arrive") { vm.pdfInfo!!.file != null && vm.pdfInfo!!.pages == 4 }
        assertEquals(pdfs.file("salva.pdf"), vm.pdfInfo!!.file)
        assertEquals(3_009L, vm.pdfInfo!!.bytes)
        assertFalse(vm.pdfInfo!!.lost)
        // the disk changes under the card: a read of the card is state, not a look at the disk (it was one on every composition)
        pdfs.file("salva.pdf").delete()
        assertNotNull(vm.pdfInfo!!.file)
    }

    @Test fun `a saved song whose file is not on this device has the card with no file`() {
        val (vm, _) = open(Song(title = "Chart", text = "x", pdf = "sumiu.pdf", pdfName = "Chart.pdf"))
        Thread.sleep(200)   // the look at the disk had its time
        assertEquals("Chart.pdf", vm.pdfInfo!!.name)
        assertNull(vm.pdfInfo!!.file)
        assertEquals(0, vm.pdfInfo!!.pages)
    }

    @Test fun `attaching, replacing and removing a PDF leave the card with the file, size and pages of the PDF there is`() = runBlocking {
        val (vm, repo) = open(Song(title = "Chart", text = "x"))
        assertNull(vm.pdfInfo)
        vm.attachPdf(picked("um.pdf", 2_000))
        // right after attaching, with no wait: what is saved next must not see the PDF before
        val first = vm.pdfInfo!!
        assertEquals("um.pdf", first.name)
        assertEquals(pdfs.file(vm.pdf), first.file)
        assertTrue(first.file!!.isFile)
        assertEquals(2_009L, first.bytes)
        assertEquals(4, first.pages)
        vm.attachPdf(picked("dois.pdf", 5_000))
        val second = vm.pdfInfo!!
        assertEquals("dois.pdf", second.name)
        assertNotEquals(first.file, second.file)
        assertEquals(pdfs.file(vm.pdf), second.file)
        assertEquals(5_009L, second.bytes)
        assertFalse("the PDF before is gone from the disk", first.file.exists())
        assertEquals(vm.pdf, repo.library.value.songs.single().pdf)
        vm.removePdf()
        assertNull(vm.pdfInfo)
        assertEquals("", vm.pdf)
        assertFalse(second.file!!.exists())
    }

    @Test fun `a new song's card has the file as soon as it is attached, and the song is saved with it`() = runBlocking {
        val (vm, repo) = open()
        vm.attachPdf(picked("Canção do Vento.pdf"))
        assertTrue(vm.pdfInfo!!.file!!.isFile)
        vm.save()
        val saved = repo.library.value.songs.single()
        assertEquals(vm.pdf, saved.pdf)
        assertEquals(vm.pdfInfo!!.file, pdfs.file(saved.pdf))
    }
}
