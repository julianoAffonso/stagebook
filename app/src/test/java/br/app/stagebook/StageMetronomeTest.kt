package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage metronome (T33c): starts at the song's BPM (no BPM, 100), counts 1-2-3-4 and the icon becomes stop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageMetronomeTest {
    @get:Rule val compose = createComposeRule()

    private fun state(text: String) = StageUiState(false, listOf(StageSong("Música", "", text, false)), look = Look())

    @Test fun `without BPM starts at 100 - with BPM starts at it`() {
        compose.setContent { StagebookTheme { StageScreen(state("{tempo: 120}\nlinha"), 0, 0, { _, _ -> }) } }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        compose.onNodeWithText("120 BPM").assertExists()
        compose.onNodeWithContentDescription("Mais rápido").performClick()
        compose.onNodeWithText("121 BPM").assertExists()
    }

    @Test fun `play counts 1-2-3-4 and the icon becomes stop - stop stops`() {
        compose.setContent { StagebookTheme { StageScreen(state("linha"), 0, 0, { _, _ -> }) } }
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("100 BPM").assertExists()
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("1").assertExists()                           // contagem
        compose.onNodeWithContentDescription("Parar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithContentDescription("Metrônomo").assertExists()
    }

    // count-in "1 . 2 . 1 2 3 4" (user, 07/11): nothing on the pauses, at 100 BPM a beat is 600 ms
    @Test fun `the count-in shows 1, a pause, 2, a pause, then 1 2 3 4`() {
        compose.setContent { StagebookTheme { StageScreen(state("linha"), 0, 0, { _, _ -> }) } }
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        fun beat() { compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        frames(3)
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("1").assertExists()
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()   // to the middle of each beat from here
        beat()
        compose.onNodeWithText("1").assertDoesNotExist()
        compose.onNodeWithText("2").assertDoesNotExist()
        beat()
        compose.onNodeWithText("2").assertExists()
        beat(); beat()
        compose.onNodeWithText("1").assertExists()
        beat(); beat(); beat()
        compose.onNodeWithText("4").assertExists()
        beat()
        compose.onNodeWithText("4").assertDoesNotExist()
    }

    // after changing songs in a setlist (or the band leader moving on) the blink and the count-in followed the first
    // song's beat counter, which no longer moved: the metronome stayed on without blinking (device test, 02/11)
    @Test fun `after changing songs the metronome still counts`() {
        val two = StageUiState(false, listOf(StageSong("Uma", "", "linha", false, id = "a"), StageSong("Outra", "", "linha", false, id = "b")), look = Look())
        var index by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { StagebookTheme { StageScreen(two, index, 0, { s, _ -> index = s }) } }
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        index = 1
        frames(3)
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        frames(3)
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("1").assertExists()
    }

    // the user can hide it in Settings → Stage (user, 31/10)
    @Test fun `hidden in settings, no metronome on the stage`() {
        val hidden = StageUiState(false, listOf(StageSong("Música", "", "linha", false)), look = Look(metroShown = false))
        compose.setContent { StagebookTheme { StageScreen(hidden, 0, 0, { _, _ -> }) } }
        compose.onNodeWithContentDescription("Metrônomo").assertDoesNotExist()
    }
}
