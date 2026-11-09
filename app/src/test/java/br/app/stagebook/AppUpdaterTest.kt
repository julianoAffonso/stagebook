package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.ui.settings.SettingsViewModel
import br.app.stagebook.ui.settings.UpdateState
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** Settings → Update the app, with a local "Drive" in place of the real one. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppUpdaterTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var server: HttpServer
    private var version = ""
    private var apk = ByteArray(0)

    @Before fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/version") { ex ->
            val b = version.toByteArray()
            ex.sendResponseHeaders(if (version.isEmpty()) 404 else 200, if (b.isEmpty()) -1 else b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/apk") { ex ->
            ex.sendResponseHeaders(200, apk.size.toLong())
            ex.responseBody.use { it.write(apk) }
        }
        server.start()
    }

    @After fun stop() {
        server.stop(0)
        Dispatchers.resetMain()
    }

    /** A /flaky address that answers with each status of [statuses] in turn (the last one repeats); counts the hits. */
    private fun flaky(vararg statuses: Int, body: String = """{"versionCode":999999,"versionName":"0.3.999 (abc1234)"}"""): AtomicInteger {
        val hits = AtomicInteger()
        server.createContext("/flaky") { ex ->
            val status = statuses[minOf(hits.getAndIncrement(), statuses.size - 1)]
            val b = if (status == 200) body.toByteArray() else ByteArray(0)
            ex.sendResponseHeaders(status, if (b.isEmpty()) -1 else b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        return hits
    }

    private fun updater() = AppUpdater(context).apply {
        retryDelaysMs = longArrayOf(10, 10, 10)
        versionUrl = "http://127.0.0.1:${server.address.port}/version"
        apkUrl = "http://127.0.0.1:${server.address.port}/apk"
    }

    private fun vm(u: AppUpdater) = LibraryRepository(context, CoroutineScope(Dispatchers.IO)).let { repo ->
        SettingsViewModel(repo, context, u, br.app.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)), br.app.stagebook.data.Backup(context, repo, br.app.stagebook.data.TrackStore(context)), br.app.stagebook.data.MetronomeSound(context), br.app.stagebook.data.MidiOut(context, repo, CoroutineScope(Dispatchers.IO)))
    }

    /** Waits for the update state to reach the screen (uiState only moves with someone watching). */
    private suspend fun SettingsViewModel.waitUpdate(done: (UpdateState) -> Boolean): UpdateState =
        withTimeout(10_000) { uiState.first { done(it.update) }.update }

    @Test fun `a server error is tried again until it answers`() = runBlocking {
        val hits = flaky(503, 500, 200)
        val u = updater().apply { versionUrl = "http://127.0.0.1:${server.address.port}/flaky" }
        assertEquals("0.3.999 (abc1234)", u.published(maxAgeMs = 0)?.versionName)
        assertEquals(3, hits.get())
    }

    @Test fun `a client error is not tried again`() = runBlocking {
        val hits = flaky(404)
        val u = updater().apply { versionUrl = "http://127.0.0.1:${server.address.port}/flaky" }
        assertNull(u.published(maxAgeMs = 0))
        assertEquals(1, hits.get())
    }

    @Test fun `a server that never recovers gives up after the last try`() = runBlocking {
        val hits = flaky(503)
        val u = updater().apply { versionUrl = "http://127.0.0.1:${server.address.port}/flaky" }
        assertNull(u.published(maxAgeMs = 0))
        assertEquals(4, hits.get())
    }

    @Test fun `cancelling while waiting to try again stops at once`() = runBlocking {
        val hits = flaky(503)
        val u = updater().apply {
            versionUrl = "http://127.0.0.1:${server.address.port}/flaky"
            retryDelaysMs = longArrayOf(60_000)
        }
        val job = launch(Dispatchers.IO) { u.published(maxAgeMs = 0) }
        withTimeout(5_000) { while (hits.get() < 1) delay(10) }
        withTimeout(5_000) { job.cancelAndJoin() }
        assertEquals(1, hits.get())
    }

    @Test fun `newer published version shows up to download`() = runBlocking {
        version = """{"versionCode":999999,"versionName":"0.3.999 (abc1234)"}"""
        val vm = vm(updater())
        vm.checkUpdate()
        assertEquals(UpdateState.Available("0.3.999 (abc1234)"), vm.waitUpdate { it is UpdateState.Available })
    }

    @Test fun `same version as installed - already up to date`() = runBlocking {
        val installed = updater().installedCode
        version = """{"versionCode":$installed,"versionName":"igual"}"""
        val vm = vm(updater())
        vm.checkUpdate()
        assertEquals(UpdateState.UpToDate, vm.waitUpdate { it == UpdateState.UpToDate })
    }

    @Test fun `no published version found - says to check the internet`() = runBlocking {
        version = ""
        val vm = vm(updater())
        vm.checkUpdate()
        assertEquals(UpdateState.Offline, vm.waitUpdate { it == UpdateState.Offline })
    }

    @Test fun `downloaded file that is not the app is not installed`() = runBlocking {
        apk = ByteArray(200_000) { it.toByte() }   // error page, cut file…
        val vm = vm(updater())
        vm.downloadUpdate()
        assertEquals(UpdateState.DownloadFailed, vm.waitUpdate { it == UpdateState.DownloadFailed })
        assertFalse(File(context.cacheDir, "atualizacao/stagebook.apk").exists())
        assertEquals(null, vm.downloadedApk)
    }

}
