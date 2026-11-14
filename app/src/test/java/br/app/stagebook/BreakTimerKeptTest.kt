package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BreakTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The break's counter belongs to the show, not to the stage screen: leaving the stage (to the setlist, to a song to fix
 * something) and coming back, or going on to the next song and back, goes on from the same start (user, 14/11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BreakTimerKeptTest {
    private val main = UnconfinedTestDispatcher()

    @Before fun main() = Dispatchers.setMain(main)
    @After fun reset() = Dispatchers.resetMain()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repo() = LibraryRepository(context, scope())
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.set(): String {
        val ids = listOf("A", "B", "C").map { saveSong(Song(title = it, author = "x", text = "{key: G}\n$it")) }
        return createSetlist("Show").also { addToSetlist(it, ids); addBreak(it); addBreak(it) }   // breaks before B and C
    }

    private fun stage(repo: LibraryRepository, show: SetlistShowStore, set: String) =
        testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 0)), show)

    @Test fun `a stage that opens again in the middle of the break goes on from the same start`() {
        val repo = repo()
        val set = repo.set()
        val show = SetlistShowStore(context, repo, scope())
        val first = stage(repo, show, set)
        first.songShown(0)
        first.setBreak(true)
        val start = first.breakSince.value
        assertNotNull(start)
        Thread.sleep(30)
        // the stage was left (a new view model, no saved state) and opened again on the same song
        val second = stage(repo, show, set)
        second.songShown(0)
        second.setBreak(true)
        assertEquals(start, second.breakSince.value)
    }

    @Test fun `going on to the next song and back to the break does not restart it`() {
        val repo = repo()
        val set = repo.set()
        val show = SetlistShowStore(context, repo, scope())
        val stage = stage(repo, show, set)
        stage.songShown(0)
        stage.setBreak(true)
        val start = stage.breakSince.value
        Thread.sleep(30)
        stage.setBreak(false)
        stage.songShown(1)
        stage.songShown(0)
        stage.setBreak(true)
        assertEquals(start, stage.breakSince.value)
    }

    private fun clocked(repo: LibraryRepository, at: LongArray) =
        SetlistShowStore(context, repo, scope()).also { it.now = { at[0] }; it.today = { "2026-11-14" } }

    @Test fun `pause keeps what was counted, resume goes on from it and zero starts again`() {
        val repo = repo()
        val set = repo.set()
        val at = longArrayOf(1_000_000L)
        val show = clocked(repo, at)
        val next = repo.library.value.setlist(set)!!.items[1]
        val key = BreakTimer.key(set, next)
        show.breakEntered(set, next)
        at[0] += 90_000
        show.breakPause(set, next)
        at[0] += 600_000   // paused for ten minutes: nothing counted
        assertEquals(90_000L, show.breaks.value.getValue(key).elapsed(at[0]))
        show.breakResume(set, next)
        at[0] += 30_000
        assertEquals(120_000L, show.breaks.value.getValue(key).elapsed(at[0]))
        show.breakReset(set, next)   // running: goes on counting from zero
        at[0] += 5_000
        assertEquals(5_000L, show.breaks.value.getValue(key).elapsed(at[0]))
        show.breakPause(set, next)
        show.breakReset(set, next)   // paused: stays paused, at zero
        at[0] += 5_000
        assertEquals(0L, show.breaks.value.getValue(key).elapsed(at[0]))
        assertEquals(false, show.breaks.value.getValue(key).running)
    }

    @Test fun `the counter survives the store being created again`() {
        val repo = repo()
        val set = repo.set()
        val at = longArrayOf(2_000_000L)
        val next = repo.library.value.setlist(set)!!.items[1]
        clocked(repo, at).breakEntered(set, next)
        at[0] += 45_000
        val again = clocked(repo, at)
        assertEquals(45_000L, again.breaks.value.getValue(BreakTimer.key(set, next)).elapsed(at[0]))
    }

    @Test fun `the break is over once the song after it is played, clear played, or another day`() {
        val repo = repo()
        val set = repo.set()
        val items = repo.library.value.setlist(set)!!.items
        val at = longArrayOf(3_000_000L)
        val show = clocked(repo, at)
        show.breakEntered(set, items[1])
        show.played(set, items[1])
        assertEquals(emptyMap<String, BreakTimer.Timer>(), show.breaks.value)
        show.breakEntered(set, items[2])
        show.clear(set)
        assertEquals(emptyMap<String, BreakTimer.Timer>(), show.breaks.value)
        show.breakEntered(set, items[1])
        show.today = { "2026-11-15" }
        show.stageOpened(set)
        assertEquals(emptyMap<String, BreakTimer.Timer>(), show.breaks.value)
    }

    @Test fun `the stage pauses, resumes and zeroes the break on screen`() {
        val repo = repo()
        val set = repo.set()
        val at = longArrayOf(4_000_000L)
        val show = clocked(repo, at)
        val stage = stage(repo, show, set)
        stage.songShown(0)
        stage.setBreak(true)
        at[0] += 20_000
        stage.pauseBreak()
        assertEquals(false, stage.breakTimer.value!!.running)
        assertEquals(20_000L, stage.breakTimer.value!!.elapsed(at[0] + 99_000))
        stage.resumeBreak()
        assertEquals(true, stage.breakTimer.value!!.running)
        stage.resetBreak()
        assertEquals(0L, stage.breakTimer.value!!.elapsed(at[0]))
    }

    // the musician in the break leaves the stage (to fix a song, the setlist) and comes back: the break is there again
    @Test fun `a stage opened again on the song before an open break shows the break, with the same counter`() {
        val repo = repo()
        val set = repo.set()
        val at = longArrayOf(5_000_000L)
        val show = clocked(repo, at)
        val first = stage(repo, show, set)
        first.songShown(0)
        first.setBreak(true)
        at[0] += 70_000
        val again = stage(repo, show, set)
        again.songShown(0)
        assertNotNull(again.breakSince.value)
        assertEquals(70_000L, again.breakTimer.value!!.elapsed(at[0]))
    }

    @Test fun `a break that was left, or a stage opened on another song, shows no break`() {
        val repo = repo()
        val set = repo.set()
        val at = longArrayOf(6_000_000L)
        val show = clocked(repo, at)
        val first = stage(repo, show, set)
        first.songShown(0)
        first.setBreak(true)
        first.setBreak(false)   // went on to the next song
        val left = stage(repo, show, set)
        left.songShown(0)
        assertEquals(null, left.breakSince.value)
        first.setBreak(true)
        val elsewhere = stage(repo, show, set)
        elsewhere.songShown(2)
        assertEquals(null, elsewhere.breakSince.value)
    }
}
