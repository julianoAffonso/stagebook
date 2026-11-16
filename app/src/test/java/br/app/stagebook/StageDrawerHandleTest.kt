package br.app.stagebook

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.up
import br.app.stagebook.ui.stage.StageDrawerHandle
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stage's side handle (user, 15/11): a touch opens the menu, a hold moves it along the edge, and the place is kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class StageDrawerHandleTest {
    @get:Rule val compose = createComposeRule()

    private var opened = 0
    private var moved: Float? = null

    private fun show(y: Float = 0.5f) = compose.setContent {
        StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, y, { moved = it }, Modifier) }
    }

    @Test fun `a touch opens the menu and moves nothing`() {
        show()
        compose.onNodeWithTag("stage_drawer_handle").performClick()
        assertEquals(1, opened)
        assertEquals(null, moved)
    }

    @Test fun `a hold and a drag down moves it, keeps the place and does not open the menu`() {
        show(0.3f)
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput {
            down(center)
            advanceEventTime(700)   // past the long press
            moveBy(Offset(0f, 160f))
            up()
        }
        compose.waitForIdle()
        assertTrue("moved down from 0.3: $moved", (moved ?: 0f) > 0.3f)
        assertEquals(0, opened)
    }

    @Test fun `with requests waiting the handle carries their number, and with none it has none`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { moved = it }, Modifier, waiting = 2) } }
        compose.onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    @Test fun `no number on the handle when nobody waits`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { moved = it }, Modifier, waiting = 0) } }
        compose.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `on the right edge the handle sits at the right, and a pull to the left opens the menu`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { moved = it }, Modifier, right = true) } }
        val node = compose.onNodeWithTag("stage_drawer_handle")
        val b = node.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("at the right edge: $b in $root", b.right >= root.right - 1f)
        node.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun `on the right edge a pull to the right does not open it`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { moved = it }, Modifier, right = true) } }
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(0, opened)
    }

    @Test fun `on the left edge a pull to the right opens the menu and a pull to the left does not`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { moved = it }, Modifier) } }
        val node = compose.onNodeWithTag("stage_drawer_handle")
        node.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(0, opened)
        node.performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun `a second hold and drag starts from where the first one left the handle`() {
        val moves = mutableListOf<Float>()
        compose.setContent {
            val y = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) }
            StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, y.floatValue, { y.floatValue = it; moves += it }, Modifier) }
        }
        fun dragDown() = compose.onNodeWithTag("stage_drawer_handle").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, 160f)); up() }
        dragDown(); compose.waitForIdle()
        dragDown(); compose.waitForIdle()
        assertEquals(2, moves.size)
        assertTrue("the second went on from the first: $moves", moves[1] > moves[0] + 0.02f)
    }
}
