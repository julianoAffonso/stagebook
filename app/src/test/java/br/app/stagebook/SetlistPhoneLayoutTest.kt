package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The setlist on a phone with large system text (seen on a 1080 px phone at 1.15, 13/11): the third button under the
 * title broke its word in two ("Compartilha / r"), and a long title took a second line and made its row taller.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths: the bug is text that doesn't fit
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h780dp-port-420dpi", application = android.app.Application::class)
class SetlistPhoneLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val state = SetlistDetailUiState(
        loading = false,
        name = "Show",
        items = listOf(
            SetItemUi("a", "a", "Curta", "Banda", false, songKey = "G"),
            SetItemUi("b", "b", "Um título bem comprido que não cabe numa linha só do celular", "Banda", false, songKey = "Dm"),
            SetItemUi("c", "c", "Outra", "Banda", false),
        ),
        breaks = listOf(2),
    )

    private fun height(text: String) =
        compose.onNodeWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height

    @Test fun `with large text a long title stays on one line`() {
        RuntimeEnvironment.setFontScale(1.15f)
        compose.setContent { StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, onRemove = { _, _ -> }, onDeleteSetlist = {}) } }
        compose.waitForIdle()
        assertEquals(height("Curta"), height("Um título bem comprido"), 0.5f)
    }

    @Test fun `editing, the setlist's name stays on one line beside the four icons, at large text`() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.setContent { StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, onRemove = { _, _ -> }, onDeleteSetlist = {}, startEditing = true) } }
        compose.waitForIdle()
        // "Edit setlist" no longer sits over the name: nothing to wrap
        compose.onAllNodesWithText("Editar setlist").assertCountEquals(0)
        compose.onNodeWithText("Show", useUnmergedTree = true).assertIsDisplayed()
    }
}
