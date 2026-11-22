package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import br.app.stagebook.ui.editor.SongEditorRoute
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
 * The editor's ROUTE (the one the app uses, with the real view model) shows "2 / 3" and the arrows when a queue is there.
 * The first version of previous/next was tested on the view model and on the screen apart, and the route never passed the
 * position to the screen: on the device nothing showed (test on the OnePlus and the Chuwi, 14/11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class EditorRouteStepsTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private class Rig(val repo: LibraryRepository, val ids: List<String>, val queue: EditorQueue, val vm: SongEditorViewModel)

    private fun rig(at: Int): Rig {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(Dispatchers.IO)
        val repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val ids = listOf("EdA", "EdB", "EdC").map { repo.saveSong(Song(title = it, author = "Band", text = "[G]letra $it")) }
        val queue = EditorQueue()
        queue.open(ids, at)
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("songId" to ids[at], "fromImport" to false, "cursor" to -1)), ImportQueue(repo), TrackStore(context), queue)
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        return Rig(repo, ids, queue, vm)
    }

    @Test fun `the route shows the place in the queue and the arrows work`() {
        val r = rig(1)
        var moved: String? = null
        compose.setContent { StagebookTheme { SongEditorRoute(onDone = {}, viewModel = r.vm, onMoved = { moved = it }) } }
        compose.waitForIdle()
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithContentDescription("Próxima música").performClick()
        compose.waitForIdle()
        assertEquals(r.ids[2], moved)
    }

    @Test fun `the route without a queue shows no place`() {
        val r = rig(1)
        r.queue.clear()
        compose.setContent { StagebookTheme { SongEditorRoute(onDone = {}, viewModel = r.vm) } }
        compose.waitForIdle()
        compose.onNodeWithText("2 / 3").assertDoesNotExist()
    }
}
