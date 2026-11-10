package br.app.stagebook.player

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.R
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

/**
 * The USB sign belongs to the wait (audit 10/11): with the track button set to pause, a tap while the interface was away
 * ended the wait and left the sign blinking on every song with a track, with nothing waiting for the interface any more.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class, shadows = [NoEngine::class])
class UsbAwayPauseTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val song = listOf(StageSong("Teste", "", "[G]Primeira linha", false, id = "s1", track = "faixa.ogg"))

    @After fun reset() {
        PlayerService.running = false
        PlayerService.connected = false
        PlayerService.playWanted = false
    }

    /** A track playing on the interface, and then the cable falls: the stage waits for it, with the sign. */
    private fun droppedWhilePlaying(): StagePlayer {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val player = StagePlayer(context, repo)
        PlayerService.running = true
        PlayerService.connected = true
        player.open(File(context.filesDir, "faixa.ogg"))
        player.play()
        runBlocking { withTimeout(5_000) { player.ui.first { it.playing } } }
        PlayerService.connected = false
        StagePlayer.onServiceChanged()
        assertEquals(PlayerUi(playing = false, waiting = true, away = true), player.ui.value)
        return player
    }

    @Test fun `a pause while the interface is away ends the sign with the wait`() {
        val player = droppedWhilePlaying()
        player.toggle(stops = false)
        assertEquals(PlayerUi(), player.ui.value)
    }

    @Test fun `turning the player off while the interface is away ends the sign`() {
        val player = droppedWhilePlaying()
        player.shutdown()
        assertFalse(player.ui.value.away)
    }

    @Test fun `the track button set to pause goes back to Play, without the USB sign`() {
        val player = droppedWhilePlaying()
        compose.setContent {
            StagebookTheme {
                // the player reaches the stage as in StageRoute
                val ui by player.ui.collectAsState()
                StageScreen(
                    StageUiState(loading = false, songs = song, look = Look(playerMode = true, trackStops = false)), 0, 0, { _, _ -> },
                    playing = ui.playing || ui.waiting, usbAway = ui.away, onTogglePlay = { player.toggle(stops = false) },
                )
            }
        }
        val waiting = context.getString(R.string.stage_track_usb_waiting)
        compose.onNodeWithContentDescription(waiting).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.stage_play)).assertExists()
        compose.onNodeWithContentDescription(waiting).assertDoesNotExist()
    }
}

/** The native engine isn't in the JVM: its library isn't loaded, and every call answers nothing (0, false, null). */
@Implements(Engine::class)
class NoEngine {
    companion object {
        @Suppress("FunctionName")   // the name Robolectric looks for
        @JvmStatic @Implementation fun __staticInitializer__() {}
    }
}
