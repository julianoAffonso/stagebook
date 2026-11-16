package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.stage.editCursor
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The line held on stage is resolved to the song's line WHEN IT IS HELD: if the stage lays the text out again before the
 * "edit this line" button is tapped (the list the index points into changes, as the SideEffect that swaps it does), the
 * editor still opens on the held line (cursor that landed on another line now and then, 14/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi", application = android.app.Application::class)
class StageHeldLineTest {
    @get:Rule val compose = createComposeRule()

    private val text = (1..40).joinToString("\n") { "[G]Linha $it da [D]letra de exemplo que e bem comprida [C]para quebrar em varias partes quando a letra [Am]ficar maior na tela do palco [F]e mais um pedaco" }
    private var cursor = -2
    private var zoom by mutableStateOf(0.6f)

    @Test fun `the cursor of the line source is the start of that line`() {
        assertEquals(0, editCursor("a\nb\nc", 0)); assertEquals(2, editCursor("a\nb\nc", 1)); assertEquals(4, editCursor("a\nb\nc", 2))
        assertEquals(-1, editCursor("a\nb", null)); assertEquals(-1, editCursor("a\nb", -1))
    }

    @Test fun `the layout changing between the hold and the tap does not move the held line`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(StageUiState(false, listOf(StageSong("Longa", "", text, false, id = "s1", zoom = zoom)), look = Look(columns = br.app.stagebook.data.model.StageColumns.ONE)), 0, 0, { _, _ -> }, onEdit = { _, c -> cursor = c })
            }
        }
        compose.waitForIdle()
        // reference: hold and tap at once
        compose.onRoot().performTouchInput { longClick(center) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Editar esta linha").performClick()
        compose.waitForIdle()
        val expected = cursor
        // hold, then the text is laid out again (bigger letters: more pieces per line), then tap
        compose.onRoot().performTouchInput { longClick(center) }
        compose.waitForIdle()
        zoom = 1.8f
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Editar esta linha").performClick()
        compose.waitForIdle()
        assertNotEquals(-2, expected)
        assertEquals(expected, cursor)
    }
}
