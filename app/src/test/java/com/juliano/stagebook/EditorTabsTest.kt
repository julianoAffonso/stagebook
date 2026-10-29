package com.juliano.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juliano.stagebook.ui.editor.EditorError
import com.juliano.stagebook.ui.editor.EditorMode
import com.juliano.stagebook.ui.editor.SongEditorScreen
import com.juliano.stagebook.ui.editor.SongEditorUiState
import com.juliano.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editor em duas abas (T27): a letra com a tela toda e os dados da música à parte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class EditorTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `música nova abre nos Dados - existente abre na Letra`() {
        var state by mutableStateOf(SongEditorUiState(false, EditorMode.NEW, "", "", "", ""))
        compose.setContent { StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertExists()
        compose.onNodeWithText("Letra").performClick()
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertDoesNotExist()
    }

    @Test fun `música existente abre na Letra e o título aparece no topo`() {
        compose.setContent { StagebookTheme { SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "Richard Marx", "", "linha"), {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertDoesNotExist()
        compose.onNodeWithText("Hazard · Richard Marx").assertExists()
    }

    @Test fun `salvar sem título leva para os Dados com o erro`() {
        var state by mutableStateOf(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "", "", "linha"))
        compose.setContent { StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        state = state.copy(title = "", error = EditorError.TITLE_EMPTY)
        compose.waitForIdle()
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertExists()
    }
}
