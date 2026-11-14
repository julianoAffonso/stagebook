package br.app.stagebook

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.setlists.PickRowUi
import br.app.stagebook.ui.setlists.PickSongsScreen
import br.app.stagebook.ui.setlists.PickSongsUiState
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.Spacing
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The song list and "choose songs" rows keep the setlist row's parameters (user, 14/11): the same small gap between what
 * leads the row and the title, and no number (the order is alphabetical).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class SongRowsAlignedTest {
    @get:Rule val compose = createComposeRule()

    /** From the row's start edge to the title: the row's padding, the box and the gap. */
    private fun titleOffset(title: String): Dp {
        val row = compose.onNodeWithText(title).getUnclippedBoundsInRoot()   // merged: the row
        val text = compose.onNodeWithText(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return text.left - row.left
    }

    @Test fun `the song list's selection box sits the setlist's small gap from the title`() {
        val state = SongsUiState(loading = false, songs = listOf(SongRowUi("1", "Hazard", "Richard Marx")), total = 1)
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}, initialSelection = listOf("1")) } }
        assertEquals((Spacing.m + 48.dp + Spacing.s).value, titleOffset("Hazard").value, 0.6f)   // the box here is tappable: 48 dp
    }

    @Test fun `the choose songs box sits the setlist's small gap from the title`() {
        val state = PickSongsUiState(setName = "Show", rows = listOf(PickRowUi("1", "Hazard", "Richard Marx", inSet = false, selected = false)), selectedCount = 0)
        compose.setContent { StagebookTheme { PickSongsScreen(state, "", {}, {}, {}, {}) } }
        assertEquals((Spacing.m + 24.dp + Spacing.s).value, titleOffset("Hazard").value, 0.6f)
    }
}
