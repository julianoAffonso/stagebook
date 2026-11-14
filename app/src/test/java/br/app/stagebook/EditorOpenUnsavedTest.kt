package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.model.Song
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opening a song in the editor and leaving without typing never asks "Leave without saving?". */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class EditorOpenUnsavedTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private fun open(text: String, cursor: Int): SongEditorViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(Dispatchers.IO)
        val repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val id = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = text))
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("songId" to id, "fromImport" to false, "cursor" to cursor)), br.app.stagebook.data.ImportQueue(repo), br.app.stagebook.data.TrackStore(context))
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        return vm
    }

    private fun check(text: String, cursor: Int) {
        val vm = open(text, cursor)
        var left = 0
        compose.setContent {
            StagebookTheme {
                SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, { left++ }, vm::delete, cursor = vm.cursor)
            }
        }
        compose.waitForIdle()
        assertFalse(text, vm.uiState.unsaved)
        compose.onNodeWithContentDescription("Voltar").performClickCompat()
        compose.onAllNodesWithText("Sair sem salvar?", substring = true).fetchSemanticsNodes().let { assertEquals(text, 0, it.size) }
        assertEquals(1, left)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickCompat() = performClick()

    @Test fun `plain song`() = check("[Refrão]\nprimeira linha\nsegunda linha", -1)
    @Test fun `from the stage with the cursor`() = check("{key: G}\n[Refrão]\n[G]primeira linha\n[D]segunda linha", 20)
    @Test fun `directives out of order and windows lines`() = check("[Verso]\r\n[G]linha\r\n{capo: 2}\r\n{key: G}\r\n", 12)
    @Test fun `trailing blank lines and tempo`() = check("{tempo: 90}\n{capo: 2}\n{key: G}\n\n[Refrão]\n[G]linha\n\n\n", 15)
}
