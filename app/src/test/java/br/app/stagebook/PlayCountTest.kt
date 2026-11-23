package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.mergeSongs
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.SetlistShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stage counts the plays the Songs list sorts by (user, 19/11): a minute on stage, once a show day, from a setlist
 * or alone; the count is the song's own, so deleting the setlist or clearing its marks keeps it, the backup carries it
 * and the exported songs file (given to others) does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlayCountTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val main = UnconfinedTestDispatcher()

    @Before fun main() = Dispatchers.setMain(main)
    @After fun reset() = Dispatchers.resetMain()

    private fun scope() = testScope()
    private fun repo() = testRepo(scope = scope(), empty = true)
    private var day = "2026-11-19"
    private fun store(repo: LibraryRepository) = SetlistShowStore(context, repo, scope()).also { it.today = { day } }
    private fun wait(ms: Long) = main.scheduler.apply { advanceTimeBy(ms); runCurrent() }
    private fun LibraryRepository.plays(id: String) = song(id)!!.plays

    @Test fun `a song alone on stage counts after a minute, not before, and once a day`() {
        val repo = repo()
        val id = repo.saveSong(Song(title = "A", text = "A"))
        val show = store(repo)
        val stage = testStage(context, repo, SavedStateHandle(mapOf("songId" to id, "start" to 0)), show)
        stage.songShown(0)
        wait(SetlistShow.PLAYED_AFTER_MS - 1_000)
        assertEquals(0, repo.plays(id))
        wait(2_000)
        assertEquals(1, repo.plays(id))
        assertEquals(day, repo.song(id)!!.lastPlayed)
        // opened again the same day: still one
        testStage(context, repo, SavedStateHandle(mapOf("songId" to id, "start" to 0)), show).songShown(0)
        wait(SetlistShow.PLAYED_AFTER_MS + 1_000)
        assertEquals(1, repo.plays(id))
        // another day: two
        day = "2026-11-20"
        testStage(context, repo, SavedStateHandle(mapOf("songId" to id, "start" to 0)), show).songShown(0)
        wait(SetlistShow.PLAYED_AFTER_MS + 1_000)
        assertEquals(2, repo.plays(id))
    }

    @Test fun `the song the app opened on by itself counts only after something is done on the stage`() {
        val repo = repo()
        val id = repo.saveSong(Song(title = "A", text = "A"))
        val other = repo.saveSong(Song(title = "B", text = "B"))
        val show = store(repo)
        // the app left open on the last song: nobody is playing it
        val stage = testStage(context, repo, SavedStateHandle(mapOf("songId" to id, "start" to 0, "resumed" to true)), show)
        stage.songShown(0)
        wait(SetlistShow.PLAYED_AFTER_MS * 3)
        assertEquals(0, repo.plays(id))
        assertTrue(repo.library.value.history.isEmpty())
        // a page turned: its minute starts there
        stage.stageAction()
        wait(SetlistShow.PLAYED_AFTER_MS - 1_000)
        assertEquals(0, repo.plays(id))
        wait(2_000)
        assertEquals(1, repo.plays(id))
        // in a setlist: going to the next song is the action, and that song counts with no more
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(id, other)) }
        day = "2026-11-20"
        val resumed = testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 0, "resumed" to true)), show)
        resumed.songShown(0)
        wait(SetlistShow.PLAYED_AFTER_MS * 2)
        assertEquals(1, repo.plays(id))
        resumed.songShown(1)
        wait(SetlistShow.PLAYED_AFTER_MS + 1_000)
        assertEquals(1, repo.plays(other))
        assertEquals(1, repo.plays(id))
    }

    @Test fun `from a setlist each song counts its own minute, and deleting the setlist or clearing it keeps the count`() {
        val repo = repo()
        val ids = listOf("A", "B").map { repo.saveSong(Song(title = it, text = it)) }
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids) }
        val show = store(repo)
        val stage = testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 0)), show)
        stage.songShown(0)
        wait(40_000)
        stage.songShown(1)   // left before its minute: A not counted
        wait(SetlistShow.PLAYED_AFTER_MS + 1_000)
        assertEquals(0, repo.plays(ids[0]))
        assertEquals(1, repo.plays(ids[1]))
        show.clear(set)
        repo.deleteSetlist(set)
        assertEquals(1, repo.plays(ids[1]))
    }

    @Test fun `the backup keeps the count, the exported songs file leaves it out, an import keeps the device's`() {
        val counted = Song(id = "1", title = "A", author = "x", text = "A", plays = 7, lastPlayed = "2026-11-01")
        val back = LibraryCodec.decode(LibraryCodec.encode(Library(songs = listOf(counted)))) { "n" }
        assertEquals(7, back.songs.single().plays)
        assertEquals("2026-11-01", back.songs.single().lastPlayed)
        val exported = LibraryCodec.decodeSongs(LibraryCodec.encodeSongs(listOf(counted))).single()
        assertEquals(0, exported.plays)
        assertEquals("", exported.lastPlayed)
        // a file with the same song (and someone else's count, if any) updates the lyrics and keeps this device's count
        val (merged, _) = Library(songs = listOf(counted)).mergeSongs(listOf(counted.copy(id = "", text = "new", plays = 99, lastPlayed = "2026-01-01"))) { "n" }
        assertEquals(7, merged.songs.single().plays)
        val (added, _) = Library().mergeSongs(listOf(counted.copy(id = ""))) { "n" }
        assertEquals("a song new to this device starts at zero", 0, added.songs.single().plays)
    }
}
