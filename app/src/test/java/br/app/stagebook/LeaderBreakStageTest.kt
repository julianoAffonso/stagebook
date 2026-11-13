package br.app.stagebook

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.stage.FollowBreak
import br.app.stagebook.ui.stage.LeaderBreakStage
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a follower without the leader's song sees on the leader's break: a stage with the break alone, and the break on
 * the band's strip (for whoever is on another screen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class LeaderBreakStageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(StageUiState(loading = false, live = true))
    private var left = 0
    private val pedal = PedalInput()

    private fun show(rest: FollowBreak?) {
        state = state.copy(followBreak = rest)
        compose.setContent {
            StagebookTheme { CompositionLocalProvider(LocalPedalInput provides pedal) { LeaderBreakStage(state, { left++ }) } }
        }
        compose.waitForIdle()
    }

    @Test fun `the break shows with the leader's time and the next song with its key`() {
        show(FollowBreak(System.currentTimeMillis() - 125_000, "Segunda", "G"))
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onNodeWithText("02:05").assertIsDisplayed()
        compose.onNode(hasText("A seguir: Segunda") and hasText(" · G")).assertIsDisplayed()
        assertEquals(0, left)
    }

    @Test fun `without the next song here it shows the title the leader said, with no key`() {
        show(FollowBreak(System.currentTimeMillis(), "Segunda"))
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("A seguir: Segunda").assertIsDisplayed()
        compose.onNodeWithText(" · ", substring = true).assertDoesNotExist()
    }

    @Test fun `a tap, a swipe and the pedal leave the break where it is`() {
        show(FollowBreak(System.currentTimeMillis(), "Segunda", "G"))
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0))
        }
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        assertEquals(0, left)
    }

    @Test fun `when the break is over with no song to open, the stage leaves by itself`() {
        show(FollowBreak(System.currentTimeMillis(), "Segunda"))
        state = state.copy(followBreak = null, breakOver = true)
        compose.waitForIdle()
        assertEquals(1, left)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
    }

    @Test fun `while the next song's stage is on its way nothing is left on screen, and the stage stays`() {
        show(FollowBreak(System.currentTimeMillis(), "Segunda"))
        state = state.copy(followBreak = null)
        compose.waitForIdle()
        assertEquals(0, left)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
    }

    @Test fun `the system's back asks before leaving the break`() {
        show(FollowBreak(System.currentTimeMillis(), "Segunda"))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Sair do palco?").assertIsDisplayed()
        assertEquals(0, left)
        compose.onNodeWithText("Sair").performClick()
        compose.waitForIdle()
        assertEquals(1, left)
    }

    // ---- the strip, for a follower on any screen ----

    private fun strip(ui: ConductUi.Following) {
        compose.setContent { StagebookTheme { ConductBar(ui, {}, {}, {}) } }
        compose.waitForIdle()
    }

    @Test fun `the strip says the break, its time and the next song`() {
        strip(ConductUi.Following("Voz", "Segunda", false, false, emptyList(), emptyList(), System.currentTimeMillis() - 125_000))
        compose.onNodeWithText("Acompanhando Voz").assertIsDisplayed()
        compose.onNodeWithText("Intervalo · 02:05 · A seguir: Segunda").assertIsDisplayed()
    }

    @Test fun `the strip says the next song is missing here, as it does for a song`() {
        strip(ConductUi.Following("Voz", "Segunda", true, false, emptyList(), emptyList(), System.currentTimeMillis() - 125_000))
        compose.onNodeWithText("Intervalo · 02:05 · Você não tem \"Segunda\"").assertIsDisplayed()
        compose.onNodeWithText("Sair").assertIsDisplayed()   // leaving the band is still there
    }

    @Test fun `with no break the strip is the one it was`() {
        strip(ConductUi.Following("Voz", "Primeira", true, false, emptyList(), emptyList()))
        compose.onNodeWithText("Você não tem \"Primeira\"").assertIsDisplayed()
        compose.onNodeWithText("Intervalo", substring = true).assertDoesNotExist()
    }
}
