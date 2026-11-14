package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.ui.band.ConductRoute
import br.app.stagebook.ui.band.ConductViewModel
import br.app.stagebook.ui.components.AppMessage
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Assert.assertEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The list of what's missing on this device ("Faltam neste aparelho") follows the library: a song that arrived (asked for
 * with "Ask", or sent by the band) leaves it at once, and when it empties the window goes away (device test v0.1.1123:
 * it kept listing a song that had already arrived). The strip is the real one, over the real [BandConduct].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class LacksListFollowsLibraryTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val voice = BandProtocol.Hello("voz", "Voz", "key")
    private val songs = listOf(Conduct.Item("Primeira", "Banda"), Conduct.Item("Segunda", "Banda"), Conduct.Item("Terceira", "Banda"))

    private val repo = runBlocking {
        LibraryRepository(context, CoroutineScope(Dispatchers.IO)).also { r ->
            withTimeout(15_000) { r.loaded.first { it } }
            r.updateLook { it.copy(bandOn = true, chords = false) }
            withTimeout(15_000) { r.library.first { it.look.bandOn } }
        }
    }
    private val link = BandLink(
        context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
        br.app.stagebook.data.BandInbox(context), repo,
    )
    private val follower = BandConduct(link, repo, DeviceIdentity(context))

    private fun following(index: Int = 2) {
        val msg = Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = songs, index = index)
        link.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
    }

    /** A song arrives as the leader's loose song. */
    private fun arrives(title: String) {
        val song = SetlistCloud.SharedSong(title, "Banda", "", "Só a letra\nSem acordes")
        link.accept(voice.id, voice.name, link.packageBytes(QrPayload.pack(SetlistCloud.looseList(listOf(song.title to song.author))), listOf(song)))
        runBlocking { withTimeout(10_000) { repo.library.first { l -> l.songs.any { it.title == title } } } }
        compose.waitForIdle()
    }

    private fun screen() {
        val vm = ConductViewModel(follower, repo, DeviceIdentity(context))
        compose.setContent {
            StagebookTheme {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val message = androidx.compose.runtime.remember { AppMessage(androidx.compose.material3.SnackbarHostState(), scope) }
                CompositionLocalProvider(LocalAppMessage provides message) { ConductRoute(viewModel = vm) }
            }
        }
        compose.waitForIdle()
    }

    private fun inWindow(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test fun `a song that arrived leaves the list, and the last one closes the window`() {
        screen(); following()
        compose.onNodeWithText("Voz", substring = true).performClick()
        compose.onNodeWithText("Faltam neste aparelho").assertExists()
        compose.waitUntil(5_000) { inWindow("Primeira\nSegunda\nTerceira") > 0 }
        arrives("Primeira")
        compose.waitUntil(5_000) { inWindow("Primeira") == 0 }
        compose.onNodeWithText("Segunda\nTerceira").assertExists()
        arrives("Segunda"); arrives("Terceira")
        compose.waitUntil(5_000) { inWindow("Faltam neste aparelho") == 0 }
    }

    // ---- the leader's list ----

    private val leaderLink = BandLink(
        context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
        br.app.stagebook.data.BandInbox(context), repo,
    )
    private val leader = BandConduct(leaderLink, repo, DeviceIdentity(context))
    private var missing = listOf("Primeira")   // what "f" says it lacks at the next message

    private fun lead() {
        leader.askedRefreshMs = 50
        leader.deliver = { _ -> mapOf("f" to Conduct.Lack("F", missing), "g" to Conduct.Lack("G", emptyList())) }
        leader.start("s1", "Show", songs, 0)
        runBlocking { withTimeout(10_000) { while ((leader.role.value as? BandConduct.Role.Conducting)?.lackers?.keys != setOf("f")) delay(10) } }
    }

    private fun lackers() = (leader.role.value as? BandConduct.Role.Conducting)?.lackers?.keys

    @Test fun `a follower that asked the leader for the song and got it leaves the leader's list by itself`() {
        lead()
        missing = emptyList()   // F has it now
        leaderLink.askedHandler!!.invoke("f", listOf(BandLink.Wanted("Primeira", "Banda")))
        runBlocking { withTimeout(10_000) { while (lackers() != emptySet<String>()) delay(10) } }
    }

    @Test fun `someone else's request, or a song nobody lacks, changes nothing`() {
        lead()
        missing = emptyList()
        leaderLink.askedHandler!!.invoke("g", listOf(BandLink.Wanted("Primeira", "Banda")))   // G lacks nothing
        leaderLink.askedHandler!!.invoke("f", listOf(BandLink.Wanted("Outra", "Banda")))      // not the one F lacks
        runBlocking { delay(400) }
        assertEquals(setOf("f"), lackers())
    }

    @Test fun `the leader's strip shows the list as it is now, and the window goes when it empties`() {
        var ui by mutableStateOf<ConductUi>(
            ConductUi.Conducting("Show", 0, emptyList(), listOf(Conduct.Lack("F", listOf("Primeira", "Segunda"))), listOf(ConductUi.Lacker("f", "F", listOf("Primeira", "Segunda")))),
        )
        compose.setContent { StagebookTheme { ConductBar(ui, {}, {}, {}, {}) } }
        compose.onNodeWithText("Ver o que falta", substring = true).performClick()
        compose.onNodeWithText("F: Primeira, Segunda", substring = true).assertExists()
        ui = ConductUi.Conducting("Show", 0, emptyList(), listOf(Conduct.Lack("F", listOf("Segunda"))), listOf(ConductUi.Lacker("f", "F", listOf("Segunda"))))
        compose.waitForIdle()
        compose.onNodeWithText("F: Segunda", substring = true).assertExists()
        compose.onAllNodesWithText("Primeira", substring = true).assertCountEquals(0)
        ui = ConductUi.Conducting("Show", 0, emptyList(), emptyList(), emptyList())
        compose.waitForIdle()
        compose.onAllNodesWithText("O que falta", substring = true).assertCountEquals(0)
    }
}
