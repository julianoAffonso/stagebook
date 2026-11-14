package br.app.stagebook

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun `a long title in the setlist keeps the tag whole and on the row`() {
        val title = "Faroeste Caboclo (ao vivo no Rock in Rio com a participação de todos os amigos)"
        val state = PickSongsUiState(setName = "Show", rows = listOf(PickRowUi("1", title, "Legião Urbana", inSet = true, selected = false, songKey = "Em", bpm = 168, capo = 2, hasTrack = true)), selectedCount = 0)
        compose.setContent { StagebookTheme { PickSongsScreen(state, "", {}, {}, {}, {}) } }
        val row = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithText("No setlist", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the tag is inside the row", badge.right <= row.right && badge.left >= row.left)
        assertTrue("the tag has its own room: ${badge.right - badge.left}", (badge.right - badge.left).value >= 40f)
        assertTrue("the tag is after the title", badge.left >= text.right)
    }
}
