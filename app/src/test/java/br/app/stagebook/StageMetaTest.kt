package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.SetlistShow.State
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage: artist or year changed in the editor shows in the song subtitle; key and capo changed on stage are saved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StageMetaTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `artist changed in the editor shows in the stage subtitle`() = runBlocking {
        val repo = testRepo()
        val id = repo.saveSong(Song(title = "Música", author = "Banda", year = "1985", text = "linha"))
        val vm = stage(repo, id)
        assertEquals("Banda · 1985", withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single().meta)
        repo.updateSong(id) { it.copy(author = "Banda ao vivo") }
        assertEquals("Banda ao vivo · 1985", withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.songs.single().meta.startsWith("Banda ao vivo") } }.songs.single().meta)
    }

    @Test fun `key and capo changed on stage are saved in the song`() = runBlocking {
        val repo = testRepo()
        val id = repo.saveSong(Song(title = "Tom no palco", text = "{key: G}\n[G]linha [D]outra"))
        val vm = stage(repo, id)
        vm.transpose(id, 2)
        vm.setCapo(id, 3)
        val saved = repo.song(id)!!.text
        assertEquals("A", br.app.stagebook.domain.Chords.key(saved))
        assertEquals(3, br.app.stagebook.domain.Chords.capo(saved))
        assertEquals(true, saved.contains("[A]linha [E]outra"))
    }

    @Test fun `the stage carries the name of the audio that did not come`() = runBlocking {
        val repo = testRepo()
        val id = repo.saveSong(Song(title = "Sem o áudio", text = "[G]linha", trackName = "Ao vivo"))
        val vm = stage(repo, id)
        assertEquals("Ao vivo", withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single().lostTrack)
    }

    // a setlist's break (see StageBreakTest for the screen): the stage knows where the breaks are, and the time the
    // break started stays with the stage's saved state, so turning the device doesn't restart its counter
    @Test fun `the stage carries the setlist's breaks and keeps when a break started`() = runBlocking {
        val repo = testRepo()
        val ids = listOf("A", "B", "C").map { repo.saveSong(Song(title = it, text = "linha")) }
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
        val saved = SavedStateHandle(mapOf("setId" to set, "start" to 0))
        val vm = stage(repo, "", saved)
        assertEquals(listOf(2), withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.songs.size == 3 && !it.loading } }.breaks)
        assertEquals(null, vm.breakSince.value)
        vm.setBreak(true)
        val since = vm.breakSince.value!!
        vm.setBreak(true)   // asked twice (a fast foot): the counter doesn't start over
        assertEquals(since, vm.breakSince.value)
        assertEquals(since, stage(repo, "", saved).breakSince.value)   // the stage built again from what was saved
        vm.setBreak(false)
        assertEquals(null, vm.breakSince.value)
    }

    // the setlist over the stage (see StageSetlistPanelTest for the screen): only a stage opened from a setlist has
    // one, with its name, each song's key and the same marks the setlist's screen shows
    @Test fun `a stage opened from a setlist carries it, and a single song carries none`() = runBlocking {
        val repo = testRepo()
        val ids = listOf("{key: G}\n{tempo: 120}\n{capo: 2}\nlinha", "linha", "{key: Em}\nlinha").mapIndexed { i, text -> repo.saveSong(Song(title = "Painel $i", text = text, track = if (i == 0) "a.ogg" else "")) }
        val set = repo.createSetlist("Show do painel").also { repo.addToSetlist(it, ids) }
        val vm = stage(repo, "", SavedStateHandle(mapOf("setId" to set, "start" to 0)))
        val opened = withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.songs.size == 3 && !it.loading } }.setlist!!
        assertEquals("Show do painel", opened.name)
        assertEquals(listOf("G", "", "Em"), opened.facts.map { it.key })
        // the tempo, the capo and the backing track come to the panel's rows too
        assertEquals(listOf(120, 0, 0), opened.facts.map { it.bpm })
        assertEquals(listOf(2, 0, 0), opened.facts.map { it.capo })
        assertEquals(listOf(true, false, false), opened.facts.map { it.hasTrack })
        vm.songShown(1)
        assertEquals(listOf(State.NONE, State.NOW, State.NEXT), withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.setlist?.states?.contains(State.NOW) == true } }.setlist!!.states)

        val single = stage(repo, ids[0])
        assertEquals(null, withTimeout(TEST_WAIT_MS) { single.uiState.first { it.songs.isNotEmpty() && !it.loading } }.setlist)
    }

    // back to the song to check something and on to the break again: the break goes on counting (user, 13/11)
    @Test fun `a break keeps its start when left backward or forward and found again`() = runBlocking {
        val repo = testRepo()
        val ids = listOf("A", "B", "C").map { repo.saveSong(Song(title = it, text = "linha")) }
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it); repo.addBreak(it) }   // breaks before B and C: the counter belongs to a real break
        val saved = SavedStateHandle(mapOf("setId" to set, "start" to 0))
        val vm = stage(repo, "", saved)
        withTimeout(TEST_WAIT_MS) { vm.uiState.first { it.songs.size == 3 && !it.loading } }
        vm.songShown(0)
        vm.setBreak(true)
        val since = vm.breakSince.value!!
        vm.setBreak(false)   // back to the song before the break
        assertEquals(null, vm.breakSince.value)
        Thread.sleep(20)   // a new start would be a later time
        vm.songShown(0)    // the same song shown again (the screen turned)
        vm.setBreak(true)
        assertEquals("the break goes on from where it started", since, vm.breakSince.value)
        // the stage built again from what was saved, on the song before the break: still the same start
        vm.setBreak(false)
        val again = stage(repo, "", saved)
        again.songShown(0)
        again.setBreak(true)
        assertEquals(since, again.breakSince.value)

        vm.setBreak(false)   // left forward: the next song opens
        vm.songShown(1)
        vm.songShown(0)      // and back on the first song: the break goes on (user, 14/11; only a played next song ends it)
        vm.setBreak(true)
        assertEquals(since, vm.breakSince.value)
    }

    private fun stage(repo: LibraryRepository, id: String, saved: SavedStateHandle = SavedStateHandle(mapOf("songId" to id, "start" to 0))) = testStage(context, repo, saved)
}
