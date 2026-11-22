package br.app.stagebook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandSession
import br.app.stagebook.domain.BandCrypto
import br.app.stagebook.domain.BandProtocol
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The protocol version (user, 14/11: raised to 3 for the breaks and the missing-songs request; 15/11: to 4 for the PDF). Both directions of
 * the warning: another device on 3 (older) and on 5 (newer) talking to this one (4). Nobody saves anything, and the text the
 * person reads says to update the app on every band device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BandVersionWarningTest {
    private class Device(val name: String) {
        val keys = BandCrypto.newKeyPair()
        val pub = BandCrypto.encode(keys.public)
        val id = BandCrypto.idOf(pub)
        val hello = BandProtocol.Hello(id, name, pub, BandProtocol.VERSION)
        val trusted = mutableListOf<BandProtocol.Hello>()
    }

    /** [other] asks to join this device ([me], on the current version) saying [v]; what [other] gets, and whether anyone saved. */
    private fun join(v: Int): Pair<BandSession.JoinResult, Boolean> {
        val me = Device("Tablet da Ana")
        val other = Device("Celular do Beto")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val t = thread { runBlocking { BandSession(me.hello).serve(server.accept(), { false }, { true }, { me.trusted += it }) } }
        val r = BandSession(other.hello.copy(v = v, app = "x")).join(Socket(InetAddress.getLoopbackAddress(), server.localPort)) { other.trusted += it }
        t.join(5_000)
        server.close()
        return r to (me.trusted.isEmpty() && other.trusted.isEmpty())
    }

    /** This device on the current version asks to join [other], which says [v] (and answers VERSION, as the published app does). */
    private fun joinOther(v: Int): Pair<BandSession.JoinResult, Boolean> {
        val me = Device("Tablet da Ana")
        val other = Device("Celular do Beto")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val t = thread { runBlocking { BandSession(other.hello.copy(v = v)).serve(server.accept(), { false }, { true }, { other.trusted += it }) } }
        val r = BandSession(me.hello).join(Socket(InetAddress.getLoopbackAddress(), server.localPort)) { me.trusted += it }
        t.join(5_000)
        server.close()
        return r to (me.trusted.isEmpty() && other.trusted.isEmpty())
    }

    @Test fun `the protocol is on version 5`() = assertEquals(5, BandProtocol.VERSION)

    @Test fun `another device on 3 or 4 (published apps) - this one on 5 says version, and nothing is saved`() {
        assertEquals(BandSession.JoinResult.VERSION to true, join(4))
        assertEquals(BandSession.JoinResult.VERSION to true, joinOther(4))
        assertEquals(BandSession.JoinResult.VERSION to true, join(2))
        assertEquals(BandSession.JoinResult.VERSION to true, joinOther(2))
        assertEquals(BandSession.JoinResult.VERSION to true, join(3))
        assertEquals(BandSession.JoinResult.VERSION to true, joinOther(3))
    }

    @Test fun `another device on 6 (a newer app) - this one on 5 says version, and nothing is saved`() {
        assertEquals(BandSession.JoinResult.VERSION to true, join(6))
        assertEquals(BandSession.JoinResult.VERSION to true, joinOther(6))
    }

    @Test fun `the same version still talks`() {
        assertEquals(BandSession.JoinResult.ACCEPTED, join(BandProtocol.VERSION).first)
    }

    @Test fun `a Hello with no version is read as 2, because the published app never wrote it`() {
        val h = Json { ignoreUnknownKeys = true }.decodeFromString<BandProtocol.Hello>("""{"id":"i","name":"n","key":"k"}""")
        assertEquals(2, h.v)
        // a mismatch with this app (3), so a published app on the network gets "version" instead of being taken for current
        assertTrue(h.v != BandProtocol.VERSION)
    }

    @Test fun `the warning tells to update the app on every band device - both texts, both languages`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        fun text(lang: String, id: Int, vararg a: Any): String {
            val c = android.content.res.Configuration(ctx.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(lang)) }
            return ctx.createConfigurationContext(c).getString(id, *a)
        }
        // the list of nearby devices (BandSettings: StatusNote with the other app's version) and the join answer
        assertEquals("Different app version (0.2.5). Everyone in the band needs the latest version: update the app on every device.", text("en", R.string.band_other_version, "0.2.5"))
        assertEquals("You're on different app versions. Update the app on every band device to the latest version and try again.", text("en", R.string.band_version_join))
        assertEquals("Outra versão do app (0.2.5). Toda a banda precisa da versão mais nova: atualizem o app em todos os aparelhos.", text("pt-BR", R.string.band_other_version, "0.2.5"))
        assertEquals("Vocês estão em versões diferentes do app. Atualizem o app em todos os aparelhos da banda para a versão mais nova e tentem de novo.", text("pt-BR", R.string.band_version_join))
        assertTrue(text("en", R.string.inbox_newer, "Ana").contains("Update the app"))
    }
}
