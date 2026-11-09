package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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

/**
 * In scroll mode the text moves up and down, so the gestures are vertical (user, 09/11): the lower half of the screen and a
 * drag up go forward, the upper half and a drag down go back. In page mode they stay left/right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageScrollGesturesTest {
    @get:Rule val compose = createComposeRule()

    private val long = (1..60).joinToString("\n") { "Linha $it da letra de exemplo" }
    private fun state(mode: StageMode) = StageUiState(false, listOf(StageSong("Longa", "", long, false), StageSong("Outra", "", "x", false)), look = Look(stageMode = mode))

    private fun run(mode: StageMode, steps: (frames: (Int) -> Unit, page: () -> Int) -> Unit) {
        var song by mutableIntStateOf(0)
        var page by mutableIntStateOf(0)
        compose.setContent { StagebookTheme { StageScreen(state(mode), song, page, { s, p -> song = s; page = p }) } }
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        frames(5)
        steps(::frames) { page }
    }

    @Test fun `scrolling, the lower half goes forward and the upper half comes back`() {
        run(StageMode.SCROLL) { frames, page ->
            fun tapAt(fx: Float, fy: Float) { compose.onRoot().performTouchInput { click(Offset(width * fx, height * fy)) }; frames(40) }
            tapAt(0.5f, 0.8f)
            assertEquals(1, page())
            tapAt(0.2f, 0.8f)   // the side doesn't matter any more: lower half
            assertEquals(2, page())
            tapAt(0.8f, 0.3f)   // upper half, even on the right
            assertEquals(1, page())
        }
    }

    @Test fun `scrolling, a drag up goes forward and a drag down comes back`() {
        run(StageMode.SCROLL) { frames, page ->
            fun drag(fromY: Float, toY: Float) { compose.onRoot().performTouchInput { swipe(Offset(width * 0.5f, height * fromY), Offset(width * 0.5f, height * toY), 300) }; frames(40) }
            drag(0.7f, 0.3f)
            assertEquals(1, page())
            drag(0.7f, 0.3f)
            assertEquals(2, page())
            drag(0.3f, 0.7f)
            assertEquals(1, page())
        }
    }

    @Test fun `in pages the right half still goes forward and the left half back`() {
        run(StageMode.PAGES) { frames, page ->
            fun tapAt(fx: Float, fy: Float) { compose.onRoot().performTouchInput { click(Offset(width * fx, height * fy)) }; frames(40) }
            tapAt(0.8f, 0.3f)
            assertEquals(1, page())
            tapAt(0.2f, 0.8f)
            assertEquals(0, page())
        }
    }
}
