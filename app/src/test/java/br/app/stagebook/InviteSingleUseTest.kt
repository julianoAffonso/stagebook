package br.app.stagebook

import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.StageGate
import br.app.stagebook.domain.BandCrypto
import br.app.stagebook.domain.BandProtocol.Hello
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.ui.setlists.ScanSetlistScreen
import br.app.stagebook.ui.setlists.ScanUi
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A band invite lets in only the first person who accepts (user, 07/11); a setlist send still lets in everyone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class InviteSingleUseTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    private val identity = DeviceIdentity(context)
    private val cloud = SetlistCloud(repo, identity)
    private val trust = BandTrust(context)

    private fun band(t: BandTrust = trust): Pair<BandInternet, BandLink> {
        val link = BandLink(context, identity, t, StageGate(), BandInbox(context), repo)
        return BandInternet(context, cloud, t, identity, BandInbox(context), Connectivity(context), link) to link
    }

    // remember() saves in the background: waits for the code to be there
    private fun BandInternet.rememberNow(code: String, invite: Boolean) = runBlocking {
        remember(code, invite)
        val codes = BandInternet::class.java.getDeclaredMethod("myCodes").apply { isAccessible = true }
        withTimeout(5_000) { while ((codes.invoke(this@rememberNow) as List<*>).none { it.toString().contains(code) }) delay(10) }
    }

    private val ana = Hello("ana", "Celular da Ana", "ka")
    private val bia = Hello("bia", "Tablet da Bia", "kb")

    private fun hello(name: String): Pair<Hello, java.security.KeyPair> {
        val keys = BandCrypto.newKeyPair()
        val key = BandCrypto.encode(keys.public)
        return Hello(BandCrypto.idOf(key), name, key) to keys
    }

    @Test fun `an invite lets in the first one and ignores the others, also later`() {
        val (net, _) = band()
        net.rememberNow("11-agua-lua", invite = true)
        assertEquals(listOf("ana"), net.admit("11-agua-lua", listOf(ana, bia)).map { it.id })
        assertTrue(trust.isMember("ana"))
        assertFalse(trust.isMember("bia"))
        assertEquals(emptyList<Hello>(), net.admit("11-agua-lua", listOf(ana, bia)))   // the next sync
        assertFalse(trust.isMember("bia"))
    }

    @Test fun `the used invite survives a restart`() {
        val (net, _) = band()
        net.rememberNow("11-agua-lua", invite = true)
        net.admit("11-agua-lua", listOf(ana))
        val again = BandTrust(context)
        val (restarted, _) = band(again)
        assertEquals(emptyList<Hello>(), restarted.admit("11-agua-lua", listOf(bia)))
        assertFalse(again.isMember("bia"))
    }

    @Test fun `a setlist send still lets in everyone who accepts`() {
        val (net, _) = band()
        net.rememberNow("12-agua-lua", invite = false)
        assertEquals(listOf("ana", "bia"), net.admit("12-agua-lua", listOf(ana, bia)).map { it.id })
    }

    @Test fun `each invite is used up on its own`() {
        val (net, _) = band()
        net.rememberNow("13-agua-lua", invite = true)
        net.rememberNow("14-agua-lua", invite = true)
        assertEquals(listOf("ana"), net.admit("13-agua-lua", listOf(ana)).map { it.id })
        assertEquals(listOf("bia"), net.admit("14-agua-lua", listOf(bia)).map { it.id })
    }

    @Test fun `someone already in the band doesn't use the invite up`() {
        val (net, _) = band()
        trust.add("caio", "Caio", "kc")
        net.rememberNow("15-agua-lua", invite = true)
        assertEquals(listOf("ana"), net.admit("15-agua-lua", listOf(Hello("caio", "Caio", "kc"), ana)).map { it.id })
    }

    @Test fun `an invite past its time lets nobody in`() {
        val (net, _) = band()
        val hour = 60L * 60 * 1000
        val created = System.currentTimeMillis() - 27 * hour   // 12 h of the invite + 12 h of the "ok" + margin, gone
        context.getSharedPreferences("band_net", 0).edit()
            .putString("codes", """[{"code":"16-agua-lua","until":${created + 30 * 24 * hour},"invite":true}]""").commit()
        assertEquals(emptyList<Hello>(), net.admit("16-agua-lua", listOf(ana)))
        assertFalse(trust.isMember("ana"))
    }

    @Test fun `on the same Wi-Fi the invite also lets in only the first one`() {
        val (net, link) = band()
        net.rememberNow("17-agua-lua", invite = true)
        val key = ShareCode.keys("17-agua-lua").key
        val (a, _) = hello("Ana")
        val (b, _) = hello("Bia")
        val handler = link.codeAckHandler!!
        assertTrue(handler(a, ShareCode.encrypt(key, cloud.ackMessage(a))))
        assertFalse(handler(b, ShareCode.encrypt(key, cloud.ackMessage(b))))
        assertTrue(trust.isMember(a.id))
        assertFalse(trust.isMember(b.id))
        assertTrue(handler(a, ShareCode.encrypt(key, cloud.ackMessage(a))))   // the first one again: still welcome
    }

    @Test fun `whoever opens an invite someone else took is told so`() {
        val (other, _) = hello("Bia")
        val (inviter, _) = hello("Ana")
        val r = cloud.read(cloud.invite(inviter) + cloud.ackMessage(other)) as SetlistCloud.Received.Invite
        assertTrue(r.used)
        // my own "ok" (the same link opened again after accepting) doesn't count
        val mine = cloud.read(cloud.invite(inviter) + cloud.ackMessage(identity.hello())) as SetlistCloud.Received.Invite
        assertFalse(mine.used)
        assertFalse((cloud.read(cloud.invite(inviter)) as SetlistCloud.Received.Invite).used)
    }

    @Test fun `the receive screen says the invite was used, with nothing to accept`() {
        compose.setContent {
            StagebookTheme { ScanSetlistScreen(ScanUi.Invite("Ana", used = true), false, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}) }
        }
        compose.onNodeWithText("Outra pessoa já usou este convite. Peça um novo para Ana.").assertExists()
        compose.onNodeWithText("Entrar na banda").assertDoesNotExist()
        compose.onNodeWithText("OK").assertExists()
    }
}
