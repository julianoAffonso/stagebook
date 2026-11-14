package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.goNow
import br.app.stagebook.ui.band.waiting
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.stage.FollowBreak
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The leader's break, for a follower who has the song, is drawn by the stage from state (not by navigation), so the
 * stage holds it too: with a window of ours open (the metronome here) the break doesn't come on behind it, the strip says
 * "<leader> went to the break" with "Go", and it shows when the window closes if the band is still on it. A break the
 * band left meanwhile never shows. Same rule as the song that waits (StageFollowWaitsTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageFollowBreakWaitsTest {
    @get:Rule val compose = createComposeRule()

    private val gate = FollowGate()
    private var rest by mutableStateOf<FollowBreak?>(null)
    private val song = StageSong("Primeira", "", "Letra da primeira", false, id = "a")

    private fun show(live: Boolean = true) {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    Box {
                        StageScreen(StageUiState(loading = false, songs = listOf(song), live = live, followBreak = rest), 0, 0, { _, _ -> })
                        // the strip as ConductRoute feeds it
                        val strip = ConductUi.Following("Voz", "Primeira", false, false, emptyList(), emptyList(), breakSince = rest?.since)
                        if (rest != null) ConductBar(strip, {}, {}, {}, {}, Modifier.align(Alignment.BottomCenter), waiting = gate.waiting(), onGo = gate::goNow)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun leaderBreak() { rest = FollowBreak(System.currentTimeMillis(), "Segunda", "G"); compose.waitForIdle() }
    private fun leaderLeavesBreak() { rest = null; compose.waitForIdle() }

    private fun openMetronome() {
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        compose.onNodeWithContentDescription("Mais devagar").assertExists()
    }

    private fun closeWindow() { compose.onNodeWithContentDescription("Fechar").performClick(); compose.waitForIdle() }

    @Test fun `with a window open the break waits, the strip says so, and it shows when the window closes`() {
        show()
        openMetronome()
        leaderBreak()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Voz foi para o intervalo").assertIsDisplayed()
        compose.onNodeWithText("Ir").assertIsDisplayed()
        assertTrue(gate.breakHeld)
        closeWindow()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onNodeWithText("Voz foi para o intervalo").assertDoesNotExist()
        assertFalse(gate.breakHeld)
    }

    @Test fun `Go shows the break at once, with the window still open`() {
        show()
        openMetronome()
        leaderBreak()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Ir").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        assertFalse(gate.breakHeld)
    }

    @Test fun `a break the band left while the window was open never shows`() {
        show()
        openMetronome()
        leaderBreak()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        leaderLeavesBreak()
        closeWindow()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Letra da primeira", substring = true).assertIsDisplayed()
        assertFalse(gate.breakHeld)
    }

    @Test fun `with no window open the break shows at once, as before`() {
        show()
        leaderBreak()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        assertFalse(gate.breakHeld)
    }

    @Test fun `a break already on screen when a window opens stays`() {
        leaderBreak()
        show()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        gate.menus++
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        assertFalse(gate.breakHeld)
    }

    @Test fun `a second break while the window is still open waits again`() {
        show()
        openMetronome()
        leaderBreak()
        leaderLeavesBreak()
        leaderBreak()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        assertTrue(gate.breakHeld)
        closeWindow()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
    }
}
