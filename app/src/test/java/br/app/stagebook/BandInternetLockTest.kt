package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.BandProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Accepting the 3 words while a band sync runs: the "ok" waits for it instead of being overwritten (audit 30/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BandInternetLockTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `the accepted code is saved after the running sync, not lost`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val link = br.app.stagebook.data.BandLink(
            context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
            br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo,
        )
        val band = BandInternet(
            context, br.app.stagebook.data.SetlistCloud(repo, br.app.stagebook.data.DeviceIdentity(context)),
            br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.DeviceIdentity(context),
            br.app.stagebook.data.BandInbox(context), br.app.stagebook.data.Connectivity(context), link,
        )
        val lock = BandInternet::class.java.getDeclaredField("lock").apply { isAccessible = true }.get(band) as Mutex
        val accepted = BandInternet::class.java.getDeclaredMethod("accepted").apply { isAccessible = true }
        fun codes() = (accepted.invoke(band) as List<*>).map { it.toString() }
        lock.lock()   // a sync running
        band.accepted("47-violao-lua", BandProtocol.Hello("ana", "Celular da Ana", "k"))
        Thread.sleep(300)
        assertEquals(emptyList<String>(), codes())   // waits for the sync
        lock.unlock()
        withTimeout(5_000) { while (codes().isEmpty()) kotlinx.coroutines.delay(20) }
        assertTrue(codes().single().contains("47-violao-lua"))
    }
}
