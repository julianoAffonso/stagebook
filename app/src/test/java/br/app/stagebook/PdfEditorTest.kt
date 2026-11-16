package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.EditorQueue
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.PdfAttach
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfProblem
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.editor.EditorExit
import br.app.stagebook.ui.editor.EditorMode
import br.app.stagebook.ui.editor.EditorPdf
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.editor.SongEditorViewModel
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/** The song editor with a PDF (T-PDF 5): attach, replace, remove, the card in place of the lyrics, and the question first. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfEditorTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    @get:Rule val compose = createComposeRule()

    private class Pages(val n: Int = 4) : PdfPageCounter {
        override fun pageCount(file: File): Int = n
    }

    private fun pdfFile(name: String, bytes: ByteArray = "%PDF-1.4\n".toByteArray() + ByteArray(2_000)) = File(context.cacheDir, name).apply { writeBytes(bytes) }

    private fun open(songId: String? = null, repoSongs: (LibraryRepository) -> String? = { null }): Triple<SongEditorViewModel, LibraryRepository, PdfStore> {
        val scope = CoroutineScope(Dispatchers.IO)
        val repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } }; repo.restore(emptyList(), emptyList()) }
        val id = repoSongs(repo)
        val pdfs = PdfStore(context)
        val args = mutableMapOf<String, Any?>("fromImport" to false)
        id?.let { args["songId"] = it }
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(args), ImportQueue(repo), TrackStore(context), EditorQueue(), pdfs, PdfInspector(Pages()))
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        return Triple(vm, repo, pdfs)
    }

    @Test fun `a new song takes a PDF and the file name as title, and saves with it`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, pdfs) = open()
            val result = vm.attachPdf(android.net.Uri.fromFile(pdfFile("Canção do Vento.pdf")))
            assertTrue(result is PdfAttach.Saved)
            assertEquals("Canção do Vento.pdf", vm.pdfInfo!!.name)
            assertEquals(4, vm.pdfInfo!!.pages)
            assertEquals("Canção do Vento", vm.uiState.title)
            vm.save()
            val saved = repo.library.value.songs.single()
            assertEquals(vm.pdf, saved.pdf)
            assertEquals("Canção do Vento.pdf", saved.pdfName)
            assertTrue(pdfs.file(saved.pdf).isFile)
            assertEquals(EditorExit.Saved(saved.id), vm.onDone.first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a title already typed is not replaced by the file name`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, _, _) = open()
            vm.onTitle("Meu título")
            vm.attachPdf(android.net.Uri.fromFile(pdfFile("outro.pdf")))
            assertEquals("Meu título", vm.uiState.title)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a PDF over a song with lyrics keeps the lyrics, and removing it brings them back`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, pdfs) = open { it.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "[G]primeira linha\nsegunda linha")) }
            val id = repo.library.value.songs.single().id
            vm.attachPdf(android.net.Uri.fromFile(pdfFile("hazard.pdf")))
            val withPdf = repo.song(id)!!
            assertEquals("hazard.pdf", withPdf.pdfName)
            assertEquals("[G]primeira linha\nsegunda linha", withPdf.text)   // the text is kept as it was, hidden
            val file = pdfs.file(withPdf.pdf)
            assertTrue(file.isFile)
            vm.removePdf()
            val back = repo.song(id)!!
            assertEquals("", back.pdf)
            assertEquals("", back.pdfName)
            assertEquals("[G]primeira linha\nsegunda linha", back.text)
            assertFalse(file.exists())
            assertEquals(null, vm.pdfInfo)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `replacing the PDF deletes the old file and the old marks`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, pdfs) = open { it.saveSong(Song(title = "Chart", text = "x")) }
            val id = repo.library.value.songs.single().id
            vm.attachPdf(android.net.Uri.fromFile(pdfFile("a.pdf")))
            val first = repo.song(id)!!.pdf
            repo.updateSong(id) { it.copy(pdfMarks = listOf(br.app.stagebook.data.model.PdfMark(0, listOf(br.app.stagebook.data.model.PdfPoint(0.1f, 0.1f))))) }
            vm.attachPdf(android.net.Uri.fromFile(pdfFile("b.pdf")))
            val now = repo.song(id)!!
            assertTrue(now.pdf != first && pdfs.file(now.pdf).isFile)
            assertFalse(pdfs.file(first).exists())
            assertTrue(now.pdfMarks.isEmpty())
            assertEquals("b.pdf", now.pdfName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `the invert choice of a saved song is kept at once, follows Settings until chosen and goes with the PDF`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, _) = open { it.saveSong(Song(title = "Chart", text = "x", pdf = "p.pdf", pdfName = "p.pdf")) }
            val id = repo.library.value.songs.single().id
            assertEquals(null, repo.song(id)!!.pdfInvert)
            assertEquals("follows Settings (off here)", false, vm.pdfInfo!!.invert)
            vm.setPdfInvert(true)
            assertEquals(true, repo.song(id)!!.pdfInvert)
            assertEquals(true, vm.pdfInfo!!.invert)
            vm.removePdf()
            assertEquals(null, repo.song(id)!!.pdfInvert)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `the trims of a saved song are kept at once, only on this device, and go with the PDF`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, _) = open { it.saveSong(Song(title = "Chart", text = "x", pdf = "p.pdf", pdfName = "p.pdf")) }
            val id = repo.library.value.songs.single().id
            vm.setPdfCrop(br.app.stagebook.data.model.PdfCrop(br.app.stagebook.data.model.PdfCrop.ALL, left = 0.1f, right = 0.9f))
            vm.setPdfCrop(br.app.stagebook.data.model.PdfCrop(2, top = 0.2f))
            assertEquals(2, repo.song(id)!!.pdfCrops.size)
            assertEquals(2, vm.pdfInfo!!.crops.size)
            vm.clearPdfCrop(2)
            assertEquals(1, repo.song(id)!!.pdfCrops.size)
            vm.removePdf()
            assertTrue(repo.song(id)!!.pdfCrops.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `on a PDF song the key is written down a step at a time, and the hidden text is left alone`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, _) = open { it.saveSong(Song(title = "Chart", text = "{key: G}\n[G]letra [D]aqui", pdf = "p.pdf", pdfName = "p.pdf")) }
            val chords = "[G]letra [D]aqui"
            vm.transpose(1)
            assertEquals(br.app.stagebook.domain.Chords.transposeKey("G", 1), vm.uiState.meta.key)
            assertTrue("the chords were not moved", vm.uiState.text.contains(chords))
            vm.transpose(-2)
            assertEquals(br.app.stagebook.domain.Chords.transposeKey(br.app.stagebook.domain.Chords.transposeKey("G", 1), -2), vm.uiState.meta.key)
            assertTrue(vm.uiState.text.contains(chords))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `on a PDF song with no key the first step writes C`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, _, _) = open { it.saveSong(Song(title = "Chart", text = "só letra", pdf = "p.pdf", pdfName = "p.pdf")) }
            vm.transpose(1)
            assertEquals("C", vm.uiState.meta.key)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a refused PDF changes nothing and says why`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, _) = open { it.saveSong(Song(title = "Chart", text = "x")) }
            val id = repo.library.value.songs.single().id
            val r = vm.attachPdf(android.net.Uri.fromFile(pdfFile("fake.pdf", "MZ not a pdf".toByteArray())))
            assertEquals(PdfProblem.NOT_PDF, (r as PdfAttach.Refused).problem)
            assertEquals("", repo.song(id)!!.pdf)
            assertEquals(null, vm.pdfInfo)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---- the screen ----

    private fun screen(state: SongEditorUiState, pdf: EditorPdf?, onPick: () -> Unit = {}, onRemove: () -> Unit = {}, onInvert: (Boolean) -> Unit = {},
        onSetCrop: (br.app.stagebook.data.model.PdfCrop) -> Unit = {}, onClearCrop: (Int) -> Unit = {}) = compose.setContent {
        StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}, pdf = pdf, onPickPdf = onPick, onRemovePdf = onRemove, onPdfInvert = onInvert, onSetPdfCrop = onSetCrop, onClearPdfCrop = onClearCrop) }
    }

    private val saved = SongEditorUiState(loading = false, mode = EditorMode.EDIT, title = "Chart", text = "[G]letra")

    @Test fun `a PDF song shows the card in place of the lyrics field - name, pages, size and the sentence`() {
        screen(saved, EditorPdf("Cifra do Vento.pdf", pages = 3, bytes = 412_000))
        compose.onNodeWithText("Cifra do Vento.pdf").assertExists()
        compose.onNodeWithText("3 páginas · 403 KB").assertExists()
        compose.onNodeWithText("Esta música é um PDF. Não dá para editar o PDF; no palco você pode marcá-lo com o marca-texto.").assertExists()
        compose.onNodeWithText("Trocar PDF").assertExists()
        compose.onNodeWithText("Remover PDF").assertExists()
        // no lyrics field: its label is only the selector's segment ("Letra e cifra"), never a field
        compose.onNodeWithText("[G]letra", substring = true).assertDoesNotExist()
    }

    @Test fun `the card has the invert switch, which says what it is now and asks for the other`() {
        var asked: Boolean? = null
        screen(saved, EditorPdf("a.pdf", 1, 1_000, invert = false), onInvert = { asked = it })
        compose.onNodeWithText("Inverter as cores do PDF").performClick()
        assertEquals(true, asked)
    }

    @Test fun `a PDF with no file to read has the card but no preview and no invert switch while lost`() {
        screen(saved, EditorPdf("a.pdf", lost = true))
        compose.onNodeWithText("Inverter as cores do PDF").assertDoesNotExist()
    }

    @Test fun `Crop opens the trim window, which saves the same trim for all pages by default`() {
        var set: br.app.stagebook.data.model.PdfCrop? = null
        screen(saved, EditorPdf("a.pdf", pages = 3, bytes = 1_000), onSetCrop = { set = it })
        compose.onNodeWithText("Recortar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Recortar o PDF").assertExists()
        compose.onNodeWithText("Todas as páginas").assertExists()
        compose.onNodeWithText("Só esta página").assertExists()
        compose.onNodeWithText("1 / 3").assertExists()
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        assertEquals(br.app.stagebook.data.model.PdfCrop.ALL, set?.page)
    }

    @Test fun `This page only saves the trim for the page on screen, and No crop takes off the one for its scope`() {
        var set: br.app.stagebook.data.model.PdfCrop? = null
        var cleared: Int? = null
        screen(saved, EditorPdf("a.pdf", pages = 3, bytes = 1_000), onSetCrop = { set = it }, onClearCrop = { cleared = it })
        compose.onNodeWithText("Recortar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Só esta página").performClick()
        compose.onNodeWithContentDescription("Próxima página").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        assertEquals(1, set?.page)
        // again: no crop for that scope
        compose.onNodeWithText("Recortar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sem recorte").performClick()
        compose.waitForIdle()
        assertEquals(br.app.stagebook.data.model.PdfCrop.ALL, cleared)
    }

    @Test fun `dragging one edge of the frame leaves the others where they were, and the next drag starts from there`() {
        var set: br.app.stagebook.data.model.PdfCrop? = null
        screen(saved, EditorPdf("a.pdf", pages = 1, bytes = 1_000), onSetCrop = { set = it })
        compose.onNodeWithText("Recortar").performClick()
        compose.waitForIdle()
        val frame = compose.onNodeWithTag("pdf_crop_frame")
        // the left edge 30% in, then the right edge 30% in: the first must still be there after the second
        frame.performTouchInput { down(Offset(1f, height / 2f)); moveBy(Offset(width * 0.3f, 0f)); up() }
        frame.performTouchInput { down(Offset(width - 2f, height / 2f)); moveBy(Offset(-width * 0.3f, 0f)); up() }
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        val crop = set!!
        assertEquals("left kept: $crop", 0.3f, crop.left, 0.05f)
        assertEquals("right moved: $crop", 0.7f, crop.right, 0.05f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(1f, crop.bottom, 0.001f)
    }

    @Test fun `after choosing This page only the frame still drags and saves for that page`() {
        var set: br.app.stagebook.data.model.PdfCrop? = null
        screen(saved, EditorPdf("a.pdf", pages = 3, bytes = 1_000), onSetCrop = { set = it })
        compose.onNodeWithText("Recortar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Só esta página").performClick()
        compose.waitForIdle()
        val frame = compose.onNodeWithTag("pdf_crop_frame")
        frame.performTouchInput { down(Offset(1f, height / 2f)); moveBy(Offset(width * 0.25f, 0f)); up() }
        frame.performTouchInput { down(Offset(width / 2f, 1f)); moveBy(Offset(0f, height * 0.2f)); up() }
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        val crop = set!!
        assertEquals(0, crop.page)
        assertEquals("left: $crop", 0.25f, crop.left, 0.05f)
        assertEquals("top: $crop", 0.2f, crop.top, 0.05f)
    }

    @Test fun `on a PDF song the key steps are on even with no chords to move`() {
        screen(saved.copy(text = "só letra"), EditorPdf("a.pdf", pages = 1, bytes = 1_000))
        compose.onNodeWithText("Dados").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Subir meio tom").assertIsEnabled()
    }

    @Test fun `a PDF whose file is gone has no Crop button`() {
        screen(saved, EditorPdf("a.pdf", lost = true))
        compose.onAllNodesWithText("Recortar").assertCountEquals(0)
    }

    @Test fun `a one page PDF has no page switch and no scope choice`() {
        screen(saved, EditorPdf("a.pdf", pages = 1, bytes = 1_000))
        compose.onNodeWithText("Recortar").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Todas as páginas").assertCountEquals(0)
        compose.onAllNodesWithText("1 / 1").assertCountEquals(0)
    }

    @Test fun `the floppy is off on a saved song with nothing changed, lit with a change, and always on for a new one`() {
        screen(saved, EditorPdf("a.pdf", pages = 2, bytes = 1_000))   // saved, nothing typed: the PDF's own windows saved theirs already
        compose.onNodeWithContentDescription("Salvar").assertIsNotEnabled()
    }

    @Test fun `a saved song with something typed lights the floppy`() {
        screen(saved.copy(unsaved = true), EditorPdf("a.pdf", pages = 2, bytes = 1_000))
        compose.onNodeWithContentDescription("Salvar").assertIsEnabled()
    }

    @Test fun `a new song has the floppy on`() {
        screen(saved.copy(mode = EditorMode.NEW), EditorPdf("a.pdf", pages = 2, bytes = 1_000))
        compose.onNodeWithContentDescription("Salvar").assertIsEnabled()
    }

    @Test fun `a song without a PDF keeps the lyrics field and has no card`() {
        screen(saved, null)
        compose.onNodeWithText("Esta música é um PDF", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Trocar PDF").assertDoesNotExist()
    }

    @Test fun `a PDF whose file is gone says to attach it again`() {
        screen(saved, EditorPdf("Cifra do Vento.pdf", lost = true))
        compose.onNodeWithText("O PDF não está neste aparelho. Anexe de novo.", substring = true).assertExists()
    }

    @Test fun `replace and remove from the card, the removal asking first`() {
        var picked = 0; var removed = 0
        screen(saved, EditorPdf("a.pdf", 1, 1_000), { picked++ }, { removed++ })
        compose.onNodeWithText("Trocar PDF").performClick()
        assertEquals(1, picked)
        compose.onNodeWithText("Remover PDF").performClick()
        assertEquals(0, removed)
        compose.onNodeWithText("Remover o PDF desta música?", substring = true).assertExists()
        compose.onAllNodesWithText("Remover PDF").onLast().performClick()   // the dialog's button, the last one drawn
        assertEquals(1, removed)
    }

    @Test fun `attaching to a song that has lyrics asks first, and the lyrics stay saved`() {
        var picked = 0
        screen(saved, null, { picked++ })
        compose.onNodeWithText("Dados").performClick()
        compose.onNodeWithText("Nenhum. Toque para anexar").performScrollTo().performClick()
        compose.onNodeWithText("Esta música já tem letra. Ela fica guardada e volta se você remover o PDF.").assertExists()
        assertEquals(0, picked)
        compose.onNodeWithText("Anexar PDF").performClick()
        assertEquals(1, picked)
    }

    @Test fun `attaching to a song with no lyrics does not ask`() {
        var picked = 0
        screen(SongEditorUiState(loading = false, mode = EditorMode.NEW, title = "", text = ""), null, { picked++ })
        compose.onNodeWithText("Nenhum. Toque para anexar").performScrollTo().performClick()
        assertEquals(1, picked)
    }
}
