package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import org.robolectric.annotation.Config

/**
 * The strip and Android's navigation bar (device test v0.1.1134, Chuwi: a window opened, the bar came back, the strip's
 * button was behind it and a tap went to "Recents"). The app hides the bar, so the insets say 0 until it shows; the
 * insets are dispatched by hand, with the bar hidden and its size known.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StripAboveNavigationBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val gate = FollowGate()

    private fun show(barPx: Int) {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    Box(Modifier.fillMaxSize()) {
                        ConductBar(
                            ConductUi.Following("Voz", "Primeira", false, false, emptyList(), emptyList()), {}, {}, {}, {},
                            Modifier.align(Alignment.BottomCenter), waiting = FollowWait.MENU, onGo = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        // the bar is hidden: no inset now, and the size it has when it shows
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, barPx))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), false)
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
    }

    private fun rootBottomDp() = compose.activity.resources.displayMetrics.let { it.heightPixels / it.density }

    @Test fun `with a window open the strip's button is above the bar when it shows`() {
        val bar = 72
        show(barPx = bar)
        compose.runOnIdle { gate.menus++ }
        compose.waitForIdle()
        val go = compose.onNodeWithText("Ir").getBoundsInRoot()
        val barDp = bar / compose.activity.resources.displayMetrics.density
        assertTrue("button bottom ${go.bottom.value} must be above ${rootBottomDp() - barDp}", go.bottom.value <= rootBottomDp() - barDp + 0.5f)
    }

    @Test fun `with no window open the strip stays where it was, at the bottom`() {
        show(barPx = 72)
        val go = compose.onNodeWithText("Ir").getBoundsInRoot()
        assertTrue(go.bottom.value > rootBottomDp() - 72 / compose.activity.resources.displayMetrics.density)
    }

    @Test fun `closing the window puts it back`() {
        show(barPx = 72)
        compose.runOnIdle { gate.menus++ }
        compose.waitForIdle()
        val lifted = compose.onNodeWithText("Ir").getBoundsInRoot().bottom.value
        compose.runOnIdle { gate.menus-- }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithText("Ir").getBoundsInRoot().bottom.value > lifted)
    }
}
