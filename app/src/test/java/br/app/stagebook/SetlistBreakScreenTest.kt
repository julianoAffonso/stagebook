package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/**
 * A break inside a setlist: its row between the songs, the blocks it makes and the count under the name. It is dragged
 * and taken out while editing the setlist (the pencil).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class SetlistBreakScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun state(songs: Int, breaks: List<Int>) = SetlistDetailUiState(
        loading = false,
        name = "Show",
        items = ('a' until 'a' + songs).map { SetItemUi("$it", "$it", "Música $it", "", false) },
        breaks = breaks,
    )

    private fun show(
        state: SetlistDetailUiState,
        onPlay: (Int) -> Unit = {},
        onMove: (Int, Int) -> Unit = { _, _ -> },
        onAddBreak: () -> Unit = {},
        onRemoveBreak: (Int) -> Unit = {},
    ) = compose.setContent {
        StagebookTheme { SetlistDetailScreen(state, null, {}, {}, onPlay, {}, {}, {}, onMove, { _, _ -> }, {}, onAddBreak = onAddBreak, onRemoveBreak = onRemoveBreak) }
    }

    @Test fun `the break shows between the songs, with a header on each block and the count under the name`() {
        show(state(5, listOf(3)))
        compose.onNodeWithText("INTERVALO").assertExists()
        compose.onNodeWithText("BLOCO 1 · 3 músicas").assertExists()
        compose.onNodeWithText("BLOCO 2 · 2 músicas").assertExists()
        compose.onNodeWithText("5 músicas · 1 intervalo", substring = true).assertExists()
        // the numbers go on counting past the break
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithText("5").assertExists()
    }

    @Test fun `a block with one song and two breaks say so in the singular and the plural`() {
        show(state(4, listOf(1, 3)))
        compose.onNodeWithText("BLOCO 1 · 1 música").assertExists()
        compose.onNodeWithText("BLOCO 2 · 2 músicas").assertExists()
        compose.onNodeWithText("BLOCO 3 · 1 música").assertExists()
        compose.onNodeWithText("4 músicas · 2 intervalos", substring = true).assertExists()
    }

    @Test fun `with no break there are no block headers and the count is only the songs`() {
        show(state(3, emptyList()))
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("BLOCO 1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("intervalo", substring = true).assertDoesNotExist()
        compose.onNodeWithText("3 músicas", substring = true).assertExists()
    }

    @Test fun `the x on the break takes it out`() {
        val removed = mutableListOf<Int>()
        show(state(5, listOf(3)), onRemoveBreak = { removed += it })
        compose.onNodeWithContentDescription("Tirar o intervalo").assertDoesNotExist()   // only while editing
        compose.onNodeWithContentDescription("Editar setlist").performClick()
        compose.onNodeWithContentDescription("Tirar o intervalo").performClick()
        assertEquals(listOf(3), removed)
    }

    @Test fun `the Break button adds one, and needs two songs`() {
        var added = 0
        show(state(2, emptyList()), onAddBreak = { added++ })
        compose.onNodeWithText("Intervalo").assertIsEnabled().performClick()
        assertEquals(1, added)
    }

    @Test fun `with a single song the Break button is off`() {
        show(state(1, emptyList()))
        compose.onNodeWithText("Intervalo").assertIsNotEnabled()
    }

    // the first touch on the off button used to meet nothing (user test, 14/11): it says why, to the eye and to the screen reader
    @Test fun `with a single song the Break button says why it is off and a touch leaves nothing added`() {
        var added = 0
        val message = br.app.stagebook.ui.components.AppMessage(
            androidx.compose.material3.SnackbarHostState(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        compose.setContent {
            StagebookTheme {
                androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.components.LocalAppMessage provides message) {
                    SetlistDetailScreen(state(1, emptyList()), null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, onAddBreak = { added++ }, onRemoveBreak = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Intervalo. Precisa de pelo menos 2 músicas").assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertEquals("Precisa de pelo menos 2 músicas", message.host.currentSnackbarData?.visuals?.message)
        assertEquals(0, added)
    }

    @Test fun `with two songs the Break button has no hint`() {
        show(state(2, emptyList()))
        compose.onNodeWithContentDescription("Intervalo. Precisa de pelo menos 2 músicas").assertDoesNotExist()
    }

    @Test fun `tapping a song after the break opens the stage on that song`() {
        var opened = -1
        show(state(5, listOf(3)), onPlay = { opened = it })
        compose.onNodeWithText("Música d").performClick()
        assertEquals(3, opened)
    }

    @Test fun `the break is dragged by its handle like a song`() {
        val moves = mutableListOf<Pair<Int, Int>>()
        show(state(5, listOf(3)), onMove = { f, t -> moves += f to t })
        compose.onNodeWithContentDescription("Editar setlist").performClick()   // the handles only show while editing
        // rows: a b c | d e, so the 4th handle is the break's
        compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")[3].performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, -8f)) }   // ~1.5 rows up, slow like a finger
            up()
        }
        compose.waitForIdle()
        assertEquals("one save, with the whole move: $moves", 1, moves.size)
        assertEquals(3, moves.single().first)
        assertTrue("moves=$moves", moves.single().second < 3)
    }
}
