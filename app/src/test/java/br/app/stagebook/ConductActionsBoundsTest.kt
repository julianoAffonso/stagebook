package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The strip's actions can be found by a screen reader and by automation (device test v0.1.1123: uiautomator gave
 * bounds [0,0][0,0] for "End", "Leave" and "Ask" on the tablet): each has real bounds, is a button and has a touch
 * height of at least 48 dp, in every state of the strip, on a narrow phone with large text and on a wide tablet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h780dp-port-420dpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConductActionsBoundsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(ui: ConductUi, waiting: FollowWait? = null, asking: Boolean = false, menus: Int = 0) {
        val gate = FollowGate().also { it.menus = menus }
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    // as in the app: the strip below the screens, in the column the keyboard lifts, shown by an animation
                    br.app.stagebook.navigation.BandStripLayout(screens = {}, strip = {
                        androidx.compose.animation.AnimatedVisibility(true) {
                            ConductBar(ui, {}, {}, {}, {}, asking = asking, waiting = waiting, onGo = {})
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun button(label: String): SemanticsNodeInteraction =
        compose.onNode((hasText(label) or hasContentDescription(label)) and hasClickAction())

    private fun check(label: String) {
        val node = button(label)
        node.assertHasClickAction()
        val bounds = node.getBoundsInRoot()
        assertTrue("$label has real bounds: $bounds", (bounds.right - bounds.left) > 0.dp && (bounds.bottom - bounds.top) > 0.dp)
        // the touch target (what a finger and a screen reader's focus use) is at least 48 dp, even where the drawn
        // button is 40 dp tall
        val touch = node.fetchSemanticsNode().touchBoundsInRoot
        val density = compose.density.density
        assertTrue("$label touch height ${touch.height / density} dp", touch.height / density >= 47.5f)
        assertTrue("$label touch width ${touch.width / density} dp", touch.width / density >= 47.5f)
        assertTrue("$label has area: $touch", !touch.isEmpty)
        val role = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
        assertEquals("$label is a button", Role.Button, role)
        // inside the screen
        val root = compose.onRoot().getBoundsInRoot()
        assertTrue("$label inside the screen: $bounds in $root", bounds.left >= 0.dp && bounds.right <= root.right && bounds.bottom <= root.bottom)
    }

    private val lead = ConductUi.Conducting("Show de sábado", 0, emptyList(), emptyList())
    private val follow = ConductUi.Following("Voz", "Primeira", false, false, emptyList(), emptyList())
    private val missing = ConductUi.Following("Voz", "Uma música com nome bem comprido", true, false, emptyList(), listOf("A"))

    private fun large() = RuntimeEnvironment.setFontScale(1.15f)

    private fun phone() {
        large()
    }

    @Test fun `phone conducting`() { phone(); show(lead); check("Encerrar"); check("Procurar a banda") }
    @Test fun `phone following`() { phone(); show(follow); check("Sair") }
    @Test fun `phone asking`() { phone(); show(missing); check("Pedir"); check("Sair") }
    @Test fun `phone asking busy`() { phone(); show(missing, asking = true); check("Sair") }
    @Test fun `phone waiting`() { phone(); show(follow, waiting = FollowWait.MENU); check("Ir"); check("Sair") }
    @Test fun `phone waiting with a window open`() { phone(); show(follow, waiting = FollowWait.MENU, menus = 1); check("Ir"); check("Sair") }
    @Test fun `phone left`() { phone(); show(ConductUi.Left("Voz")); check("Acompanhar") }

    @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    @Test fun `tablet conducting`() { show(lead); check("Encerrar"); check("Procurar a banda") }
    @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    @Test fun `tablet following`() { show(follow); check("Sair") }
    @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    @Test fun `tablet asking`() { show(missing); check("Pedir"); check("Sair") }
    @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    @Test fun `tablet waiting`() { show(follow, waiting = FollowWait.MENU); check("Ir"); check("Sair") }
    @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    @Test fun `tablet waiting with a window open`() { show(follow, waiting = FollowWait.MENU, menus = 1); check("Ir"); check("Sair") }
    @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    @Test fun `tablet left`() { show(ConductUi.Left("Voz")); check("Acompanhar") }
}
