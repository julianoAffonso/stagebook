package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A page that turns slides to the screen's edge (user, 16/11: it slid under a band of 24 dp on each side, which read as a
 * border). Every line starts at the margin, so a page pulled to the left paints inside the left margin while it goes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class StageTurnToEdgeTest {
    @get:Rule val compose = createComposeRule()

    /** How many pixels of the left margin's strip are not the background. */
    private fun Bitmap.inkInLeftMargin(marginPx: Int): Int {
        val bg = getPixel(2, height / 2)
        var ink = 0
        for (y in height / 5 until height * 4 / 5) for (x in 6 until marginPx - 6) if (getPixel(x, y) != bg) ink++
        return ink
    }

    @Test fun `a page dragged toward the edge paints in the side margin, which used to cut it`() {
        val text = (1..40).joinToString("\n\n") { st -> (1..4).joinToString("\n") { "estrofe $st linha $it" } }
        val state = StageUiState(loading = false, songs = listOf(StageSong("Longa", "", text, false)), look = Look(fontSp = 34f))
        var page by mutableIntStateOf(0)
        compose.setContent { StagebookTheme { StageScreen(state, 0, page, { _, p -> page = p }) } }
        compose.waitForIdle()
        val margin = 24 * 3   // Spacing.l at xxhdpi
        val still = compose.onRoot().captureToImage().asAndroidBitmap().inkInLeftMargin(margin)
        // the finger pulls the page to the left and holds it there: every line starts at the margin, so it moves into it
        compose.onRoot().performTouchInput { down(center); moveBy(Offset(-90f, 0f)); moveBy(Offset(-90f, 0f)) }
        compose.waitForIdle()
        val moving = compose.onRoot().captureToImage().asAndroidBitmap().inkInLeftMargin(margin)
        compose.onRoot().performTouchInput { up() }
        assertTrue("the margin is empty while the page is still: $still", still == 0)
        assertTrue("the page is drawn in the margin while it is pulled: $moving", moving > 0)
    }
}
