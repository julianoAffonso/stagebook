package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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

    private fun screen(state: SongEditorUiState, pdf: EditorPdf?, onPick: () -> Unit = {}, onRemove: () -> Unit = {}, onInvert: (Boolean) -> Unit = {}) = compose.setContent {
        StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}, pdf = pdf, onPickPdf = onPick, onRemovePdf = onRemove, onPdfInvert = onInvert) }
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
