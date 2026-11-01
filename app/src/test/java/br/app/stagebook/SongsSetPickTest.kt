package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.songs.AddResult
import br.app.stagebook.ui.songs.SongsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Add to which setlist?" (audit 01/11): follows the setlists as they change, and a deleted one adds nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SongsSetPickTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `a setlist created after opening shows in the picker - a deleted one says it is gone`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
            withTimeout(5_000) { repo.loaded.first { it } }
            val song = repo.saveSong(Song(title = "Uma", author = "Banda", text = "letra"))
            val identity = br.app.stagebook.data.DeviceIdentity(context)
            val link = br.app.stagebook.data.BandLink(
                context, identity, br.app.stagebook.data.BandTrust(context),
                br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo,
            )
            val cloud = br.app.stagebook.data.SetlistCloud(repo, identity)
            val vm = SongsViewModel(
                repo, br.app.stagebook.data.Connectivity(context), cloud, link, br.app.stagebook.data.BandTrust(context),
                BandInternet(context, cloud, br.app.stagebook.data.BandTrust(context), identity, br.app.stagebook.data.BandInbox(context), br.app.stagebook.data.Connectivity(context), link),
            )
            val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.uiState.collect {} }
            withTimeout(5_000) { vm.uiState.first { !it.loading } }
            val id = repo.createSetlist("Sexta")
            withTimeout(5_000) { vm.uiState.first { s -> s.sets.any { it.id == id } } }
            repo.deleteSetlist(id)
            assertEquals(AddResult.Gone, vm.addToSetlist(listOf(song), id, ""))
            job.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
