package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The song list in the library drawer (user, 15/11): the + and the checklist at the bottom, and a sheet for a PDF. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class SongsBottomBarTest {
    @get:Rule val compose = createComposeRule()

    private val songs = SongsUiState(
        loading = false,
        songs = listOf(SongRowUi("1", "Wonderwall", "Oasis", songKey = "G", bpm = 87), SongRowUi("2", "Partitura", "Ana", songKey = "C", hasPdf = true)),
        total = 2,
    )

    private fun show(new: () -> Unit = {}, sites: () -> Unit = {}, scan: () -> Unit = {}) =
        compose.setContent { StagebookTheme { SongsScreen(songs, "", {}, new, sites, {}, onScan = scan) } }

    @Test fun `the top has no buttons any more and the plus is at the bottom`() {
        show()
        compose.onAllNodesWithText("Nova música").assertCountEquals(0)
        compose.onAllNodesWithText("Importar").assertCountEquals(0)
        compose.onNodeWithContentDescription("Nova e importar").assertIsDisplayed()
    }

    @Test fun `the plus offers a new song first and then the ways to bring songs in`() {
        var made = 0
        var scanned = 0
        var sites = 0
        show(new = { made++ }, sites = { sites++ }, scan = { scanned++ })
        compose.onNodeWithContentDescription("Nova e importar").performClick()
        compose.onNodeWithText("Nova música").assertIsDisplayed()
        compose.onNodeWithText("Ler QR", substring = true, ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Nova música").performClick()
        assertEquals(1, made)
        compose.onNodeWithContentDescription("Nova e importar").performClick()
        compose.onNodeWithText("Ler QR", substring = true, ignoreCase = true).performClick()
        assertEquals(1, scanned)
        assertEquals(0, sites)
    }

    @Test fun `the checklist turns the selection on with nothing chosen and turns it off`() {
        show()
        compose.onNodeWithContentDescription("Selecionar músicas").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wonderwall").performClick()   // chooses it instead of opening it
        compose.waitForIdle()
        compose.onNodeWithText("1", substring = true).assertExists()
        compose.onNodeWithContentDescription("Selecionar músicas").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Nova e importar").assertIsDisplayed()
    }

    @Test fun `a song that is a PDF shows a sheet in its row`() {
        show()
        compose.onNodeWithContentDescription("PDF").assertIsDisplayed()
    }

    @Test fun `the plus offers Import PDFs in the song list, and it runs the import`() {
        var pdfs = 0
        compose.setContent { StagebookTheme { SongsScreen(songs, "", {}, {}, {}, {}, onImportPdfs = { pdfs++ }) } }
        compose.onNodeWithContentDescription("Nova e importar").performClick()
        compose.onNodeWithText("Importar PDFs").performClick()
        assertEquals(1, pdfs)
    }

    @Test fun `without a way to import PDFs the menu has no such item`() {
        show()
        compose.onNodeWithContentDescription("Nova e importar").performClick()
        compose.onAllNodesWithText("Importar PDFs").assertCountEquals(0)
    }
}
