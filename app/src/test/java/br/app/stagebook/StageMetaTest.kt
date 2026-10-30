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

/** Palco: artista ou ano trocado no editor aparece no subtítulo da música. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StageMetaTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `artista trocado no editor aparece no subtítulo do palco`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val id = repo.saveSong(Song(title = "Música", author = "Banda", year = "1985", text = "linha"))
        val vm = StageViewModel(repo, SavedStateHandle(mapOf("songId" to id, "start" to 0)), StageGate(), br.app.stagebook.data.TrackStore(context), br.app.stagebook.player.StagePlayer(context, repo),
            br.app.stagebook.data.BandConduct(
                br.app.stagebook.data.BandLink(
                    context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
                    StageGate(), br.app.stagebook.data.BandInbox(context), repo,
                ),
                repo, br.app.stagebook.data.DeviceIdentity(context),
            ),
            br.app.stagebook.data.MetronomeSound(),
            br.app.stagebook.data.ChordPlayer(),
            br.app.stagebook.data.StageAnnouncer(
                br.app.stagebook.data.BandLink(
                    context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
                    StageGate(), br.app.stagebook.data.BandInbox(context), repo,
                ),
            ),
        )
        assertEquals("Banda · 1985", withTimeout(5_000) { vm.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single().meta)
        repo.updateSong(id) { it.copy(author = "Banda ao vivo") }
        assertEquals("Banda ao vivo · 1985", withTimeout(5_000) { vm.uiState.first { it.songs.single().meta.startsWith("Banda ao vivo") } }.songs.single().meta)
    }
}
