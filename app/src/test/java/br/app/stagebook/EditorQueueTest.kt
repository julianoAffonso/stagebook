package br.app.stagebook

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
import br.app.stagebook.ui.editor.EditorExit
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
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editor's previous/next: the queue of ids (where the editor came from) and the editor's place in it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class EditorQueueTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private class Rig(val repo: LibraryRepository, val ids: List<String>, val queue: EditorQueue, val context: android.app.Application, val scope: CoroutineScope)

    private fun rig(count: Int = 4): Rig {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(Dispatchers.IO)
        val repo = testRepo(scope = scope)
        val ids = (1..count).map { repo.saveSong(Song(title = "Song $it", author = "Band", text = "[G]letra $it")) }
        return Rig(repo, ids, EditorQueue(), context, scope)
    }

    private fun Rig.editor(at: Int, fromImport: Boolean = false): SongEditorViewModel {
        val vm = SongEditorViewModel(repo, SongMetadata(repo, MusicBrainz(), scope), ImportDraft(), SavedStateHandle(mapOf("songId" to ids[at], "fromImport" to fromImport, "cursor" to -1)), ImportQueue(repo), TrackStore(context), queue)
        runBlocking { withTimeout(5_000) { while (vm.uiState.loading) kotlinx.coroutines.delay(10) } }
        return vm
    }

    private fun SongEditorViewModel.exit(): EditorExit? = runBlocking { withTimeoutOrNull(300) { onDone.first() } }

    @Test fun `the editor knows its place in the setlist's order`() {
        val r = rig()
        r.queue.open(r.ids, 1)
        val p = r.editor(1).position.value!!
        assertEquals(1, p.index); assertEquals(4, p.total)
        assertTrue(p.hasPrev && p.hasNext)
    }

    @Test fun `next moves to the following song and the queue follows`() {
        val r = rig()
        r.queue.open(r.ids, 1)
        val vm = r.editor(1)
        vm.go(+1)
        assertEquals(EditorExit.Moved(r.ids[2]), vm.exit())
        assertEquals(2, r.queue.state.value!!.index)
    }

    @Test fun `at the ends there is nothing to go to`() {
        val r = rig()
        r.queue.open(r.ids, 0)
        val first = r.editor(0)
        assertFalse(first.position.value!!.hasPrev)
        first.go(-1)
        assertNull(first.exit())
        val r2 = rig()
        r2.queue.open(r2.ids, 3)
        val last = r2.editor(3)
        assertFalse(last.position.value!!.hasNext)
        last.go(+1)
        assertNull(last.exit())
    }

    @Test fun `without a queue, or from an import, there are no arrows`() {
        val r = rig()
        assertNull(r.editor(1).position.value)
        r.queue.open(r.ids, 1)
        assertNull(r.editor(1, fromImport = true).position.value)
        // a queue that doesn't hold this song (an old one) shows nothing either
        val other = rig()
        r.queue.open(other.ids, 0)
        assertNull(r.editor(1).position.value)
    }

    @Test fun `a song deleted meanwhile is skipped`() {
        val r = rig()
        r.queue.open(r.ids, 0)
        val vm = r.editor(0)
        r.repo.deleteSong(r.ids[1])
        vm.go(+1)
        assertEquals(EditorExit.Moved(r.ids[2]), vm.exit())
    }

    @Test fun `save and go saves this song first, and stays when it can't save`() {
        val r = rig()
        r.queue.open(r.ids, 1)
        val vm = r.editor(1)
        vm.onText("[C]letra mudada")
        vm.saveAndGo(+1)
        assertEquals(EditorExit.Moved(r.ids[2]), vm.exit())
        assertTrue(r.repo.song(r.ids[1])!!.text.contains("letra mudada"))
        val r2 = rig()
        r2.queue.open(r2.ids, 1)
        val bad = r2.editor(1)
        bad.onTitle("")
        bad.saveAndGo(+1)
        assertNull(bad.exit())
        assertEquals(br.app.stagebook.ui.editor.EditorError.TITLE_EMPTY, bad.uiState.error)
    }

    @Test fun `going without saving leaves the song as it was`() {
        val r = rig()
        r.queue.open(r.ids, 1)
        val vm = r.editor(1)
        vm.onText("[C]nunca salva")
        vm.go(+1)
        assertEquals(EditorExit.Moved(r.ids[2]), vm.exit())
        assertEquals("[G]letra 2", r.repo.song(r.ids[1])!!.text.trim())
    }

    @Test fun `the queue keeps a window around the open song when the list is huge`() {
        val q = EditorQueue()
        val ids = (0 until 5000).map { "s$it" }
        q.open(ids, 4000)
        val s = q.state.value!!
        assertTrue(s.ids.size <= EditorQueue.MAX)
        assertEquals("s4000", s.ids[s.index])
    }

    @Test fun `a single song or an index out of range makes no queue`() {
        val q = EditorQueue()
        q.open(listOf("a"), 0); assertNull(q.state.value)
        q.open(listOf("a", "b"), 5); assertNull(q.state.value)
        q.offerList(listOf("a", "b", "c")); q.openFromList("b")
        assertEquals(1, q.state.value!!.index)
        q.openFromList("zzz"); assertNull(q.state.value)
    }

    private fun SongEditorViewModel.close() {
        SongEditorViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(this)
    }

    @Test fun `an editor closing after the next one opened leaves the new queue alone`() {
        val r = rig()
        r.queue.open(r.ids, 1)
        val first = r.editor(1)
        // the pencil again before the first editor's exit animation ended: the queue is filled again, then the old one is cleared
        r.queue.open(r.ids, 2)
        val second = r.editor(2)
        first.close()
        assertEquals(2, r.queue.state.value!!.index)
        assertEquals(3, second.position.value!!.index + 1)
        second.close()
        assertNull("the editor that is still the latest empties it", r.queue.state.value)
    }
}

/** What the stage fills in for the editor, for every way it opens it (pencil and long press go through the same call). */
class EditorQueueStageTest {
    private val songs = listOf("a" to false, "gone" to true, "b" to false, "c" to false)

    @Test fun `in a setlist the order without the missing ones, at the open song`() {
        val q = EditorQueue()
        q.openFromStage(songs, open = 2, inSetlist = true, fromList = false, songId = "b")
        val s = q.state.value!!
        assertEquals(listOf("a", "b", "c"), s.ids); assertEquals(1, s.index)
    }

    @Test fun `from the Songs list with a search, that list`() {
        val q = EditorQueue()
        q.offerList(listOf("x", "b", "z"))
        q.openFromStage(listOf("b" to false), open = 0, inSetlist = false, fromList = true, songId = "b")
        val s = q.state.value!!
        assertEquals(listOf("x", "b", "z"), s.ids); assertEquals(1, s.index)
    }

    @Test fun `from elsewhere no queue, and an old one is dropped`() {
        val q = EditorQueue()
        q.open(listOf("a", "b"), 0)
        q.openFromStage(listOf("b" to false), open = 0, inSetlist = false, fromList = false, songId = "b")
        assertNull(q.state.value)
    }
}
