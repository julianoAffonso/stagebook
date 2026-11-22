package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Discard puts the setlist back as it was when editing began, even after adding songs: that opens another screen over this one
 * (it leaves the composition and comes back), and the copy of "as it was" was lost then (audit, 15/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SetlistDiscardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Discard restores the setlist as it was when editing began, after the screen left and came back`() {
        val tester = StateRestorationTester(compose)
        var restored: Pair<List<String>, List<Int>>? = null
        var state by mutableStateOf(SetlistDetailUiState(loading = false, name = "Show", items = listOf(SetItemUi("a", "a", "A", "", false), SetItemUi("b", "b", "B", "", false))))
        tester.setContent {
            StagebookTheme {
                SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, onRemove = { _, _ -> }, onDeleteSetlist = {}, onRestore = { ids, br -> restored = ids to br })
            }
        }
        compose.onNodeWithContentDescription("Editar setlist").performClick()
        compose.waitForIdle()
        // songs added (what the picker does) and the screen gone and back, as when the picker is opened and closed
        state = state.copy(items = state.items + SetItemUi("c", "c", "C", "", false))
        compose.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Descartar").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a", "b"), restored?.first)
    }

    @Test fun `the question says Discard also undoes what the band or the computer changed meanwhile`() {
        var state by mutableStateOf(SetlistDetailUiState(loading = false, name = "Show", items = listOf(SetItemUi("a", "a", "A", "", false)), external = 0))
        compose.setContent { StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, onRemove = { _, _ -> }, onDeleteSetlist = {}) } }
        compose.onNodeWithContentDescription("Editar setlist").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Fechar").performClick()   // unchanged: leaves the edit with no question
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Editar setlist").performClick()
        compose.waitForIdle()
        state = state.copy(external = 1, items = state.items + SetItemUi("b", "b", "B", "", false))   // changed from outside
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("também foi mudado de fora", substring = true).assertExists()
    }
}
