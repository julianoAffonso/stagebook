package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
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

/** Scroll mode on a phone (device test B-16): holding a line and tapping "Edit this line" opens the editor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageScrollEditPhoneTest {
    @get:Rule val compose = createComposeRule()

    private val long = (1..80).joinToString("\n") { "[G]Linha $it da [D]letra de exemplo" }
    private var page by mutableIntStateOf(0)
    private var cursor = -1

    @Test fun `on a phone in scroll mode, Edit this line opens the editor`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(false, listOf(StageSong("Longa", "", long, false, id = "s1")), look = Look(stageMode = StageMode.SCROLL)),
                    0, page, { _, p -> page = p }, onEdit = { _, c -> cursor = c },
                )
            }
        }
        compose.waitForIdle()
        // one tap scrolls a step, as on the device before the long press
        compose.onRoot().performTouchInput { click(Offset(width * 0.8f, height * 0.6f)) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { longClick(Offset(width * 0.3f, height * 0.5f)) }
        compose.waitForIdle()
        val button = compose.onNodeWithText("Editar esta linha").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(button.center) }
        compose.waitForIdle()
        assertTrue("abriu o editor (cursor=$cursor, page=$page)", cursor >= 0)
    }
}
