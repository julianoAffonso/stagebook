package br.app.stagebook

import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.domain.SetlistBreaks
import br.app.stagebook.domain.SetlistShow.State
import br.app.stagebook.ui.components.AppMessage
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.rememberAppMessage
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
 * The two faces of a setlist's screen. The show: no handles, each song's key, tempo and capo, and where the show is
 * (played, now, next). The edit: handles and a remove button on every row, and a removal that can be undone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class SetlistShowScreenTest {
    @get:Rule val compose = createComposeRule()

    // a b c | d, the break before d; a played, b on stage, c next (the stage says it: the edit doesn't)
    private val show = SetlistDetailUiState(
        loading = false,
        name = "Show",
        items = listOf(
            SetItemUi("a", "a", "Música a", "", false, songKey = "G", bpm = 96, state = State.PLAYED),
            SetItemUi("b", "b", "Música b", "", false, songKey = "Em", bpm = 120, capo = 2, hasTrack = true, state = State.NOW),
            SetItemUi("c", "c", "Música c", "", false, state = State.NEXT),
            SetItemUi("d", "d", "Música d", "", false, songKey = "A"),
        ),
        breaks = listOf(3),
        anyPlayed = true,
    )
    private var state by mutableStateOf(show)
    private var before: SetlistDetailUiState? = null
    private var deleted = 0
    private var left = 0
    private var removedBreaks = 0
    private val density = 1.5f   // hdpi

    private fun screen(initial: SetlistDetailUiState = show, openShare: Boolean = false) {
        state = initial
        compose.setContent {
            StagebookTheme {
                // the app's message bar, as the app's root draws it
                val message: AppMessage = rememberAppMessage()
                CompositionLocalProvider(LocalAppMessage provides message) {
                    SetlistDetailScreen(
                        state, null, { left++ }, {}, {}, {}, {}, { _, _ -> },
                        onRemove = { index, _ ->
                            before = state
                            state = state.copy(
                                items = state.items.filterIndexed { i, _ -> i != index },
                                breaks = SetlistBreaks.afterRemoving(state.breaks, state.items.size, setOf(index)),
                            )
                        },
                        onDeleteSetlist = { deleted++ },
                        onRemoveBreak = { removedBreaks++ },
                        onUndoRemove = { before?.let { state = it } },
                        openShare = openShare,
                    )
                    SnackbarHost(message.host)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun handles() = compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")
    private fun removeButtons() = compose.onAllNodesWithContentDescription("Tirar deste setlist")
    /** Where one text of a row is drawn (the row's texts are read together, so each one is looked up on its own). */
    private fun part(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    // ---- the edit is the whole screen ----
    @Test fun `the screen is the edit, with a handle and a remove button on every row`() {
        screen()
        compose.onNodeWithText("Editar setlist").assertIsDisplayed()   // the title
        handles().assertCountEquals(5)        // four songs and the break
        removeButtons().assertCountEquals(4)
        compose.onNodeWithContentDescription("Tirar o intervalo").assertExists()
        compose.onNodeWithText("Show · 4 músicas · 1 intervalo").assertExists()   // which setlist, under the title
        // nothing that belongs to the show: no marks, no stage button, no clear played
        listOf("TOCADA", "AGORA", "PRÓX.").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithContentDescription("Palco").assertDoesNotExist()
    }

    @Test fun `each song keeps showing its key, tempo, capo and backing track`() {
        screen()
        compose.onNodeWithText("G").assertExists()
        compose.onNodeWithText("Em").assertExists()
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("96 bpm").assertExists()
        compose.onNodeWithText("120 bpm").assertExists()
        compose.onNodeWithText("capo 2").assertExists()
        compose.onAllNodesWithText("capo", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("bpm", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Backing track").assertCountEquals(1)
    }

    @Test fun `Songs, Break and Share are on the screen`() {
        screen()
        compose.onNodeWithText("Músicas").assertIsDisplayed()
        compose.onNodeWithText("Intervalo").assertIsDisplayed()
        compose.onNodeWithText("Compartilhar").assertIsDisplayed()
    }

    @Test fun `a row draws its handle, its small number, its title and its remove button, in that order`() {
        screen()
        val handle = handles()[1].assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val number = part("2")
        val title = part("Música b")
        val remove = removeButtons()[1].assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("$handle $number $title $remove", handle.right <= number.left && number.right <= title.left && title.right <= remove.left)
        assertTrue("handle and number at the same height", kotlin.math.abs(handle.center.y - number.center.y) <= 2 * density)
        // the handle sits at the row's very edge, and the break's is in line with it
        val row = compose.onNodeWithText("Música b").fetchSemanticsNode().boundsInRoot   // the row speaks as one: its texts together
        assertTrue("row $row handle $handle", handle.left - row.left <= 8 * density)
        val breakHandle = handles()[3].fetchSemanticsNode().boundsInRoot
        assertEquals(handle.left, breakHandle.left, 1f)
    }

    @Test fun `a tap on a song opens nothing, and holding one still selects`() {
        screen()
        compose.onNodeWithText("Música d").performClick()
        compose.onNodeWithText("Editar setlist").assertIsDisplayed()
        compose.onNodeWithText("Música b").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Selecionar todas").assertExists()
    }

    // ---- the top bar and its menu ----
    @Test fun `Done leaves, and so does the x`() {
        screen()
        compose.onNodeWithText("Pronto").performClick()
        assertEquals(1, left)
        compose.onNodeWithContentDescription("Fechar").performClick()
        assertEquals(2, left)
    }

    @Test fun `the menu has rename and delete only, and delete still asks`() {
        screen()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear").assertIsDisplayed()
        compose.onNodeWithText("Limpar tocadas").assertDoesNotExist()
        compose.onNodeWithText("Apagar o setlist").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("not before the answer", 0, deleted)
        compose.onNodeWithText("Apagar o setlist \"Show\"? As músicas continuam na biblioteca.").assertIsDisplayed()
        compose.onNodeWithText("Apagar").performClick()
        assertEquals(1, deleted)
    }

    @Test fun `rename in the menu opens the window`() {
        screen()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Renomear").assertIsDisplayed()   // the window's title: the menu is closed by now
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test fun `from the stage's menu the Share sheet is already open`() {
        screen(openShare = true)
        compose.onNodeWithText("QR sem internet").assertIsDisplayed()
    }

    @Test fun `without it the Share sheet waits for the button`() {
        screen()
        compose.onNodeWithText("QR sem internet").assertDoesNotExist()
        compose.onNodeWithText("Compartilhar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("QR sem internet").assertIsDisplayed()
    }

    // ---- taking songs out ----
    @Test fun `removing a song asks nothing, says it left and Undo puts it back with the break in place`() {
        screen()
        removeButtons()[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Música b").assertDoesNotExist()
        compose.onNodeWithText("Música b saiu deste setlist").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertDoesNotExist()   // no confirmation window
        assertEquals(listOf("a", "c", "d"), state.items.map { it.key })
        assertEquals(listOf(2), state.breaks)
        compose.onNodeWithText("Desfazer").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a", "b", "c", "d"), state.items.map { it.key })
        assertEquals(listOf(3), state.breaks)
        compose.onNodeWithText("Música b").assertExists()
        handles().assertCountEquals(5)
    }

    @Test fun `removing the break shows no message`() {
        screen()
        compose.onNodeWithContentDescription("Tirar o intervalo").performClick()
        compose.waitForIdle()
        assertEquals(1, removedBreaks)
        compose.onNodeWithText("Desfazer").assertDoesNotExist()
    }
}
