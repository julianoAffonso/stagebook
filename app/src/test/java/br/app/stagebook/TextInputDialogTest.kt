package br.app.stagebook

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import br.app.stagebook.ui.components.TextInputDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renaming (device test B-10): the old name comes selected, so typing replaces it instead of going in front of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class TextInputDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `typing replaces the current name`() {
        var named = ""
        compose.setContent { StagebookTheme { TextInputDialog("Renomear", "01/11/2026 17:40", "Nome", { named = it }, {}) } }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("Show")
        compose.onNodeWithText("OK").performClick()
        assertEquals("Show", named)
    }
}
