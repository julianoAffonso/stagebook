package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdateOffer
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.UpdateOffer
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/** Versão nova do app oferecida sozinha ao abrir, fora do palco, sem insistir no mesmo dia. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppUpdateOfferTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var server: HttpServer
    private var version = ""
    private val gate = StageGate()
    private var day = "20261021"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/version") { ex ->
            val b = version.toByteArray()
            ex.sendResponseHeaders(if (version.isEmpty()) 404 else 200, if (b.isEmpty()) -1 else b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private val order by lazy { br.app.stagebook.data.UpdateOrder(context).apply { today = { day } } }

    private fun offer(): AppUpdateOffer {
        val updater = AppUpdater(context).apply { versionUrl = "http://127.0.0.1:${server.address.port}/version" }
        return AppUpdateOffer(updater, gate, order, scope, Dispatchers.IO)
    }

    private fun Job?.await() = runBlocking { withTimeout(10_000) { this@await?.join() } }

    @Test fun `versão mais nova - oferece ao abrir`() {
        version = """{"versionCode":999999,"versionName":"0.3.999 (abc1234)"}"""
        val o = offer()
        o.onEnter().await()
        assertEquals(UpdateOffer.Available("0.3.999 (abc1234)"), o.offer.value)
    }

    @Test fun `mesma versão ou sem internet - nada a oferecer`() {
        val installed = AppUpdater(context).installedCode
        version = """{"versionCode":$installed,"versionName":"igual"}"""
        val o = offer()
        o.onEnter().await()
        assertNull(o.offer.value)
        version = ""
        offer().also { it.onEnter().await() }.let { assertNull(it.offer.value) }
    }

    @Test fun `no palco - espera sair para perguntar`() {
        version = """{"versionCode":999999,"versionName":"0.3.999"}"""
        gate.enter()
        val o = offer()
        val job = o.onEnter()
        Thread.sleep(300)
        assertNull("no palco não pergunta", o.offer.value)
        gate.leave()
        job.await()
        assertEquals(UpdateOffer.Available("0.3.999"), o.offer.value)
    }

    @Test fun `agora não - a mesma versão só volta no dia seguinte`() {
        version = """{"versionCode":999999,"versionName":"0.3.999"}"""
        val o = offer()
        o.onEnter().await()
        o.decline()
        assertNull(o.offer.value)
        o.onEnter().await()
        assertNull("mesmo dia: não insiste", o.offer.value)
        day = "20261022"
        o.onEnter().await()
        assertEquals(UpdateOffer.Available("0.3.999"), o.offer.value)
    }
}
