package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Song
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
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val id = repo.saveSong(Song(title = "Música", author = "Banda", year = "1985", text = "linha"))
        val vm = stage(repo, id)
        assertEquals("Banda · 1985", withTimeout(5_000) { vm.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single().meta)
        repo.updateSong(id) { it.copy(author = "Banda ao vivo") }
        assertEquals("Banda ao vivo · 1985", withTimeout(5_000) { vm.uiState.first { it.songs.single().meta.startsWith("Banda ao vivo") } }.songs.single().meta)
    }

    @Test fun `key and capo changed on stage are saved in the song`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
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
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val id = repo.saveSong(Song(title = "Sem o áudio", text = "[G]linha", trackName = "Ao vivo"))
        val vm = stage(repo, id)
        assertEquals("Ao vivo", withTimeout(5_000) { vm.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single().lostTrack)
    }

    // a setlist's break (see StageBreakTest for the screen): the stage knows where the breaks are, and the time the
    // break started stays with the stage's saved state, so turning the device doesn't restart its counter
    @Test fun `the stage carries the setlist's breaks and keeps when a break started`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val ids = listOf("A", "B", "C").map { repo.saveSong(Song(title = it, text = "linha")) }
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
        val saved = SavedStateHandle(mapOf("setId" to set, "start" to 0))
        val vm = stage(repo, "", saved)
        assertEquals(listOf(2), withTimeout(5_000) { vm.uiState.first { it.songs.size == 3 && !it.loading } }.breaks)
        assertEquals(null, vm.breakSince.value)
        vm.setBreak(true)
        val since = vm.breakSince.value!!
        vm.setBreak(true)   // asked twice (a fast foot): the counter doesn't start over
        assertEquals(since, vm.breakSince.value)
        assertEquals(since, stage(repo, "", saved).breakSince.value)   // the stage built again from what was saved
        vm.setBreak(false)
        assertEquals(null, vm.breakSince.value)
    }

    private fun stage(repo: LibraryRepository, id: String, saved: SavedStateHandle = SavedStateHandle(mapOf("songId" to id, "start" to 0))) = testStage(context, repo, saved)
}

/** A stage with the real pieces around it (no audio plays in a test); [show] is where the setlist's marks go. */
internal fun testStage(
    context: android.app.Application,
    repo: LibraryRepository,
    saved: SavedStateHandle,
    show: br.app.stagebook.data.SetlistShowStore = br.app.stagebook.data.SetlistShowStore(context, repo, CoroutineScope(SupervisorJob() + Dispatchers.IO)),
) = StageViewModel(repo, saved, StageGate(), br.app.stagebook.data.TrackStore(context), br.app.stagebook.player.StagePlayer(context, repo),
    br.app.stagebook.data.BandConduct(
        br.app.stagebook.data.BandLink(
            context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
            StageGate(), br.app.stagebook.data.BandInbox(context), repo,
        ),
        repo, br.app.stagebook.data.DeviceIdentity(context),
    ),
    br.app.stagebook.data.MetronomeSound(context),
    br.app.stagebook.data.ChordPlayer(),
    br.app.stagebook.data.StageAnnouncer(
        br.app.stagebook.data.BandLink(
            context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
            StageGate(), br.app.stagebook.data.BandInbox(context), repo,
        ),
    ),
    br.app.stagebook.data.MidiOut(context, repo, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)),
    show,
)
