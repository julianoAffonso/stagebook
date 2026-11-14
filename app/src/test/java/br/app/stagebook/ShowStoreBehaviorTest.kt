package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * Behavior of the show's store through what the app already had before the fixes (the store, the repository and the stage's
 * view model), so each test also compiles, and fails, against the old code. The wall clock is moved by changing the
 * device's time zone, which is what both the old (`LocalDate.now()`) and the new rule read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShowStoreBehaviorTest {
    private val savedZone = TimeZone.getDefault()

    // Main is replaced only by the one test that builds a view model, and every scope this class makes is cancelled after
    // each test: a background coroutine of an earlier test reading Main while another test swapped it broke the CI run
    private val scopes = mutableListOf<CoroutineScope>()
    @After fun reset() { scopes.forEach { it.cancel() }; scopes.clear(); TimeZone.setDefault(savedZone) }

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
    private fun repo() = LibraryRepository(context, scope())
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.songs(vararg titles: String) = titles.map { saveSong(Song(title = it, author = "x", text = "{key: G}\n$it")) }

    /** The device's wall clock reads [hour]:[minute] plus [plusMinutes] now: a zone whose offset puts it there. */
    private fun clockAt(hour: Int, minute: Int, plusMinutes: Int = 0) {
        val utc = java.time.LocalTime.now(java.time.ZoneOffset.UTC)
        var offset = (hour * 60 + minute - (utc.hour * 60 + utc.minute) + plusMinutes) % (24 * 60)
        if (offset >= 12 * 60 + plusMinutes) offset -= 24 * 60
        val sign = if (offset < 0) "-" else "+"
        val a = kotlin.math.abs(offset)
        TimeZone.setDefault(TimeZone.getTimeZone("GMT$sign%02d:%02d".format(a / 60, a % 60)))
    }

    // ---- the show day ----

    private fun nightShow(): Triple<SetlistShowStore, String, List<String>> {
        val repo = repo()
        val ids = repo.songs("A", "B")
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }   // a break before B
        clockAt(23, 50)
        val store = SetlistShowStore(context, repo, scope())
        store.stageOpened(set)
        store.onStage(set, ids[0]); store.played(set, ids[0]); store.onStage(set, ids[1])
        store.breakEntered(set, ids[1])
        assertEquals(listOf(ids[0]), store.marks.value[set]?.played)
        assertTrue(store.breakOpen(set, ids[1]))
        return Triple(store, set, ids)
    }

    @Test fun `a stage opened after midnight in the same show keeps the played songs and the break counter`() {
        val (store, set, ids) = nightShow()
        clockAt(23, 50, plusMinutes = 20)   // 00:10 of the next day
        store.stageOpened(set)
        assertEquals(listOf(ids[0]), store.marks.value[set]?.played)
        assertEquals(ids[1], store.marks.value[set]?.current)
        assertTrue("the counter is still open", store.breakOpen(set, ids[1]))
    }

    @Test fun `from 5 in the morning it is another show - marks and counter start over`() {
        val (store, set, ids) = nightShow()
        clockAt(23, 50, plusMinutes = 5 * 60 + 11)   // 05:01
        store.stageOpened(set)
        assertNull(store.marks.value[set]?.played?.firstOrNull())
        assertFalse(store.breakOpen(set, ids[1]))
    }

    // ---- an old counter doesn't come back ----

    private fun withCounters(): Triple<SetlistShowStore, String, List<String>> {
        val repo = repo()
        val ids = repo.songs("A", "B", "C", "D")
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it); repo.addBreak(it) }   // breaks before C and D
        val store = SetlistShowStore(context, repo, scope())
        store.breakEntered(set, ids[2]); store.breakEntered(set, ids[3])
        repoOf[store] = repo
        return Triple(store, set, ids)
    }
    private val repoOf = HashMap<SetlistShowStore, LibraryRepository>()

    private fun waitFor(what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 3_000
        while (!check() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue(what, check())
    }

    @Test fun `a break taken out of the setlist leaves no counter to come back when it is put back`() {
        val (store, set, ids) = withCounters()
        val repo = repoOf.getValue(store)
        repo.removeBreak(set, 3)   // the break before D goes; D is still in the setlist
        waitFor("the counter of the removed break goes") { !store.breakOpen(set, ids[3]) }
        assertTrue("the break that is still there keeps its counter", store.breakOpen(set, ids[2]))
        repo.addBreak(set)         // a break before D again
        assertEquals(listOf(2, 3), repo.library.value.setlist(set)!!.breaks)
        assertFalse("a new break starts from zero, not from the old counter", store.breakOpen(set, ids[3]))
        assertTrue(store.breakOpen(set, ids[2]))
    }

    @Test fun `a break moved to another song leaves the old place's counter behind`() {
        val (store, set, ids) = withCounters()
        val repo = repoOf.getValue(store)
        repo.removeBreak(set, 2)   // the break before C goes; the one before D stays
        waitFor("the counter of the song that lost its break goes") { !store.breakOpen(set, ids[2]) }
        assertTrue("the break that stays keeps its own counter", store.breakOpen(set, ids[3]))
    }

    // ---- the stage resuming a break of a setlist that is gone ----

    @Test fun `resuming a break of a setlist deleted meanwhile throws nothing and leaves no break`() {
        val repo = repo()
        val ids = repo.songs("A", "B")
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
        val show = SetlistShowStore(context, repo, scope())
        show.breakEntered(set, ids[1])
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val stage = testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 0, "atBreak" to true)), show)
            repo.deleteSetlist(set)
            stage.songShown(0)
            assertNull(stage.breakSince.value)
            assertNull(stage.breakTimer.value)
        } finally { Dispatchers.resetMain() }
    }
}
