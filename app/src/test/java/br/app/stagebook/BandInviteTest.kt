package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The band invite over the internet (user, 07/11): only who invites, read as an invite, never as a setlist. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class BandInviteTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    private val cloud = SetlistCloud(LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)), identity)

    private fun otherDevice(name: String): br.app.stagebook.domain.BandProtocol.Hello {
        val key = br.app.stagebook.domain.BandCrypto.encode(br.app.stagebook.domain.BandCrypto.newKeyPair().public)
        return br.app.stagebook.domain.BandProtocol.Hello(br.app.stagebook.domain.BandCrypto.idOf(key), name, key)
    }

    @Test fun `an invite reads as an invite from who sent it`() {
        val ana = otherDevice("Ana")
        val r = cloud.read(cloud.invite(ana))
        assertTrue(r is SetlistCloud.Received.Invite)
        assertEquals(ana.id, (r as SetlistCloud.Received.Invite).from.id)
    }

    // audit 07/11: tapping your own link in a chat must not make you a member of your own band
    @Test fun `my own invite is nothing to accept`() =
        assertEquals(SetlistCloud.Received.NotFound, cloud.read(cloud.invite(identity.hello())))

    @Test fun `the mark alone, with nobody inviting, is nothing`() =
        assertEquals(SetlistCloud.Received.NotFound, cloud.read(cloud.invite(identity.hello()).drop(1)))

    @Test fun `who it says invites must hold its key`() {
        val forged = identity.hello().copy(id = "someone-else")
        assertEquals(SetlistCloud.Received.NotFound, cloud.read(cloud.invite(forged)))
    }

    @Test fun `the receive screen shows who invites and accepts`() {
        var accepted = 0
        var ui by androidx.compose.runtime.mutableStateOf<br.app.stagebook.ui.setlists.ScanUi>(br.app.stagebook.ui.setlists.ScanUi.Invite("Ana"))
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.setlists.ScanSetlistScreen(ui, false, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, onAcceptInvite = { accepted++ })
            }
        }
        compose.onNodeWithText("Convite para a banda").assertExists()
        compose.onNodeWithText("Ana convidou você para a banda", substring = true).assertExists()
        compose.onNodeWithText("Entrar na banda").performClick()
        assertEquals(1, accepted)
        ui = br.app.stagebook.ui.setlists.ScanUi.Invite("Ana", joined = true)
        compose.onNodeWithText("Você está na banda com Ana.").assertExists()
    }

    // whoever tapped a link never saw the 3 words (user, 07/11): the screen talks about the link
    @Test fun `from a link, the receive screen talks about the link`() {
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.setlists.ScanSetlistScreen(
                    br.app.stagebook.ui.setlists.ScanUi.Fetching(br.app.stagebook.ui.setlists.CodeProblem.NOT_FOUND),
                    false, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, byCode = true, fromLink = true,
                )
            }
        }
        compose.onNodeWithText("Recebendo pelo link").assertExists()
        compose.onNodeWithText("Esse link não vale mais", substring = true).assertExists()
        compose.onNodeWithText("palavras", substring = true).assertDoesNotExist()
    }
}
