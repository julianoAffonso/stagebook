package br.app.stagebook

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.setlists.PickRowUi
import br.app.stagebook.ui.setlists.PickSongsScreen
import br.app.stagebook.ui.setlists.PickSongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A long title gives way to the "In setlist" tag, with real text widths: the tag stays whole inside the row, beside the title. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class PickSongsBadgeWidthTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a song already in the setlist has no phrase, and a long title stays inside its row`() {
        val title = "Faroeste Caboclo (ao vivo no Rock in Rio com a participação de todos os amigos)"
        val state = PickSongsUiState(setName = "Show", rows = listOf(PickRowUi("1", title, "Legião Urbana", inSet = true, selected = false, songKey = "Em", bpm = 168, capo = 2, hasTrack = true)), selectedCount = 0)
        compose.setContent { StagebookTheme { PickSongsScreen(state, "", {}, {}, {}, {}) } }
        val row = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onAllNodesWithText("No setlist", useUnmergedTree = true).assertCountEquals(0)   // the ticked box and the faded row say it
        assertTrue("the title is inside the row", text.right <= row.right && text.left >= row.left)
    }

    @Test fun `the chosen songs carry the place they will take, in the order of the taps`() {
        val rows = listOf(
            PickRowUi("1", "Primeira", "A", inSet = false, selected = true, order = 2),
            PickRowUi("2", "Segunda", "B", inSet = false, selected = true, order = 1),
            PickRowUi("3", "Terceira", "C", inSet = false, selected = false),
        )
        compose.setContent { StagebookTheme { PickSongsScreen(PickSongsUiState(setName = "Show", rows = rows, selectedCount = 2), "", {}, {}, {}, {}) } }
        compose.onNodeWithText("1", useUnmergedTree = true).assertExists()
        // "2": the row's place and the check's count (nothing in the setlist yet, two checked)
        compose.onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("3", useUnmergedTree = true).assertCountEquals(0)
    }
}
