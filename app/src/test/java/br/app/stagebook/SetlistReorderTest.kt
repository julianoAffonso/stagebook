package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** Arrastar pela alça muda a ordem do setlist (biblioteca reorderable). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class SetlistReorderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `arrastar a primeira música para baixo da segunda`() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val state = SetlistDetailUiState(
            loading = false,
            name = "Show",
            items = listOf("a", "b", "c").map { SetItemUi(it, it, "Música $it", "", false) },
        )
        compose.setContent {
            StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, {}, { f, t -> moves += f to t }, {}, { _, _ -> }, {}) }
        }
        compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")[0].performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 8f)) }   // ~1,5 linha para baixo, devagar como um dedo
            up()
        }
        compose.waitForIdle()
        assertTrue("moves=$moves", moves.contains(0 to 1))
    }

    @Test fun `arrastar por várias posições grava uma vez só, ao soltar`() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val state = SetlistDetailUiState(
            loading = false,
            name = "Show",
            items = listOf("a", "b", "c", "d").map { SetItemUi(it, it, "Música $it", "", false) },
        )
        compose.setContent {
            StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, {}, { f, t -> moves += f to t }, {}, { _, _ -> }, {}) }
        }
        compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")[0].performTouchInput {
            down(center)
            repeat(40) { moveBy(Offset(0f, 8f)) }   // ~2,5 linhas para baixo
            up()
        }
        compose.waitForIdle()
        assertEquals("uma gravação só, com o movimento inteiro: $moves", 1, moves.size)
        assertEquals(0, moves.single().first)
        assertTrue(moves.single().second >= 2)
    }

    @Test fun `tocar numa música do setlist abre o palco nela`() {
        var opened = -1
        val state = SetlistDetailUiState(
            loading = false,
            name = "Show",
            items = listOf("a", "b", "c").map { SetItemUi(it, it, "Música $it", "", false) },
        )
        compose.setContent {
            StagebookTheme { SetlistDetailScreen(state, null, {}, {}, { opened = it }, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}) }
        }
        compose.onNodeWithText("Música b").performClick()
        assertEquals(1, opened)
        compose.onNodeWithContentDescription("Palco").performClick()   // play do topo: do começo
        assertEquals(0, opened)
    }
}
