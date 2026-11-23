package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.setlists.setlistOrder
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Setlists list with the show day: month headings and the day on each row (a tall screen, so the whole list is composed). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w400dp-h1000dp", application = android.app.Application::class)
class SetlistDateListTest {
    @get:Rule val compose = createComposeRule()

    private fun row(id: String, date: String) = SetlistRowUi(id, id, 0, date)

    @Test fun `the list shows a heading for each month and the day on the row`() {
        val state = SetlistsUiState(false, setlistOrder(listOf(row("Show de sábado", "2026-11-15"), row("Natal", "2025-12-24"), row("Antigo", ""))), total = 3)
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("novembro de 2026", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("dezembro de 2025", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Sem data").assertIsDisplayed()
        compose.onNodeWithText("15 de nov. de 2026", substring = true, ignoreCase = true).assertIsDisplayed()
    }
}
