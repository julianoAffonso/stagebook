package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
 * An open setlist in the menu has no back arrow (user, 19/11: it read as "close the menu"): "‹ Todos os setlists" above the big
 * name goes back to the list, and so does tapping the Setlists tab again, which asks first when an edit is half done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistParentLinkTest {
    @get:Rule val compose = createComposeRule()
    private var left = 0
    private val two = listOf(SetItemUi("a", "a", "Música a", "", false), SetItemUi("b", "b", "Música b", "", false))
    private var state by mutableStateOf(SetlistDetailUiState(loading = false, name = "Show de sábado", items = two, breaks = emptyList()))
    private var toList by mutableIntStateOf(0)

    private fun show(editing: Boolean = false) = compose.setContent {
        StagebookTheme {
            SetlistDetailScreen(state, null, { left++ }, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, startEditing = editing, toList = toList)
        }
    }

    @Test fun `no back arrow - the list it came from is a link above the name, and it goes back`() {
        show()
        compose.onAllNodesWithContentDescription("Voltar").assertCountEquals(0)
        val link = compose.onNodeWithText("‹ Todos os setlists").getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText("Show de sábado").getUnclippedBoundsInRoot()
        assertTrue("the link is above the name", link.bottom <= name.top + 1f.let { androidx.compose.ui.unit.Dp(it) })
        // the name has the whole width: the icons sit on the link's line, not beside the name
        val edit = compose.onNodeWithContentDescription("Editar setlist").getUnclippedBoundsInRoot()
        assertTrue("icons on the link's line", edit.bottom <= name.top + androidx.compose.ui.unit.Dp(1f))
        compose.onNodeWithText("‹ Todos os setlists").performClick()
        assertEquals(1, left)
    }

    @Test fun `editing shows the X that ends it, not the link`() {
        show(editing = true)
        compose.onAllNodesWithText("‹ Todos os setlists").assertCountEquals(0)
        compose.onNodeWithText("Show de sábado").assertExists()
    }

    @Test fun `the tab tapped again goes back to the list`() {
        show()
        toList++
        compose.waitForIdle()
        assertEquals(1, left)
    }

    @Test fun `the tab tapped again with an edit half done asks first`() {
        show(editing = true)
        state = state.copy(items = two.take(1))   // a song taken out while editing
        compose.waitForIdle()
        toList++
        compose.waitForIdle()
        assertEquals("nothing left yet", 0, left)
        compose.onNodeWithText("Descartar").performClick()
        compose.waitForIdle()
        assertEquals(1, left)
    }

    @Test fun `opening does not count as a tap on the tab`() {
        toList = 3
        show()
        compose.waitForIdle()
        assertEquals(0, left)
    }
}
