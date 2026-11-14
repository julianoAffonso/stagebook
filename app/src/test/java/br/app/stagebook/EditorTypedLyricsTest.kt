package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.editor.SongEditorScreen
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "New song": what is typed in the lyrics field is what Save writes, whichever way the person got there (typing in
 * pieces, going to the title and back, the lyrics-only profile) and Back with something typed asks first (device test
 * on the OnePlus: two songs were created with empty lyrics).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class EditorTypedLyricsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private lateinit var repo: LibraryRepository
    private lateinit var vm: SongEditorViewModel
    private var left = 0

    private fun open(look: Look) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(Dispatchers.IO)
        repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("fromImport" to false)), br.app.stagebook.data.ImportQueue(repo), br.app.stagebook.data.TrackStore(context))
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        compose.setContent {
            StagebookTheme {
                SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, { left++ }, vm::delete, cursor = vm.cursor, look = look)
            }
        }
        compose.waitForIdle()
    }

    private fun lyricsField(look: Look) = compose.onNode(hasSetTextAction() and hasText(if (look.chords) "Letra e cifra" else "Letra"))
    private fun titleField() = compose.onNode(hasSetTextAction() and hasText("Nome da música"))

    private fun savedText() = repo.library.value.songs.single { it.title == "Teste" }.text

    private fun typeAndSave(look: Look, pieces: List<String>, between: () -> Unit = {}) {
        open(look)
        titleField().performTextInput("Teste")
        for (piece in pieces) { lyricsField(look).performTextInput(piece); between() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Salvar").performClick()
        compose.waitForIdle()
    }

    @Test fun `lyrics typed in one go are saved`() {
        typeAndSave(Look(), listOf("Primeira linha da letra"))
        assertEquals("Primeira linha da letra", savedText())
    }

    @Test fun `lyrics typed in pieces with lines are saved`() {
        typeAndSave(Look(), listOf("Primeira ", "linha\n", "Segunda linha", "\n\nTerceira"))
        assertEquals("Primeira linha\nSegunda linha\n\nTerceira", savedText())
    }

    @Test fun `lyrics typed after going to the title and back are saved`() {
        typeAndSave(Look(), listOf("Primeira linha", " e mais")) { titleField().performTextInput("") }
        assertEquals("Primeira linha e mais", savedText())
    }

    @Test fun `lyrics typed with chords in brackets are saved`() {
        typeAndSave(Look(), listOf("[G]Primeira ", "[D]linha"))
        // the key is worked out from the chords when saving; the lyrics are all there
        assertEquals("{key: G}\n[G]Primeira [D]linha", savedText())
    }

    @Test fun `lyrics typed in the lyrics only profile are saved`() {
        val look = Look(chords = false)
        typeAndSave(look, listOf("Primeira linha", "\nSegunda linha", " e mais\n"))
        assertEquals("Primeira linha\nSegunda linha e mais\n", savedText())
    }

    @Test fun `back with lyrics typed asks before leaving`() {
        open(Look())
        lyricsField(Look()).performTextInput("Primeira linha")
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Sair sem salvar?", substring = true).assertExists()
        assertEquals(0, left)
    }
}
