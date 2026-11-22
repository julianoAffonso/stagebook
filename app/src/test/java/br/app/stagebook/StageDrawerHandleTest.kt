package br.app.stagebook

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, y, { y, _ -> moved = y }, Modifier) }
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
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { y, _ -> moved = y }, Modifier, waiting = 2) } }
        compose.onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    @Test fun `no number on the handle when nobody waits`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { y, _ -> moved = y }, Modifier, waiting = 0) } }
        compose.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `on the right edge the handle sits at the right, and a pull to the left opens the menu`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { y, _ -> moved = y }, Modifier, right = true) } }
        val node = compose.onNodeWithTag("stage_drawer_handle")
        val b = node.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("at the right edge: $b in $root", b.right >= root.right - 1f)
        node.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun `on the right edge a pull to the right does not open it`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { y, _ -> moved = y }, Modifier, right = true) } }
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(0, opened)
    }

    @Test fun `on the left edge a pull to the right opens the menu and a pull to the left does not`() {
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { y, _ -> moved = y }, Modifier) } }
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
            StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, y.floatValue, { v, _ -> y.floatValue = v; moves += v }, Modifier) }
        }
        fun dragDown() = compose.onNodeWithTag("stage_drawer_handle").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, 160f)); up() }
        dragDown(); compose.waitForIdle()
        dragDown(); compose.waitForIdle()
        assertEquals(2, moves.size)
        assertTrue("the second went on from the first: $moves", moves[1] > moves[0] + 0.02f)
    }

    // user, 19/11: no switch in Settings any more; the handle held and taken past the middle goes to that edge
    private var side: Boolean? = null
    private fun showSide(right: Boolean) = compose.setContent {
        StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { _, r -> side = r }, Modifier.fillMaxSize(), right = right) }
    }
    private fun holdAndDrag(dx: Float) = compose.onNodeWithTag("stage_drawer_handle").performTouchInput {
        down(center); advanceEventTime(700)
        repeat(10) { moveBy(Offset(dx / 10f, 0f)) }
        up()
    }

    @Test fun `held and taken past the middle, the handle goes to the right edge`() {
        showSide(right = false)
        holdAndDrag(compose.onRoot().fetchSemanticsNode().boundsInRoot.width * 0.8f)
        compose.waitForIdle()
        assertEquals(true, side)
        assertEquals("moving it is not opening the menu", 0, opened)
    }

    @Test fun `on the right edge, held and taken past the middle, it goes back to the left`() {
        showSide(right = true)
        holdAndDrag(-compose.onRoot().fetchSemanticsNode().boundsInRoot.width * 0.8f)
        compose.waitForIdle()
        assertEquals(false, side)
        assertEquals(0, opened)
    }

    @Test fun `held and moved a little sideways, the handle stays on its edge`() {
        showSide(right = false)
        holdAndDrag(compose.onRoot().fetchSemanticsNode().boundsInRoot.width * 0.3f)
        compose.waitForIdle()
        assertEquals(false, side)
    }

    @Test fun `while held past the middle the handle already shows on the other edge`() {
        showSide(right = false)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput { down(center); advanceEventTime(700); repeat(10) { moveBy(Offset(root.width * 0.08f, 0f)) } }
        compose.waitForIdle()
        val b = compose.onNodeWithTag("stage_drawer_handle").fetchSemanticsNode().boundsInRoot
        assertTrue("at the right edge while held: $b", b.right >= root.right - 1f)
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput { up() }
    }

    // user, 19/11: the handle "didn't obey, then after a while it worked": a drag up or down with no hold first moved
    // nothing; now it takes the handle along at once
    @Test fun `a drag up or down with no hold first moves the handle and keeps its side`() {
        var at: Pair<Float, Boolean>? = null
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.3f, { y, r -> at = y to r }, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput { down(center); repeat(8) { moveBy(Offset(0f, 40f)) }; up() }
        compose.waitForIdle()
        assertTrue("moved down from 0.3: $at", (at?.first ?: 0f) > 0.3f)
        assertEquals(false, at?.second)
        assertEquals(0, opened)
    }

    @Test fun `a quick pull sideways away from the edge still opens the menu and moves nothing`() {
        var at: Pair<Float, Boolean>? = null
        compose.setContent { StagebookTheme { StageDrawerHandle({ opened++ }, Color.White, Color.Black, 0.5f, { y, r -> at = y to r }, Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("stage_drawer_handle").performTouchInput { down(center); repeat(8) { moveBy(Offset(30f, 2f)) }; up() }
        compose.waitForIdle()
        assertEquals(1, opened)
        assertEquals(null, at)
    }
}
