package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandConduct.Role
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.band.ConductRoute
import br.app.stagebook.ui.band.ConductViewModel
import br.app.stagebook.ui.components.AppMessage
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.setlists.SetlistsRoute
import br.app.stagebook.ui.setlists.SetlistsViewModel
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V18: "Lead the band" is one armed button in the Setlists header. Leading always belongs to a setlist, so a tap with
 * none chosen arms it (lit, a short message, and the strip below says so with a Cancel); tapping a setlist's card opens
 * its stage and starts leading it; the icon again, or Cancel, disarms. The armed state lives in BandConduct, not in
 * the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class ConductArmedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)
    private val trust = BandTrust(context)
    private val voice = VOICE
    private val member = BandProtocol.Hello("armed-member", "Baixo", "key")
    private val played = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val sent = mutableListOf<Conduct.Msg>()

    private val repo = runBlocking {
        testRepo(bandOn = true)
    }
    private val link = testLink(repo = repo, identity = identity, trust = trust)
    private val conduct = testConduct(repo = repo, link = link, identity = identity).also { it.deliver = { m -> sent += m; emptyMap() } }
    private val setId = repo.createSetlist("Show de sábado").also { id ->
        repo.addToSetlist(id, listOf("A", "B", "C").map { repo.saveSong(Song(title = it, author = "x", text = it)) })
    }

    // the band and the library are saved on disk and outlive the test: leave them as found (a later test of the band
    // read this member and a band left off)
    @After fun clean() {
        trust.remove(member.id)
        repo.library.value.sets.forEach { repo.deleteSetlist(it.id) }
        repo.library.value.songs.forEach { repo.deleteSong(it.id) }
        repo.updateLook { it.copy(bandOn = true) }   // the default
    }

    private fun viewModel(): SetlistsViewModel {
        val cloud = br.app.stagebook.data.SetlistCloud(repo, identity)
        val connectivity = br.app.stagebook.data.Connectivity(context)
        val internet = br.app.stagebook.data.BandInternet(context, cloud, trust, identity, BandInbox(context), connectivity, link)
        val scope = testScope()
        return SetlistsViewModel(repo, connectivity, cloud, link, trust, internet, br.app.stagebook.data.SetlistShowStore(context, repo, scope), conduct)
    }

    private fun member() = trust.add(member.id, member.name, member.key)

    private fun screen(vm: SetlistsViewModel = viewModel()) {
        val strip = ConductViewModel(conduct, repo, identity)
        compose.setContent {
            StagebookTheme {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val message = androidx.compose.runtime.remember { AppMessage(androidx.compose.material3.SnackbarHostState(), scope) }
                CompositionLocalProvider(LocalAppMessage provides message) {
                    Box {
                        Column {
                            Box(androidx.compose.ui.Modifier.weight(1f)) { SetlistsRoute(onOpen = { opened += it }, onPlay = { id, _, _ -> played += id }, viewModel = vm) }
                            SnackbarHost(message.host)   // above the strip, as the app's host is
                            ConductRoute(viewModel = strip)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun button() = compose.onNodeWithContentDescription("Conduzir a banda")

    // ---- when the button is there ----

    @Test fun `without anyone in the band the button is not there`() {
        screen()
        compose.onAllNodesWithContentDescription("Conduzir a banda").assertCountEquals(0)
    }

    @Test fun `with the band off the button is not there`() {
        member(); repo.updateLook { it.copy(bandOn = false) }
        runBlocking { withTimeout(15_000) { repo.library.first { !it.look.bandOn } } }
        screen()
        compose.onAllNodesWithContentDescription("Conduzir a banda").assertCountEquals(0)
    }

    @Test fun `with the band on and someone in it the button is there, not lit`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        button().assertIsNotSelected()
        compose.onAllNodesWithText("Pronto para conduzir").assertCountEquals(0)
    }

    @Test fun `while leading the button is not there`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { conduct.start(setId, "Show de sábado", listOf(Conduct.Item("A")), 0) }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun `while following the button is not there`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        val msg = Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = listOf(Conduct.Item("A")), index = 0)
        compose.runOnIdle { link.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!) }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isEmpty() }
    }

    // ---- arming ----

    @Test fun `a tap arms it, lights the button, says what it waits for and the strip offers Cancel`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        button().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Pronto para conduzir").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(conduct.armed.value)
        button().assertIsSelected()
        compose.onNodeWithText("Escolha um setlist para começar a conduzir").assertExists()   // the short message
        compose.onNodeWithText("Escolha um setlist").assertExists()   // the strip's second line
        compose.onNodeWithText("Cancelar").assertExists()
        assertEquals(Role.None, conduct.role.value)   // nothing is led yet
    }

    @Test fun `tapping a setlist while armed opens its stage and starts leading it`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        button().performClick()
        compose.waitUntil(5_000) { conduct.armed.value }
        compose.onNodeWithText("Show de sábado").performClick()
        assertEquals(listOf(setId), played)
        val role = conduct.role.value as Role.Conducting
        assertEquals(setId, role.setId)
        assertEquals(listOf("A", "B", "C"), role.msg.songs.map { it.title })
        assertFalse(conduct.armed.value)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Conduzindo · Show de sábado").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Cancelar").assertCountEquals(0)
    }

    @Test fun `tapping a setlist when not armed only opens it, with no stage and no leading`() {
        member(); screen()
        compose.onNodeWithText("Show de sábado").performClick()
        assertEquals(listOf(setId), opened)
        assertEquals(emptyList<String>(), played)
        assertEquals(Role.None, conduct.role.value)
    }

    @Test fun `the icon again disarms`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        button().performClick()
        compose.waitUntil(5_000) { conduct.armed.value }
        button().performClick()
        compose.waitUntil(5_000) { !conduct.armed.value }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Pronto para conduzir").fetchSemanticsNodes().isEmpty() }
        button().assertIsNotSelected()
    }

    @Test fun `Cancel on the strip disarms`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        button().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Cancelar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitUntil(5_000) { !conduct.armed.value }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Pronto para conduzir").fetchSemanticsNodes().isEmpty() }
        button().assertIsNotSelected()
    }

    @Test fun `it stays armed with no time limit until a setlist is opened, also when opening another way`() {
        member(); screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Conduzir a banda").fetchSemanticsNodes().isNotEmpty() }
        button().performClick()
        compose.waitUntil(5_000) { conduct.armed.value }
        // sharing from the card menu (or a setlist reached by import, receive or create) does not start leading
        compose.onAllNodesWithContentDescription("Mais opções")[0].performClick()
        compose.onNodeWithText("Compartilhar").performClick()
        assertEquals(Role.None, conduct.role.value)
        assertTrue(conduct.armed.value)
    }

    @Test fun `it survives the screen being recreated`() {
        member()
        val first = viewModel()
        assertTrue(first.toggleConduct())
        val second = viewModel()   // a new screen, as after turning the device or changing tab
        screen(second)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Pronto para conduzir").fetchSemanticsNodes().isNotEmpty() }
        button().assertIsSelected()
    }

    @Test fun `an empty setlist does not start leading and stays armed`() {
        member()
        val empty = repo.createSetlist("Vazio")
        assertFalse(conduct.startIfArmed(empty, 0))
        conduct.arm()
        assertFalse(conduct.startIfArmed(empty, 0))
        assertEquals(Role.None, conduct.role.value)
        assertTrue(conduct.armed.value)
    }

    @Test fun `it starts from the song where the show stopped`() {
        member()
        conduct.arm()
        assertTrue(conduct.startIfArmed(setId, 2))
        assertEquals(2, (conduct.role.value as Role.Conducting).msg.index)
    }

    @Test fun `it cannot be armed while following`() {
        val msg = Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = listOf(Conduct.Item("A")), index = 0)
        link.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
        conduct.arm()
        assertFalse(conduct.armed.value)
    }

    @Test fun `the stage panel's lead keeps its action`() {
        member()
        assertTrue(conduct.startSetlist(setId, 1))
        assertEquals(1, (conduct.role.value as Role.Conducting).msg.index)
    }
}
