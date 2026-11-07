package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.ShowTimer
import br.app.stagebook.ui.stage.ShowLengthWindow
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The show's length window over the stage clock (user, 07/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ShowLengthWindowTest {
    @get:Rule val compose = createComposeRule()

    // S26, 07/11: − and + moved sideways as the length went from "1h" to "1h30"
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths: the default mode measures text too narrow
    fun `the minus and plus buttons stay put as the length changes`() {
        var length by mutableIntStateOf(60)
        compose.setContent { StagebookTheme { Box { ShowLengthWindow(length, { up -> length = ShowTimer.step(length, up) }, {}, {}) } } }
        val minus = compose.onNodeWithContentDescription("Meia hora a menos")
        val plus = compose.onNodeWithContentDescription("Meia hora a mais")
        val before = minus.fetchSemanticsNode().boundsInWindow.left to plus.fetchSemanticsNode().boundsInWindow.left
        plus.performClick()
        compose.waitForIdle()
        assertEquals(90, length)
        assertEquals(before, minus.fetchSemanticsNode().boundsInWindow.left to plus.fetchSemanticsNode().boundsInWindow.left)
    }
}
