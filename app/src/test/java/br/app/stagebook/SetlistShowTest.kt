package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.SetlistShow
import br.app.stagebook.domain.SetlistShow.Marks
import br.app.stagebook.domain.SetlistShow.State.NEXT
import br.app.stagebook.domain.SetlistShow.State.NONE
import br.app.stagebook.domain.SetlistShow.State.NOW
import br.app.stagebook.domain.SetlistShow.State.PLAYED
import br.app.stagebook.ui.setlists.SetlistDetailViewModel
import br.app.stagebook.ui.stage.StageViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the show is in its setlist: played after a minute on stage, the song on stage now and the next one. The marks
 * are this device's, last one day and follow the library (a setlist or a song that goes takes its marks along).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SetlistShowTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val main = UnconfinedTestDispatcher()   // the stage's minute runs on its clock: no test really waits

    @Before fun main() = Dispatchers.setMain(main)
    @After fun reset() = Dispatchers.resetMain()

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repo() = LibraryRepository(context, scope())
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun store(repo: LibraryRepository) = SetlistShowStore(context, repo, scope()).also { it.today = { "2026-11-13" } }

    /** A setlist "Show" with the songs A to E. */
    private fun LibraryRepository.show(): Pair<String, List<String>> {
        val ids = listOf("A", "B", "C", "D", "E").map { saveSong(Song(title = it, author = "x", text = "{key: G}\n{tempo: 120}\n{capo: 2}\n$it")) }
        return createSetlist("Show").also { addToSetlist(it, ids) } to ids
    }

    private fun stage(repo: LibraryRepository, show: SetlistShowStore, set: String) =
        testStage(context, repo, SavedStateHandle(mapOf("setId" to set, "start" to 0)), show)

    private fun wait(ms: Long) = main.scheduler.apply { advanceTimeBy(ms); runCurrent() }

    private fun SetlistShowStore.await(what: (Map<String, Marks>) -> Boolean) = runBlocking { withTimeout(5_000) { marks.first(what) } }

    // ---- what each row shows ----
    @Test fun `the song on stage is now, the one after it next and the ones marked played`() {
        assertEquals(listOf(PLAYED, NOW, NEXT, NONE), SetlistShow.states(listOf("a", "b", "c", "d"), Marks("d", listOf("a"), "b")))
    }

    @Test fun `there is no next when the last song is on stage`() {
        assertEquals(listOf(PLAYED, PLAYED, NOW), SetlistShow.states(listOf("a", "b", "c"), Marks("d", listOf("a", "b", "c"), "c")))
    }

    @Test fun `going back to a played song makes it now again, and the played one after it next`() {
        assertEquals(listOf(NOW, NEXT, PLAYED), SetlistShow.states(listOf("a", "b", "c"), Marks("d", listOf("a", "b", "c"), "a")))
    }

    @Test fun `with no marks no song has a state`() {
        assertEquals(listOf(NONE, NONE), SetlistShow.states(listOf("a", "b"), null))
        assertEquals(listOf(NONE, NONE), SetlistShow.states(listOf("a", "b"), Marks("d")))
    }

    // ---- the stage feeds the marks ----
    @Test fun `a song is played after a minute on stage, not before`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        stage(repo, show, set).songShown(0)
        assertEquals(Marks("2026-11-13", emptyList(), ids[0]), show.marks.value[set])
        wait(SetlistShow.PLAYED_AFTER_MS - 1_000)
        assertEquals(emptyList<String>(), show.marks.value[set]!!.played)
        wait(2_000)
        assertEquals(listOf(ids[0]), show.marks.value[set]!!.played)
        assertEquals(ids[0], show.marks.value[set]!!.current)
    }

    @Test fun `a song left before its minute is not played, and the next one counts its own`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        val stage = stage(repo, show, set)
        stage.songShown(0)
        wait(40_000)
        stage.songShown(1)
        wait(40_000)   // 80 s since the first one came up: it is gone, and the second has only 40 s
        assertEquals(Marks("2026-11-13", emptyList(), ids[1]), show.marks.value[set])
        wait(25_000)
        assertEquals(listOf(ids[1]), show.marks.value[set]!!.played)
    }

    @Test fun `the same song shown again goes on counting its minute`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        val stage = stage(repo, show, set)
        stage.songShown(0)
        wait(40_000)
        stage.songShown(0)   // the screen turned
        wait(25_000)
        assertEquals(listOf(ids[0]), show.marks.value[set]!!.played)
    }

    @Test fun `closing the stage before the minute leaves the song unplayed, and still the last one on stage`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        val owner = ViewModelStore()
        val stage = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = stage(repo, show, set) as T
        })[StageViewModel::class.java]
        stage.songShown(0)
        wait(30_000)
        owner.clear()
        wait(120_000)
        assertEquals(Marks("2026-11-13", emptyList(), ids[0]), show.marks.value[set])
    }

    @Test fun `a single song opened from the library marks nothing`() {
        val repo = repo()
        val (_, ids) = repo.show()
        val show = store(repo)
        testStage(context, repo, SavedStateHandle(mapOf("songId" to ids[0], "start" to 0)), show).songShown(0)
        wait(120_000)
        assertEquals(emptyMap<String, Marks>(), show.marks.value)
    }

    // ---- one day, and by hand ----
    @Test fun `opening the stage on another day clears the marks first, on the same day it keeps them`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        stage(repo, show, set).songShown(0)
        wait(61_000)
        stage(repo, show, set)   // later the same night
        assertEquals(Marks("2026-11-13", listOf(ids[0]), ids[0]), show.marks.value[set])
        show.today = { "2026-11-14" }
        assertEquals("looking at the setlist changes nothing", listOf(ids[0]), show.marks.value[set]!!.played)
        stage(repo, show, set)
        assertEquals(null, show.marks.value[set])
    }

    @Test fun `a show that goes past midnight keeps its marks`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        val stage = stage(repo, show, set)
        stage.songShown(0)
        wait(61_000)
        show.today = { "2026-11-14" }   // midnight, with the stage open
        stage.songShown(1)
        wait(61_000)
        stage(repo, show, set)   // left and opened again the same night: the last mark is from today
        assertEquals(Marks("2026-11-14", listOf(ids[0], ids[1]), ids[1]), show.marks.value[set])
    }

    @Test fun `clear played takes every mark of that setlist and of no other`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val other = repo.createSetlist("Outro").also { repo.addToSetlist(it, ids) }
        val show = store(repo)
        show.onStage(set, ids[0]); show.played(set, ids[0])
        show.onStage(other, ids[1])
        show.clear(set)
        assertEquals(mapOf(other to Marks("2026-11-13", emptyList(), ids[1])), show.marks.value)
    }

    // ---- the marks follow the library and stay on this device ----
    @Test fun `deleting the setlist removes its marks`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        show.onStage(set, ids[0]); show.played(set, ids[0])
        repo.deleteSetlist(set)
        show.await { set !in it }
    }

    @Test fun `a song deleted or taken out of the setlist leaves the marks`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        ids.take(3).forEach { show.played(set, it) }
        show.onStage(set, ids[2])
        repo.deleteSong(ids[0])
        show.await { it[set]?.played == listOf(ids[1], ids[2]) }
        repo.removeItem(set, 1, ids[2])   // the one on stage now: no "now" left
        assertEquals(Marks("2026-11-13", listOf(ids[1]), ""), show.await { it[set]?.current == "" }[set])
    }

    @Test fun `the marks survive closing the app`() {
        val repo = repo()
        val (set, ids) = repo.show()
        store(repo).apply { onStage(set, ids[1]); played(set, ids[0]) }
        assertEquals(Marks("2026-11-13", listOf(ids[0]), ids[1]), store(repo).marks.value[set])
    }

    @Test fun `marking changes nothing in the setlist that is shared`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val before = repo.library.value
        val qr = repo.qrPayload(set)!!
        store(repo).apply { onStage(set, ids[1]); played(set, ids[0]) }
        assertTrue(before === repo.library.value)
        assertTrue(qr.contentEquals(repo.qrPayload(set)!!))
    }

    // ---- the setlist screen's state ----
    private fun detail(repo: LibraryRepository, show: SetlistShowStore, set: String): SetlistDetailViewModel {
        val identity = DeviceIdentity(context)
        val trust = BandTrust(context)
        val inbox = BandInbox(context)
        val link = BandLink(context, identity, trust, StageGate(), inbox, repo)
        val cloud = SetlistCloud(repo, identity)
        val connectivity = Connectivity(context)
        return SetlistDetailViewModel(
            repo, SavedStateHandle(mapOf("setId" to set)), cloud, connectivity, link, trust,
            BandInternet(context, cloud, trust, identity, inbox, connectivity, link), br.app.stagebook.data.BandConduct(link, repo, identity), show,
        )
    }

    @Test fun `the setlist screen gets each song's key, tempo, capo, track and state`() = runBlocking {
        val repo = repo()
        val (set, ids) = repo.show()
        repo.updateSong(ids[1]) { it.copy(text = "no directives", track = "b.ogg") }
        repo.addBreak(set); repo.moveRow(set, 4, 2)   // A B | C D E
        val show = store(repo)
        show.played(set, ids[0]); show.onStage(set, ids[1])
        val ui = withTimeout(5_000) { detail(repo, show, set).uiState.first { !it.loading && it.anyPlayed && it.items.size == 5 } }
        assertEquals(listOf(2), ui.breaks)
        assertEquals("next is the song after the break", listOf(PLAYED, NOW, NEXT, NONE, NONE), ui.items.map { it.state })
        assertEquals(listOf("G", "", "G", "G", "G"), ui.items.map { it.songKey })
        assertEquals(listOf(120, 0, 120, 120, 120), ui.items.map { it.bpm })
        assertEquals(listOf(2, 0, 2, 2, 2), ui.items.map { it.capo })
        assertEquals(listOf(false, true, false, false, false), ui.items.map { it.hasTrack })
    }

    @Test fun `with only a song on stage there is nothing played to clear`() = runBlocking {
        val repo = repo()
        val (set, ids) = repo.show()
        val show = store(repo)
        show.onStage(set, ids[0])
        val vm = detail(repo, show, set)
        assertFalse(withTimeout(5_000) { vm.uiState.first { !it.loading && it.items.firstOrNull()?.state == NOW } }.anyPlayed)
        show.played(set, ids[0])
        withTimeout(5_000) { vm.uiState.first { it.anyPlayed } }
        vm.clearPlayed()
        assertEquals(listOf(NONE, NONE, NONE, NONE, NONE), withTimeout(5_000) { vm.uiState.first { !it.anyPlayed } }.items.map { it.state })
    }

    @Test fun `undo puts the removed song back in its place, with the breaks as they were`() {
        val repo = repo()
        val (set, ids) = repo.show()
        repo.addBreak(set); repo.moveRow(set, 4, 2)   // A B | C D E
        repo.addBreak(set)                             // A B | C D | E
        val before = repo.library.value.setlist(set)!!
        assertEquals(listOf(2, 4), before.breaks)
        val vm = detail(repo, store(repo), set)
        vm.remove(4, ids[4])   // E goes, and the break before it with it (a break never ends a setlist)
        assertEquals(listOf(2), repo.library.value.setlist(set)!!.breaks)
        vm.undoRemove()
        assertEquals(before, repo.library.value.setlist(set))
        vm.remove(1, ids[1])
        assertEquals(listOf(ids[0], ids[2], ids[3], ids[4]), repo.library.value.setlist(set)!!.items)
        vm.undoRemove()
        assertEquals(before, repo.library.value.setlist(set))
        vm.undoRemove()   // asked twice: nothing more to undo
        assertEquals(before, repo.library.value.setlist(set))
    }

    @Test fun `undo after the setlist changed puts the song back without doubling it, and never a deleted one`() {
        val repo = repo()
        val (set, ids) = repo.show()
        val vm = detail(repo, store(repo), set)
        vm.remove(1, ids[1])
        repo.moveItem(set, 0, 3)   // C D E A
        vm.undoRemove()
        assertEquals(listOf(ids[2], ids[1], ids[3], ids[4], ids[0]), repo.library.value.setlist(set)!!.items)
        vm.remove(1, ids[1])
        repo.addToSetlist(set, listOf(ids[1]))   // put back by hand meanwhile
        vm.undoRemove()
        assertEquals(1, repo.library.value.setlist(set)!!.items.count { it == ids[1] })
        vm.remove(0, ids[2])
        repo.deleteSong(ids[2])
        vm.undoRemove()
        assertFalse(ids[2] in repo.library.value.setlist(set)!!.items)
    }
}
