package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.drawer.SetlistRequest
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The setlist open in the library drawer (user, 15/11): a tap on a song plays it, counting the songs and not the breaks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class SetlistPlayAtTest {
    @get:Rule val compose = createComposeRule()

    private fun show(breaks: List<Int>, played: MutableList<Int>) {
        val state = SetlistDetailUiState(
            loading = false,
            name = "Show",
            items = listOf("a", "b", "c").map { SetItemUi(it, it, "Música $it", "", false) },
            breaks = breaks,
        )
        compose.setContent {
            StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, onPlayAt = { played += it }) }
        }
    }

    @Test fun `a tap on a song plays it at its position`() {
        val played = mutableListOf<Int>()
        show(emptyList(), played)
        compose.onNodeWithText("Música b").performClick()
        assertEquals(listOf(1), played)
    }

    @Test fun `a break before the song does not count in its position`() {
        val played = mutableListOf<Int>()
        show(listOf(2), played)   // a break between the second and the third
        compose.onNodeWithText("Música c").performClick()
        assertEquals(listOf(2), played)
    }

    @Test fun `each request for a setlist is its own, so asking for the same one again opens it again`() {
        val first = SetlistRequest("a")
        Thread.sleep(2)
        assertNotEquals(first, SetlistRequest("a"))
    }
}
