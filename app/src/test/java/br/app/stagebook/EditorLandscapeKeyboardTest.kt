package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone on its side (800x360 dp) with the keyboard up: the lyrics field keeps room for several lines (OnePlus, 14/11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w800dp-h360dp", application = android.app.Application::class)
class EditorLandscapeKeyboardTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `the lyrics field keeps at least two lines with the keyboard up in a low window`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(Dispatchers.IO)
        val repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val id = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = (1..40).joinToString("\n") { "[G]linha $it" }))
        val queue = EditorQueue(); queue.open(listOf(id, repo.saveSong(Song(title = "B", text = "x"))), 0)
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("songId" to id, "fromImport" to false, "cursor" to -1)), ImportQueue(repo), TrackStore(context), queue)
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        val pos = vm.position.value
        compose.setContent {
            StagebookTheme {
                // the keyboard: ~190 dp taken from the bottom (the insets reach the screen as less room)
                Box(Modifier.padding(bottom = 190.dp)) {
                    SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete, position = pos)
                }
            }
        }
        compose.waitForIdle()
        val h = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().height
        assertTrue("field height $h", h >= 96.dp)
    }
}
