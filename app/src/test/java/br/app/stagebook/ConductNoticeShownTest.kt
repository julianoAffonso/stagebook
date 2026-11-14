package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Arrived without chords" (and the other short notices of the band) has to be on screen when it happens, also when it
 * happens while the strip's screen is being redrawn (turning the device, a text size change): a notice used to be
 * dropped when nobody was collecting at that instant (two devices, 13/11: it didn't show on arrival).
 * ConductRoute's snackbar is the screen that draws it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class ConductNoticeShownTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val voice = BandProtocol.Hello("voz", "Voz", "key")
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda", "Banda"))

    private val repo = runBlocking {
        LibraryRepository(context, CoroutineScope(Dispatchers.IO)).also { r ->
            withTimeout(15_000) { r.loaded.first { it } }
            r.updateLook { it.copy(bandOn = true, chords = true) }
            withTimeout(15_000) { r.library.first { it.look.bandOn } }
        }
    }
    private val link = BandLink(
        context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(),
        br.app.stagebook.data.BandInbox(context), repo,
    )
    private val follower = BandConduct(link, repo, DeviceIdentity(context))
    private var shown by mutableStateOf(true)

    private fun following() {
        val msg = Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = songs, index = 1)
        link.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!)
    }

    /** The song comes from the band as the leader's loose song, with lyrics only. */
    private fun arrives() {
        val song = SetlistCloud.SharedSong("Segunda", "Banda", "", "Só a letra\nSem acordes")
        val list = SetlistCloud.looseList(listOf(song.title to song.author))
        link.accept(voice.id, voice.name, link.packageBytes(QrPayload.pack(list), listOf(song)))
    }

    private fun screen() {
        val vm = ConductViewModel(follower, repo, DeviceIdentity(context))
        compose.setContent {
            StagebookTheme {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val message = androidx.compose.runtime.remember { AppMessage(androidx.compose.material3.SnackbarHostState(), scope) }
                CompositionLocalProvider(LocalAppMessage provides message) {
                    Box {
                        if (shown) ConductRoute(viewModel = vm)
                        SnackbarHost(message.host)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the notice shows when the song arrives`() {
        screen(); following()
        arrives()
        noticeShown()
    }

    @Test fun `a notice that comes while the strip is being redrawn still shows`() {
        screen(); following()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        arrives()   // nobody is listening at this instant
        runBlocking { withTimeout(10_000) { repo.library.first { l -> l.songs.any { it.title == "Segunda" } } } }
        Thread.sleep(500)   // the notice has been emitted by now
        compose.runOnIdle { shown = true }
        noticeShown()
    }

    // the strip's own second line says the same words: the notice is the second one, in the snackbar
    private fun noticeShown() = compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("Segunda chegou sem cifra") >= 2 }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodesWithText(t).fetchSemanticsNodes().size
}
