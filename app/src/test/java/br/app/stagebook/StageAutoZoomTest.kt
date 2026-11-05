package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.domain.StageFit
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A song nobody zoomed fills the page of this device; a chosen zoom always wins and nothing automatic is saved. Real font
 * measurement: by default Robolectric gives each letter 1 px, and a squeezed or moving notice goes unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageAutoZoomTest {
    @get:Rule val compose = createComposeRule()

    // no section declared: 5 verses of 4 lines, more than a page at 30 sp on this phone and less than one at 22 sp
    private val verses = (1..5).joinToString("\n\n") { v -> (1..4).joinToString("\n") { "Verso $v linha $it" } }
    private val saved = ArrayList<Pair<String, Float>>()

    private fun show(zoom: Float = 1f, look: Look = Look(fontSp = 30f, columns = StageColumns.ONE)) {
        val state = StageUiState(false, listOf(StageSong("Longa", "", verses, false, id = "a", zoom = zoom)), look = look)
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onZoom = { id, z -> saved += id to z }) } }
        compose.waitForIdle()
    }

    private fun pages() = compose.onAllNodesWithText("2").fetchSemanticsNodes().size

    private fun pinch() {
        compose.onRoot().performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(250f, 0f), center + Offset(50f, 0f), center + Offset(250f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
    }

    @Test fun `a song without a zoom fits the page`() {
        show()
        assertEquals(0, pages())
    }

    @Test fun `opening a song saves nothing`() {
        show()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertTrue(saved.toString(), saved.isEmpty())
    }

    @Test fun `switched off, the size in Settings stays`() {
        show(look = Look(fontSp = 30f, columns = StageColumns.ONE, autoZoom = false))
        assertEquals(1, pages())
    }

    @Test fun `100 percent chosen on purpose stays`() {
        show(zoom = StageFit.CHOSEN_DEFAULT_ZOOM)
        assertEquals(1, pages())
    }

    @Test fun `the pinch badge goes back to automatic`() {
        show()
        pinch()
        compose.onNodeWithText("Automático").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("a" to 1f, saved.last())
    }

    @Test fun `both badge buttons fit on a phone`() {
        show()
        pinch()
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.right
        listOf("Automático", "Padrão").forEach {
            val box = compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
            assertTrue("$it passou da borda: $box", box.right <= width + 0.5f && box.left >= 0f)
            assertTrue("$it espremido: $box", box.width > box.height)
        }
    }

    @Test fun `the notice stays still while the percent changes`() {
        show()
        fun pinchBy(from: Float, to: Float) {
            compose.onRoot().performTouchInput {
                pinch(center - Offset(from, 0f), center - Offset(to, 0f), center + Offset(from, 0f), center + Offset(to, 0f), durationMillis = 400)
            }
            compose.waitForIdle()
        }
        fun place() = compose.onNodeWithText("Automático").fetchSemanticsNode().boundsInRoot.left
        fun percent() = compose.onNodeWithText("Zoom em", substring = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        pinchBy(250f, 200f)
        val small = place()
        val smallText = percent()
        pinchBy(100f, 300f)
        assertTrue("o percentual não mudou de tamanho: $smallText, ${percent()}", smallText.length < percent().length)
        assertEquals(small, place(), 0.5f)
    }

    @Test fun `at 100 percent the notice keeps its place`() {
        show(zoom = 1.3f)
        pinch()
        val before = compose.onNodeWithText("Automático").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithText("Padrão").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Padrão").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithText("Automático").fetchSemanticsNode().boundsInRoot.left, 0.5f)
    }

    @Test fun `the pinch badge also chooses 100 percent`() {
        show()
        pinch()
        compose.onNodeWithText("Padrão").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("a" to StageFit.CHOSEN_DEFAULT_ZOOM, saved.last())
    }

    @Test fun `switched off, the badge has no automatic`() {
        show(look = Look(fontSp = 30f, columns = StageColumns.ONE, autoZoom = false))
        pinch()
        compose.onNodeWithText("Automático").assertDoesNotExist()
    }
}
