package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.BreakTimer
import br.app.stagebook.domain.SetlistResume
import br.app.stagebook.domain.SetlistShow
import br.app.stagebook.domain.StageStart
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

/**
 * V08: tapping a setlist opens its stage (where the show stopped); the pencil and an empty setlist open the edit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistOpensStageTest {
    @get:Rule val compose = createComposeRule()

    private val played = mutableListOf<String>()
    private val edited = mutableListOf<String>()

    private fun list(vararg sets: SetlistRowUi) {
        compose.setContent {
            StagebookTheme { SetlistsScreen(SetlistsUiState(false, sets.toList()), {}, { edited += it }, { played += it }, {}) }
        }
    }

    @Test fun `tapping a setlist opens the stage, not the edit`() {
        list(SetlistRowUi("a", "Show de sábado", 5))
        compose.onNodeWithText("Show de sábado").performClick()
        assertEquals(listOf("a"), played)
        assertEquals(emptyList<String>(), edited)
    }

    @Test fun `the pencil on the card opens the edit`() {
        list(SetlistRowUi("a", "Show de sábado", 5))
        compose.onNodeWithContentDescription("Editar setlist").assertIsDisplayed().performClick()
        assertEquals(listOf("a"), edited)
        assertEquals(emptyList<String>(), played)
    }

    @Test fun `an empty setlist opens the edit when tapped, as there is nothing to play`() {
        list(SetlistRowUi("e", "Ensaio", 0))
        compose.onNodeWithText("Ensaio").performClick()
        assertEquals(listOf("e"), edited)
        assertEquals(emptyList<String>(), played)
    }

    @Test fun `the card has no play button any more, only the pencil`() {
        list(SetlistRowUi("a", "Show", 5), SetlistRowUi("b", "Ensaio", 3))
        compose.onAllNodesWithContentDescription("Palco").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Editar setlist").assertCountEquals(2)
    }

    // ---- where it opens ----
    private val items = listOf("a", "b", "c", "d")

    @Test fun `with no show today the stage opens on the first song`() {
        assertEquals(StageStart(0, false), SetlistResume.at("s", items, listOf(2), null, emptyMap(), "2026-11-14"))
    }

    @Test fun `it opens on the song last on stage today`() {
        val marks = SetlistShow.Marks("2026-11-14", listOf("a"), "c")
        assertEquals(StageStart(2, false), SetlistResume.at("s", items, emptyList(), marks, emptyMap(), "2026-11-14"))
    }

    @Test fun `marks from another day start the show clean`() {
        val marks = SetlistShow.Marks("2026-11-13", listOf("a"), "c")
        assertEquals(StageStart(0, false), SetlistResume.at("s", items, emptyList(), marks, emptyMap(), "2026-11-14"))
    }

    @Test fun `a song no longer in the setlist falls back to the first`() {
        val marks = SetlistShow.Marks("2026-11-14", emptyList(), "gone")
        assertEquals(StageStart(0, false), SetlistResume.at("s", items, emptyList(), marks, emptyMap(), "2026-11-14"))
    }

    @Test fun `a break left open after the song it stopped on comes back with it`() {
        val marks = SetlistShow.Marks("2026-11-14", listOf("a", "b"), "b")
        val timers = mapOf(BreakTimer.key("s", "c") to BreakTimer.Timer("2026-11-14", 0L, 1_000L, open = true))
        assertEquals(StageStart(1, true), SetlistResume.at("s", items, listOf(2), marks, timers, "2026-11-14"))
    }

    @Test fun `a closed break does not`() {
        val marks = SetlistShow.Marks("2026-11-14", listOf("a", "b"), "b")
        val timers = mapOf(BreakTimer.key("s", "c") to BreakTimer.Timer("2026-11-14", 0L, 1_000L, open = false))
        assertEquals(StageStart(1, false), SetlistResume.at("s", items, listOf(2), marks, timers, "2026-11-14"))
    }
}
