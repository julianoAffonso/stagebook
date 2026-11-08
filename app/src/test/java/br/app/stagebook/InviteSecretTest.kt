package br.app.stagebook

import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.ShareCode.LinkKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The band invite is unguessable (audit 07/11): 128 random bits ride in the link's fragment and enter the channel and
 * the key, so the 3 typed words alone (22 bits) can't find, read or answer an invite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class InviteSecretTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    private val cloud = SetlistCloud(LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)), identity)

    private val words = "47-violao-lua"
    private val secretA = "AAAAAAAAAAAAAAAAAAAAAA"
    private val secretB = "BBBBBBBBBBBBBBBBBBBBBB"

    @Test fun `a new invite carries a 128 bit secret that no two invites share`() {
        val a = ShareCode.generateInvite()
        val b = ShareCode.generateInvite()
        assertEquals(22, ShareCode.secretOf(a)!!.length)
        assertNotEquals(ShareCode.secretOf(a), ShareCode.secretOf(b))
        assertEquals(a, ShareCode.normalize(a))
        assertEquals(ShareCode.wordsOf(a), ShareCode.display(a))   // the screen never shows the secret
    }

    @Test fun `channel and key differ with another secret, and from the words alone`() {
        val a = ShareCode.keys("$words~$secretA")
        val b = ShareCode.keys("$words~$secretB")
        val bare = ShareCode.keys(words)
        assertNotEquals(a.topic, b.topic)
        assertNotEquals(a.topic, bare.topic)
        assertTrue(!a.key.contentEquals(b.key) && !a.key.contentEquals(bare.key))
        assertEquals(a.topic, ShareCode.keys("$words~$secretA").topic)
        // what the words alone derive is exactly as before (sends keep working with the words)
        assertEquals(ShareCode.keys("47-violao-lua").topic, bare.topic)
    }

    @Test fun `typing the words of an invite cannot read what the invite encrypted`() {
        val code = ShareCode.generateInvite()
        val msg = ShareCode.encrypt(ShareCode.keys(code).key, "x".toByteArray())
        assertNull(ShareCode.decrypt(ShareCode.keys(ShareCode.wordsOf(code)).key, msg))
        assertEquals("x", String(ShareCode.decrypt(ShareCode.keys(code).key, msg)!!))
    }

    @Test fun `the link round trips the secret`() {
        val code = "$words~$secretA"
        val link = ShareCode.link(code, LinkKind.INVITE, "Ana & Cia", lang = "pt-BR")
        assertTrue(link, link.startsWith("https://stagebook.app.br/r#$words."))
        assertTrue(link, !link.contains(secretA))   // inside the base64 details, not loose
        assertEquals(code, ShareCode.fromLink(link))
        assertEquals(code, ShareCode.normalize("Ana te chamou: $link. Vale 12 horas"))
        assertEquals(ShareCode.LinkInfo(code, LinkKind.INVITE, "Ana & Cia", lang = "pt-BR"), ShareCode.linkInfo(link))
        listOf("$link.", "($link)", "$link,").forEach { assertEquals(it, code, ShareCode.fromLink(it)) }
        // the words of an invite link are not enough for the sender's own copy of the code either
        assertEquals(code, ShareCode.normalize(code))
    }

    @Test fun `a hostile fragment gives words only, never a made up secret`() {
        fun details(json: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        listOf(
            """{"t":"i","k":"short"}""",
            """{"t":"i","k":"${"A".repeat(23)}"}""",
            """{"t":"i","k":"${"A".repeat(21)}!"}""",
            """{"t":"i","k":"<script>alert(1)</script>"}""",
            """{"t":"i","k":["$secretA"]}""",
            """{"t":"i","k":7}""",
        ).forEach { json ->
            val info = ShareCode.linkInfo("https://stagebook.app.br/r#$words.${details(json)}")
            assertEquals(json, words, info?.code)
        }
        assertNull(ShareCode.normalize("$words~short"))
        assertNull(ShareCode.normalize("$words~${secretA}x"))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/r#$words.${"k".repeat(5000)}") ?.takeIf { it != words })
        // the legacy "&k=" parameter is not a way in either
        assertEquals(words, ShareCode.fromLink("https://stagebook.app.br/r#$words&t=i&k=$secretA"))
    }

    @Test fun `an invite with a secret reads from the keys of its link and not from the words`() {
        val sent = cloud.invite(BandProtocol.Hello("a", "Ana", "k"))
        // the receive path decrypts with the code's keys: only the full code opens it
        val code = ShareCode.generateInvite()
        val encrypted = sent.map { ShareCode.encrypt(ShareCode.keys(code).key, it) }
        assertEquals(sent.size, encrypted.mapNotNull { ShareCode.decrypt(ShareCode.keys(code).key, it) }.size)
        assertEquals(0, encrypted.mapNotNull { ShareCode.decrypt(ShareCode.keys(ShareCode.wordsOf(code)).key, it) }.size)
    }

    @Test fun `an invite in the older format, with the 3 words only, still reads`() {
        val key = br.app.stagebook.domain.BandCrypto.encode(br.app.stagebook.domain.BandCrypto.newKeyPair().public)
        val inviter = BandProtocol.Hello(br.app.stagebook.domain.BandCrypto.idOf(key), "Ana", key)
        assertTrue(cloud.read(cloud.invite(inviter, version = 1)) is SetlistCloud.Received.Invite)
        assertTrue(cloud.read(cloud.invite(inviter)) is SetlistCloud.Received.Invite)
    }

    @Test fun `an invite from a newer format asks to update`() {
        val key = br.app.stagebook.domain.BandCrypto.encode(br.app.stagebook.domain.BandCrypto.newKeyPair().public)
        val inviter = BandProtocol.Hello(br.app.stagebook.domain.BandCrypto.idOf(key), "Ana", key, app = "9.9")
        assertEquals(SetlistCloud.Received.Newer("9.9"), cloud.read(cloud.invite(inviter, version = SetlistCloud.INVITE_VERSION + 1)))
    }

    @Test fun `typed words of a lost invite tell the person it needs its link`() {
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.setlists.ScanSetlistScreen(
                    br.app.stagebook.ui.setlists.ScanUi.Fetching(br.app.stagebook.ui.setlists.CodeProblem.NOT_FOUND),
                    false, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, byCode = true, fromLink = false,
                )
            }
        }
        compose.onNodeWithText("Se for um convite para a banda, ele só abre pelo link. Peça o link de novo a quem convidou.", substring = true).assertExists()
    }

    @Test fun `the invite message names no 3 words to type`() {
        val code = "$words~$secretA"
        val text = br.app.stagebook.ui.share.inviteMessage(context.getString(R.string.band_invite_message), "Ana", code, "pt-BR")
        assertTrue(text, !text.contains("digite") && !text.contains("3 palavras") && text.contains("só abre por esse link"))
        assertEquals(code, ShareCode.fromLink(text))
    }
}
