package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A setlist created while the list is at the top shows at the top, not hidden above it (device test B-09). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistsNewOnTopTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a new setlist at the top of the list is on screen`() {
        val old = (1..15).map { SetlistRowUi("s$it", "Setlist $it", it) }
        var state by mutableStateOf(SetlistsUiState(false, old))
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("Setlist 1").assertIsDisplayed()
        state = SetlistsUiState(false, listOf(SetlistRowUi("novo", "01/11/2026 17:40", 0)) + old)
        compose.waitForIdle()
        compose.onNodeWithText("01/11/2026 17:40").assertIsDisplayed()
    }

    @Test fun `a search with no match says so and the header keeps the total`() {
        compose.setContent { StagebookTheme { SetlistsScreen(SetlistsUiState(false, emptyList(), total = 3), {}, {}, {}, {}, query = "xyz") } }
        compose.onNodeWithText("Nenhum setlist encontrado").assertIsDisplayed()
        compose.onNodeWithText("3 setlists · 0 no filtro").assertIsDisplayed()
    }

    @Test fun `the order sits beside the search and changes from its menu`() {
        var abc = false
        val sets = listOf(SetlistRowUi("1", "Um", 1), SetlistRowUi("2", "Dois", 2))
        compose.setContent { StagebookTheme { SetlistsScreen(SetlistsUiState(false, sets), {}, {}, {}, {}, onSortAbc = { abc = it }) } }
        compose.onNodeWithText("Recentes").performClick()
        compose.onNodeWithText("A a Z").performClick()
        assertTrue(abc)
    }

    @Test fun `one setlist has nothing to order`() {
        compose.setContent { StagebookTheme { SetlistsScreen(SetlistsUiState(false, listOf(SetlistRowUi("1", "Um", 1))), {}, {}, {}, {}) } }
        compose.onNodeWithText("Ordem").assertDoesNotExist()
    }
}
