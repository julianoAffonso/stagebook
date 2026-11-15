package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The area that takes the drag is a real touch target: wide and as tall as the row, not just the 32 dp handle drawn. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class SetlistDragZoneTest {
    @get:Rule val compose = createComposeRule()

    private fun show(moves: MutableList<Pair<Int, Int>>) {
        val state = SetlistDetailUiState(
            loading = false,
            name = "Show",
            items = listOf("a", "b", "c").map { SetItemUi(it, it, "Música $it", "", false) },
        )
        compose.setContent {
            StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { f, t -> moves += f to t }, { _, _ -> }, {}) }
        }
    }

    @Test fun `the drag area is 48 dp wide and as tall as the row`() {
        show(mutableListOf())
        val zone = compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")[0]
        zone.assertWidthIsAtLeast(48.dp)
        val row = compose.onNodeWithText("Música a").getBoundsInRoot()
        zone.assertHeightIsAtLeast(row.height - 1.dp)
    }

    @Test fun `a drag starting 34 dp beside the drawn handle still moves the song`() {
        val moves = mutableListOf<Pair<Int, Int>>()
        show(moves)
        compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")[0].performTouchInput {
            // the icon's center is 16 dp from the row's edge; 34 dp past it (50 dp) was outside the old handle, even with
            // the 48 dp minimum Compose already gives a pointer input (it reached 40 dp)
            down(Offset(50.dp.toPx(), center.y))
            repeat(20) { moveBy(Offset(0f, 8f)) }
            up()
        }
        compose.waitForIdle()
        assertTrue("moves=$moves", moves.contains(0 to 1))
        assertEquals(1, moves.size)
    }
}
