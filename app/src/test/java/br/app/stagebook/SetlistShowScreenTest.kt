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

    // a b c | d, the break before d; a played, b on stage, c next
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
    private var opened = -1
    private var deleted = 0
    private var cleared = 0
    private var removedBreaks = 0

    private fun show(initial: SetlistDetailUiState = show) {
        state = initial
        compose.setContent {
            StagebookTheme {
                // the app's message bar, as the app's root draws it
                val message: AppMessage = rememberAppMessage()
                CompositionLocalProvider(LocalAppMessage provides message) {
                    SetlistDetailScreen(
                        state, null, {}, {}, { opened = it }, {}, {}, {}, { _, _ -> },
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
                        onClearPlayed = { cleared++ },
                    )
                    SnackbarHost(message.host)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun handles() = compose.onAllNodesWithContentDescription("Arrastar para mudar a ordem")
    private fun removeButtons() = compose.onAllNodesWithContentDescription("Tirar deste setlist")
    private fun edit() { compose.onNodeWithContentDescription("Editar setlist").performClick(); compose.waitForIdle() }

    // ---- the show ----
    @Test fun `the show has no handle and no remove button, on songs or on the break`() {
        show()
        handles().assertCountEquals(0)
        removeButtons().assertCountEquals(0)
        compose.onNodeWithText("INTERVALO").assertExists()
        compose.onNodeWithContentDescription("Tirar o intervalo").assertDoesNotExist()
        compose.onNodeWithText("Compartilhar").assertExists()
    }

    @Test fun `each song shows its key, tempo and capo, and a play mark with a backing track`() {
        show()
        compose.onNodeWithText("G").assertExists()
        compose.onNodeWithText("Em").assertExists()
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("96 bpm").assertExists()
        compose.onNodeWithText("120 bpm").assertExists()
        compose.onNodeWithText("capo 2").assertExists()
        // only what the song has: one capo, one backing track, and nothing for the song with no key or tempo
        compose.onAllNodesWithText("capo", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("bpm", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Backing track").assertCountEquals(1)
    }

    @Test fun `played, now and next are written on their rows`() {
        show()
        compose.onNodeWithText("TOCADA").assertExists()
        compose.onNodeWithText("AGORA").assertExists()
        compose.onNodeWithText("PRÓX.").assertExists()
    }

    @Test fun `only the states the songs have are written`() {
        show(show.copy(items = show.items.map { it.copy(state = if (it.key == "d") State.NOW else State.NONE) }))
        compose.onNodeWithText("AGORA").assertExists()   // the last song on stage: no next
        compose.onNodeWithText("PRÓX.").assertDoesNotExist()
        compose.onNodeWithText("TOCADA").assertDoesNotExist()
    }

    @Test fun `tapping a song still opens the stage on it, and holding one still selects`() {
        show()
        compose.onNodeWithText("Música d").performClick()
        assertEquals(3, opened)
        compose.onNodeWithText("Música b").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Selecionar todas").assertExists()
        compose.onNodeWithContentDescription("Editar setlist").assertDoesNotExist()
    }

    // ---- the top bar and its menu ----
    @Test fun `the top bar has the pencil, the stage and the menu, and no trash`() {
        show()
        compose.onNodeWithContentDescription("Editar setlist").assertIsDisplayed()
        compose.onNodeWithContentDescription("Palco").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mais opções").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apagar o setlist").assertDoesNotExist()
        compose.onNodeWithText("Apagar o setlist").assertDoesNotExist()
    }

    @Test fun `the menu has rename, clear played and delete, and delete still asks`() {
        show()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear").assertIsDisplayed()
        compose.onNodeWithText("Limpar tocadas").assertIsDisplayed()
        compose.onNodeWithText("Apagar o setlist").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("not before the answer", 0, deleted)
        compose.onNodeWithText("Apagar o setlist \"Show\"? As músicas continuam na biblioteca.").assertIsDisplayed()
        compose.onNodeWithText("Apagar").performClick()
        assertEquals(1, deleted)
    }

    @Test fun `clear played is only in the menu with a song played, and clears`() {
        show(show.copy(anyPlayed = false))
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear").assertIsDisplayed()
        compose.onNodeWithText("Limpar tocadas").assertDoesNotExist()
        compose.runOnUiThread { state = show }
        compose.waitForIdle()
        compose.onNodeWithText("Limpar tocadas").performClick()
        assertEquals(1, cleared)
    }

    @Test fun `rename in the menu opens the same window as tapping the name`() {
        show()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Renomear").assertIsDisplayed()   // the window's title: the menu is closed by now
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    // ---- the edit ----
    @Test fun `the pencil shows a handle and a remove button on every row and hides the show's marks`() {
        show()
        edit()
        compose.onNodeWithText("Editar setlist").assertIsDisplayed()   // the title
        handles().assertCountEquals(5)        // four songs and the break
        removeButtons().assertCountEquals(4)
        compose.onNodeWithContentDescription("Tirar o intervalo").assertExists()
        listOf("G", "Em", "A", "120 bpm", "capo 2", "TOCADA", "AGORA", "PRÓX.").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onAllNodesWithContentDescription("Backing track").assertCountEquals(0)
        // nothing but the list to work on: no Songs, Break or Share, no stage and no menu
        compose.onNodeWithText("Compartilhar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Palco").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mais opções").assertDoesNotExist()
        compose.onNodeWithText("Música b").performClick()
        assertEquals("a tap while editing opens nothing", -1, opened)
    }

    @Test fun `Done leaves the edit, and so does the x`() {
        show()
        edit()
        compose.onNodeWithText("Pronto").performClick()
        compose.waitForIdle()
        handles().assertCountEquals(0)
        compose.onNodeWithText("Em").assertExists()
        compose.onNodeWithText("AGORA").assertExists()
        compose.onNodeWithText("Show").assertExists()
        edit()
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        handles().assertCountEquals(0)
        compose.onNodeWithText("Compartilhar").assertExists()
    }

    @Test fun `removing a song asks nothing, says it left and Undo puts it back with the break in place`() {
        show()
        edit()
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
        handles().assertCountEquals(5)   // still editing
    }

    @Test fun `removing the break shows no message`() {
        show()
        edit()
        compose.onNodeWithContentDescription("Tirar o intervalo").performClick()
        compose.waitForIdle()
        assertEquals(1, removedBreaks)
        compose.onNodeWithText("Desfazer").assertDoesNotExist()
    }
}
