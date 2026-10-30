package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import br.app.stagebook.ui.editor.EditorError
import br.app.stagebook.ui.editor.EditorMode
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editor (T27): the lyrics take the whole screen and the song details are collapsed on one line at the top. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class EditorTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `new song opens with the details shown - tapping the summary collapses`() {
        val state = SongEditorUiState(false, EditorMode.NEW, "", "", "", "")
        compose.setContent { StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título").assertExists()
        // when open, the summary doesn't show (it would repeat the title): the ▲ in the Title field collapses
        compose.onNodeWithText("Música sem título").assertDoesNotExist()
        compose.onNodeWithContentDescription("Recolher os dados da música").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Música sem título").assertExists()
        compose.onNodeWithText("Título").assertDoesNotExist()
    }

    @Test fun `existing song opens collapsed with the summary - tapping opens the fields`() {
        compose.setContent { StagebookTheme { SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "Richard Marx", "", "{key: G}\n{capo: 2}\n[G]linha"), {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título").assertDoesNotExist()
        // the lyrics field doesn't show {key}/{capo}: they're in the summary
        compose.onNodeWithText("{key: G}", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Hazard · Richard Marx · G · capo 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Título").assertExists()
    }

    @Test fun `saving without a title opens the details with the error`() {
        var state by mutableStateOf(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "", "", "linha"))
        compose.setContent { StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        state = state.copy(title = "", error = EditorError.TITLE_EMPTY)
        compose.waitForIdle()
        compose.onNodeWithText("Título").assertExists()
    }

    @Test fun `open - you can type the song name in the Title field`() {
        var typed = ""
        compose.setContent { StagebookTheme { SongEditorScreen(SongEditorUiState(false, EditorMode.NEW, "", "", "", ""), { typed = it }, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título").performTextInput("Hazard")
        org.junit.Assert.assertEquals("Hazard", typed)
    }

    @Test fun `key works like capo - shows the key and + transposes a half step`() {
        var moved = 0
        compose.setContent {
            StagebookTheme {
                SongEditorScreen(SongEditorUiState(false, EditorMode.NEW, "", "", "", "{key: G}\n[G]um [C]dois [D]três"), {}, {}, {}, {}, {}, {}, {}, onTranspose = { moved += it })
            }
        }
        compose.onNodeWithText("G").assertExists()
        compose.onNodeWithContentDescription("Subir meio tom").performClick()
        org.junit.Assert.assertEquals(1, moved)
    }
}
