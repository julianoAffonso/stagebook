package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BreakTimer
import br.app.stagebook.domain.BreakTimer.Timer
import br.app.stagebook.domain.Chords
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A setlist's break counters: which ones are kept, how a break is entered, and the undo of a removed song. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BreakCountersTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun repo() = LibraryRepository(context, scope())
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.songs(vararg titles: String) = titles.map { saveSong(Song(title = it, author = "x", text = "{key: G}\n$it")) }

    // ---- the break entered with nothing after it ----

    @Test fun `a break past the last song makes no counter under a made-up key`() {
        val repo = repo()
        val ids = repo.songs("A", "B", "C")
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
        val show = SetlistShowStore(context, repo, scope())
        val stage = testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 2)), show)
        stage.songShown(2)   // the last song: nothing comes after it
        stage.setBreak(true)
        assertNull(stage.breakSince.value)
        assertTrue("no counter at all, least of all a '#n' one: ${show.breaks.value.keys}", show.breaks.value.isEmpty())
    }

    @Test fun `entering a break hands back the counter it made, whatever else touched the store`() {
        val repo = repo()
        val ids = repo.songs("A", "B")
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
        val show = SetlistShowStore(context, repo, scope())
        show.now = { 1_000L }
        val first = show.breakEntered(set, ids[1])
        assertTrue(first.open && first.running)
        show.now = { 5_000L }
        assertEquals("a second entry goes on from the same start", first.startedAt, show.breakEntered(set, ids[1]).startedAt)
    }

    @Test fun `entered keeps a counter that exists and starts one that does not, and always has the key`() {
        val old = Timer("d", 100L, 50L, open = false)
        val kept = BreakTimer.entered(mapOf("s|a" to old), "s|a", "d2", 900L)
        assertEquals(old.copy(open = true), kept.getValue("s|a"))
        val fresh = BreakTimer.entered(emptyMap(), "s|a", "d2", 900L)
        assertEquals(BreakTimer.started("d2", 900L), fresh.getValue("s|a"))
    }

    // ---- an old counter doesn't come back ----

    private fun set(items: List<String>, breaks: List<Int>) = Setlist("s", "Show", items, breaks)
    private val t = Timer("d", 100L, 0L, open = true)

    @Test fun `a counter stays while its break is there`() {
        val kept = BreakTimer.pruned(mapOf("s|b" to t), mapOf("s" to set(listOf("a", "b", "c"), listOf(1))))
        assertEquals(setOf("s|b"), kept.keys)
    }

    @Test fun `a counter goes when its break is taken out or moved, though the song is still there`() {
        val all = mapOf("s|b" to t)
        assertTrue(BreakTimer.pruned(all, mapOf("s" to set(listOf("a", "b", "c"), emptyList()))).isEmpty())
        assertEquals(setOf("s|c"), BreakTimer.pruned(mapOf("s|b" to t, "s|c" to t), mapOf("s" to set(listOf("a", "b", "c"), listOf(2)))).keys)
    }

    @Test fun `a counter goes with its setlist or its song`() {
        assertTrue(BreakTimer.pruned(mapOf("s|b" to t), emptyMap()).isEmpty())
        assertTrue(BreakTimer.pruned(mapOf("s|z" to t), mapOf("s" to set(listOf("a", "b"), listOf(1)))).isEmpty())
    }
}
