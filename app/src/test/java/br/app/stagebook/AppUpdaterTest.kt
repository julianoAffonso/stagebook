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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

/** Ajustes → Atualizar o app, com um "Drive" local no lugar do de verdade. */
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
        server.createContext("/versao") { ex ->
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

    private fun updater() = AppUpdater(context).apply {
        versionUrl = "http://127.0.0.1:${server.address.port}/versao"
        apkUrl = "http://127.0.0.1:${server.address.port}/apk"
    }

    private fun vm(u: AppUpdater) = LibraryRepository(context, CoroutineScope(Dispatchers.IO)).let { repo ->
        SettingsViewModel(repo, context, u, br.app.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)), br.app.stagebook.data.Backup(context, repo, br.app.stagebook.data.TrackStore(context)))
    }

    /** Espera o estado da atualização chegar à tela (o uiState só anda com alguém olhando). */
    private suspend fun SettingsViewModel.waitUpdate(done: (UpdateState) -> Boolean): UpdateState =
        withTimeout(10_000) { uiState.first { done(it.update) }.update }

    @Test fun `versão publicada mais nova aparece para baixar`() = runBlocking {
        version = """{"versionCode":999999,"versionName":"0.3.999 (abc1234)"}"""
        val vm = vm(updater())
        vm.checkUpdate()
        assertEquals(UpdateState.Available("0.3.999 (abc1234)"), vm.waitUpdate { it is UpdateState.Available })
    }

    @Test fun `mesma versão do instalado - já está em dia`() = runBlocking {
        val installed = updater().installedCode
        version = """{"versionCode":$installed,"versionName":"igual"}"""
        val vm = vm(updater())
        vm.checkUpdate()
        assertEquals(UpdateState.UpToDate, vm.waitUpdate { it == UpdateState.UpToDate })
    }

    @Test fun `sem a versão publicada - avisa para conferir a internet`() = runBlocking {
        version = ""
        val vm = vm(updater())
        vm.checkUpdate()
        assertEquals(UpdateState.Offline, vm.waitUpdate { it == UpdateState.Offline })
    }

    @Test fun `arquivo baixado que não é o app não é instalado`() = runBlocking {
        apk = ByteArray(200_000) { it.toByte() }   // página de erro, arquivo cortado…
        val vm = vm(updater())
        vm.downloadUpdate()
        assertEquals(UpdateState.DownloadFailed, vm.waitUpdate { it == UpdateState.DownloadFailed })
        assertFalse(File(context.cacheDir, "atualizacao/letras.apk").exists())
        assertEquals(null, vm.installIntent())
    }

}
