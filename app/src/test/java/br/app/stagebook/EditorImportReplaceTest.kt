package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.editor.EditorMode
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An import over a song already in the library used to save over it without a word (user, 15/11): now the note says so
 * and Save asks before replacing the text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w400dp-h800dp", application = android.app.Application::class)
class EditorImportReplaceTest {
    @get:Rule val compose = createComposeRule()

    private var saved = 0

    private fun open(mode: EditorMode) {
        compose.setContent {
            StagebookTheme {
                SongEditorScreen(
                    SongEditorUiState(loading = false, title = "Hazard", text = "letra", mode = mode, unsaved = true),
                    {}, {}, {}, {}, onSave = { saved++ }, onCancel = {}, onDelete = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun `saving an import over a song already there asks first, and only Replace saves`() {
        open(EditorMode.IMPORTED_UPDATE)
        compose.onNodeWithText("Esta música já está na sua biblioteca", substring = true).assertExists()
        compose.onNodeWithContentDescription("Salvar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Trocar o texto de", substring = true).assertExists()
        assertEquals(0, saved)
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertEquals(0, saved)
        compose.onNodeWithContentDescription("Salvar").performClick()
        compose.onNodeWithText("Trocar").performClick()
        compose.waitForIdle()
        assertEquals(1, saved)
    }

    @Test fun `a new song or an import of a new one saves at once`() {
        open(EditorMode.IMPORTED_NEW)
        compose.onNodeWithContentDescription("Salvar").performClick()
        compose.waitForIdle()
        assertEquals(1, saved)
    }
}
