package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
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

/** Auto-scroll (T15): ▶ starts it, tapping the screen pauses without changing songs, and −/+ change the speed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageAutoScrollTest {
    @get:Rule val compose = createComposeRule()

    private val long = (1..40).joinToString("\n") { "Linha $it da letra de exemplo" }
    private val state = StageUiState(false, listOf(StageSong("Longa", "", long, false), StageSong("Outra", "", "x", false)), look = Look(stageMode = StageMode.SCROLL))

    @Test fun `play scrolls by itself - tapping the screen pauses and stays on the same song`() {
        var song by mutableIntStateOf(0)
        var level = 0
        var page by mutableIntStateOf(0)
        compose.setContent {
            StagebookTheme {
                StageScreen(state, song, page, { s, p -> song = s; page = p }, onAutoScroll = { level = it })
            }
        }
        // clock stopped (frame by frame): waiting for idle, the test would move time until scrolling reached the end
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        fun open() { compose.onNodeWithContentDescription("Rolagem automática").performClick(); frames(3) }
        // play in the small window: it closes and the lyrics scroll; tapping the screen pauses, without changing songs
        open()
        compose.onNodeWithContentDescription("Rolar sozinho").performClick()
        frames(60)
        compose.onRoot().performTouchInput { click(centerRight.copy(x = centerRight.x - 50f)) }
        frames(10)
        assertEquals(0, song)
        // no drift: the pause position became step 1 (the next pedal press starts from it)
        assertEquals(1, page)
        open()
        compose.onNodeWithContentDescription("Rolar sozinho").assertExists()
        // again: while scrolling, the icon next to the clock becomes pause and stops right away
        compose.onNodeWithContentDescription("Rolar sozinho").performClick()
        frames(30)
        compose.onNodeWithContentDescription("Rolagem automática").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pausar a rolagem").performClick()
        frames(10)
        compose.onNodeWithContentDescription("Rolagem automática").assertExists()
        // × closes without changing anything
        open()
        compose.onNodeWithContentDescription("Fechar").performClick()
        frames(5)
        compose.onNodeWithContentDescription("Rolar sozinho").assertDoesNotExist()
        open()
        compose.onNodeWithContentDescription("Mais rápido").performClick()
        assertEquals(Look.DEFAULT_AUTO_SCROLL + 1, level)
    }
}
