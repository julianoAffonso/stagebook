package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Holding a song's row in a setlist opens its note (user, 24/11); the note shows under the title. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SetlistNoteScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(items: List<SetItemUi>, onNote: (String, String) -> Unit = { _, _ -> }) {
        val state = SetlistDetailUiState(loading = false, name = "Show", items = items)
        compose.setContent { StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, onRemove = { _, _ -> }, onDeleteSetlist = {}, onNote = onNote) } }
    }

    @Test fun `the note shows under the song`() {
        show(listOf(SetItemUi("a", "a", "Hazard", "", false, note = "entra a capela")))
        compose.onNodeWithText("entra a capela").assertExists()
    }

    @Test fun `holding the row asks for the note and saves what was typed`() {
        var saved: Pair<String, String>? = null
        show(listOf(SetItemUi("a", "a", "Hazard", "", false))) { id, text -> saved = id to text }
        compose.onNodeWithText("Hazard").performTouchInput { longClick() }
        compose.onNodeWithText("Nota de Hazard").assertExists()
        compose.onNodeWithText("O que lembrar neste setlist (ex.: entra a capela)").performTextInput("tom abaixo")
        compose.onNodeWithText("OK").performClick()
        assertEquals("a" to "tom abaixo", saved)
    }
}
