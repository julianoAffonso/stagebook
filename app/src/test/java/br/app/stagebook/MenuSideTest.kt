package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import br.app.stagebook.navigation.OpenWhenAsked
import br.app.stagebook.navigation.SideMenuDrawer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The menu slides in from the handle's edge (user, 19/11: with the handle on the right the menu opened on the left,
 * which was odd), and what is inside it and the screen behind keep reading left to right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class MenuSideTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: DrawerState
    private lateinit var scope: CoroutineScope

    private fun open(right: Boolean) {
        compose.setContent {
            StagebookTheme {
                state = rememberDrawerState(DrawerValue.Closed)
                scope = rememberCoroutineScope()
                SideMenuDrawer(state, right, gesturesEnabled = true, sheet = {
                    Row(Modifier.width(300.dp).fillMaxHeight().testTag("sheet")) { Text("primeiro"); Text("segundo") }
                }) { Row(Modifier.fillMaxSize()) { Text("tela-a"); Text("tela-b") } }
            }
        }
        compose.runOnIdle { scope.launch { state.open() } }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun text(t: String) = compose.onNodeWithText(t).fetchSemanticsNode().boundsInRoot

    @Test fun `with the handle on the left the menu opens on the left`() {
        open(right = false)
        assertEquals(0f, bounds("sheet").left, 1f)
    }

    @Test fun `with the handle on the right the menu opens on the right, still reading left to right`() {
        open(right = true)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val sheet = bounds("sheet")
        assertEquals("the menu against the right edge: $sheet", root.right, sheet.right, 1f)
        assertTrue("inside the menu, left to right", text("primeiro").left < text("segundo").left)
        assertTrue("the screen behind, left to right", text("tela-a").left < text("tela-b").left)
    }

    // back from an update installed with the menu open, the menu opens again over the stage (user, 20/11)
    private fun startAsked(ask: Boolean, fixed: Boolean = false): () -> Int {
        var done = 0
        compose.setContent {
            StagebookTheme {
                state = rememberDrawerState(DrawerValue.Closed)
                OpenWhenAsked(state, ask, fixed) { done++ }
                SideMenuDrawer(state, right = false, gesturesEnabled = true, sheet = {
                    Row(Modifier.width(300.dp).fillMaxHeight().testTag("sheet")) { Text("menu") }
                }) { Box(Modifier.fillMaxSize()) { Text("palco") } }
            }
        }
        compose.waitForIdle()
        return { done }
    }

    @Test fun `back from an update with the menu open the menu shows again`() {
        val done = startAsked(ask = true)
        compose.onNodeWithText("menu").assertIsDisplayed()
        assertEquals(1, done())
    }

    @Test fun `a plain start keeps the menu closed`() {
        val done = startAsked(ask = false)
        compose.onNodeWithText("menu").assertIsNotDisplayed()
        assertEquals(0, done())
    }

    @Test fun `pinned, nothing slides open, and the ask is used up`() {
        val done = startAsked(ask = true, fixed = true)
        compose.onNodeWithText("menu").assertIsNotDisplayed()
        assertEquals(1, done())
    }
}
