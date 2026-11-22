package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The setlist list in the library drawer (user, 15/11): the + and the checklist at the bottom, and the PDF note when sharing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class SetlistsBottomBarTest {
    @get:Rule val compose = createComposeRule()

    private val state = SetlistsUiState(false, listOf(SetlistRowUi("1", "Show de sábado", 12, "2026-11-14")), total = 1)

    @Test fun `the top has no buttons any more and the plus is at the bottom`() {
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onAllNodesWithText("Importar").assertCountEquals(0)
        compose.onNodeWithContentDescription("Nova e importar").assertIsDisplayed()
    }

    @Test fun `the plus offers a new setlist and the ways to bring one in`() {
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Nova e importar").performClick()
        compose.onNodeWithText("Novo setlist").assertIsDisplayed()
        compose.onNodeWithText("Ler QR", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test fun `the checklist turns the boxes on with nothing chosen`() {
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Selecionar setlists").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Selecionar setlists").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Nova e importar").assertIsDisplayed()
    }

    @Test fun `sharing setlists with PDFs too big for the internet says why`() {
        var asked = emptyList<String>()
        compose.setContent {
            StagebookTheme {
                SetlistsScreen(state, {}, {}, {}, {}, online = true, songsIn = { 12 }, pdfSizesIn = { ids -> asked = ids; listOf(9_000_000L) })
            }
        }
        compose.onNodeWithText("Show de sábado").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Compartilhar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("pela internet vão até 30 MB", substring = true).assertIsDisplayed()
        assertEquals(listOf("1"), asked)
    }
}
