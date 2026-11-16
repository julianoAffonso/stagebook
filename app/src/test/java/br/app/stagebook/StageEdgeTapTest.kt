package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.STAGE_EDGE_LEFT_TAG
import br.app.stagebook.ui.stage.STAGE_EDGE_RIGHT_TAG
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
 * A tap on the very edge of the stage turns the page (user, 16/11: "the touch right at the edges doesn't turn the page").
 * The lyrics' side margin was outside the page's touch area, so a thumb resting at the edge of the device did nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageEdgeTapTest {
    @get:Rule val compose = createComposeRule()
    private var page by mutableIntStateOf(0)

    private fun show(look: Look = Look(fontSp = 34f)) {
        val text = (1..40).joinToString("\n\n") { s -> (1..4).joinToString("\n") { "estrofe $s linha $it" } }
        val state = StageUiState(loading = false, songs = listOf(StageSong("Longa", "", text, false)), look = look)
        compose.setContent { StagebookTheme { StageScreen(state, 0, page, { _, p -> page = p }) } }
        compose.waitForIdle()
    }

    @Test fun `the last pixels of the right edge go forward and those of the left edge go back`() {
        show()
        // well below the menu handle, which sits at the middle of the left edge; the outermost pixel of each side
        compose.onNodeWithTag(STAGE_EDGE_RIGHT_TAG).performTouchInput { click(Offset(width - 1f, height * 0.8f)) }
        compose.waitForIdle()
        assertEquals(1, page)
        compose.onNodeWithTag(STAGE_EDGE_LEFT_TAG).performTouchInput { click(Offset(0f, height * 0.8f)) }
        compose.waitForIdle()
        assertEquals(0, page)
    }

    @Test fun `scrolling, the lower part of an edge goes forward and the upper part goes back`() {
        show(Look(fontSp = 34f, stageMode = br.app.stagebook.data.model.StageMode.SCROLL))
        compose.onNodeWithTag(STAGE_EDGE_LEFT_TAG).performTouchInput { click(Offset(2f, height * 0.9f)) }
        compose.waitForIdle()
        val after = page
        assert(after > 0) { "the lower part of the left edge went forward: $after" }
        compose.onNodeWithTag(STAGE_EDGE_RIGHT_TAG).performTouchInput { click(Offset(2f, height * 0.1f)) }
        compose.waitForIdle()
        assert(page < after) { "the upper part of the right edge went back: $page" }
    }
}
