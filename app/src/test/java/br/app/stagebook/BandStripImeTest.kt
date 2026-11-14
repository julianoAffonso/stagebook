package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import br.app.stagebook.navigation.BandStripLayout
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard and the band's strip (device test v0.1.1134: with the keyboard open in the song editor the strip stayed
 * behind it and a tap on "Go" typed a "."). The window insets are dispatched by hand: Robolectric has no keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class BandStripImeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun layout() {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            StagebookTheme {
                BandStripLayout(
                    // a screen with a text field: it lifts itself above the keyboard, as the song editor does
                    screens = { Box(Modifier.fillMaxSize().imePadding().testTag("screen")) },
                    strip = { Box(Modifier.fillMaxWidth().height(60.dp).testTag("strip")) },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun keyboard(heightPx: Int) {
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
    }

    @Test fun `with the keyboard up the strip sits right above it, not behind it`() {
        layout()
        val window = compose.activity.resources.displayMetrics
        val density = window.density
        val rootHeight = compose.onNodeWithTag("strip").getBoundsInRoot().bottom
        assertEquals("the strip starts at the bottom of the window", window.heightPixels / density, rootHeight.value, 1f)
        keyboard(heightPx = (300 * density).toInt())
        val strip = compose.onNodeWithTag("strip").getBoundsInRoot()
        assertEquals("above the keyboard", (window.heightPixels / density) - 300f, strip.bottom.value, 1f)
    }

    @Test fun `the screen gives the strip its height once, not twice`() {
        layout()
        val density = compose.activity.resources.displayMetrics.density
        keyboard(heightPx = (300 * density).toInt())
        val screen = compose.onNodeWithTag("screen").getBoundsInRoot()
        val strip = compose.onNodeWithTag("strip").getBoundsInRoot()
        assertEquals("the screen ends where the strip starts", strip.top.value, screen.bottom.value, 1f)
        assertTrue(screen.bottom.value - screen.top.value > 300f)
    }
}
