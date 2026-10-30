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

/** Editor (T27): a letra com a tela toda e os dados da música recolhidos numa linha no topo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class EditorTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `música nova abre com os dados à vista - tocar no resumo recolhe`() {
        val state = SongEditorUiState(false, EditorMode.NEW, "", "", "", "")
        compose.setContent { StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertExists()
        // aberto, o resumo não aparece (repetiria o título): o ▲ do campo Título recolhe
        compose.onNodeWithText("Música sem título").assertDoesNotExist()
        compose.onNodeWithContentDescription("Recolher os dados da música").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Música sem título").assertExists()
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertDoesNotExist()
    }

    @Test fun `música existente abre recolhida com o resumo - tocar abre os campos`() {
        compose.setContent { StagebookTheme { SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "Richard Marx", "", "{key: G}\n{capo: 2}\n[G]linha"), {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertDoesNotExist()
        // o campo da letra não mostra {key}/{capo}: estão no resumo
        compose.onNodeWithText("{key: G}", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Hazard · Richard Marx · G · capo 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertExists()
    }

    @Test fun `salvar sem título abre os dados com o erro`() {
        var state by mutableStateOf(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "", "", "linha"))
        compose.setContent { StagebookTheme { SongEditorScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        state = state.copy(title = "", error = EditorError.TITLE_EMPTY)
        compose.waitForIdle()
        compose.onNodeWithText("Título (é o nome usado no setlist)").assertExists()
    }

    @Test fun `aberto - dá para digitar o nome da música no campo Título`() {
        var typed = ""
        compose.setContent { StagebookTheme { SongEditorScreen(SongEditorUiState(false, EditorMode.NEW, "", "", "", ""), { typed = it }, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Título (é o nome usado no setlist)").performTextInput("Hazard")
        org.junit.Assert.assertEquals("Hazard", typed)
    }

    @Test fun `tom igual ao capo - mostra o tom e + transpõe meio tom`() {
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
