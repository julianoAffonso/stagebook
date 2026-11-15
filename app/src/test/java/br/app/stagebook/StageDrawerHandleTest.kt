package br.app.stagebook

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
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
}
