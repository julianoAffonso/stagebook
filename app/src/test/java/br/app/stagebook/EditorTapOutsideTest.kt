package br.app.stagebook

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.EditorQueue
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.TrackStore
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tap on a neutral area takes the focus off the lyrics (the keyboard goes with it); the tools and the field keep it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w400dp-h800dp", application = android.app.Application::class)
class EditorTapOutsideTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `a tap outside takes the focus off, the tools and the field do not`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = testScope()
        val repo = testRepo(scope = scope)
        val id = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "[G]linha 1\n[D]linha 2"))
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("songId" to id, "fromImport" to false, "cursor" to -1)), ImportQueue(repo), TrackStore(context), EditorQueue())
        runBlocking { withTimeout(TEST_WAIT_MS) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        compose.setContent { StagebookTheme { SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete) } }
        compose.waitForIdle()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.assertIsFocused()
        // a button of the tools: the field keeps the focus (and the keyboard)
        compose.onAllNodes(hasText("Verso", substring = true) and hasClickAction())[0].performClick()
        compose.waitForIdle()
        field.assertIsFocused()
        // neutral area: left margin of the pane
        compose.onAllNodes(isRoot())[0].performTouchInput { this.click(androidx.compose.ui.geometry.Offset(2f, height / 2f)) }
        compose.waitForIdle()
        field.assertIsNotFocused()
    }
}
