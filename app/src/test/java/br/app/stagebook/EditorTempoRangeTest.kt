package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BPM out of range (device test B-07): the field says the range instead of only turning red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class EditorTempoRangeTest {
    @get:Rule val compose = createComposeRule()

    private fun show(tempo: String) = compose.setContent {
        StagebookTheme { SongEditorScreen(SongEditorUiState(loading = false, text = "{tempo: $tempo}\n[G]linha"), {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun `912 shows the range`() {
        show("912")
        compose.onNodeWithText("20–300").assertExists()
    }

    @Test fun `120 shows no warning`() {
        show("120")
        assertEquals(0, compose.onAllNodesWithText("20–300").fetchSemanticsNodes().size)
    }
}
