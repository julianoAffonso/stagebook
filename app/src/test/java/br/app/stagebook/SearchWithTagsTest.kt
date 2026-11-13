package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import br.app.stagebook.ui.components.SearchWithTags
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On phones the search beside the tag filter has "Buscar" on top (user, 01/11), and typing still searches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class SearchWithTagsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `phone - label on top, hint below, and typing reaches the search`() {
        var typed = ""
        compose.setContent {
            var q by remember { mutableStateOf("") }
            StagebookTheme { SearchWithTags(q, { q = it; typed = it }, "Buscar música ou artista", listOf("Nacional"), emptySet(), {}) }
        }
        compose.onNodeWithText("Buscar").assertExists()
        compose.onNodeWithText("Música, artista").assertExists()
        compose.onNodeWithText("Buscar").performTextInput("legião")
        compose.waitForIdle()
        assertEquals("legião", typed)
    }

    // typed fast, the second letter left the field before the first came back from the screen, and the field was put
    // back to the first: "Vocalise" read "Va" (S26, 13/11)
    @Test fun `phone - a letter coming back late doesn't undo the ones typed after it`() {
        var shown by mutableStateOf("")
        var typed = ""
        compose.setContent { StagebookTheme { SearchWithTags(shown, { typed = it }, "Buscar música ou artista", listOf("Nacional"), emptySet(), {}) } }
        compose.onNodeWithText("Buscar").performTextInput("V")
        compose.onNodeWithText("Buscar").performTextInput("o")
        compose.waitForIdle()
        assertEquals("Vo", typed)
        // the screen is one letter behind
        shown = "V"
        compose.waitForIdle()
        compose.onNodeWithText("Vo").assertExists()
        assertEquals("Vo", typed)
        shown = "Vo"
        compose.waitForIdle()
        compose.onNodeWithText("Vo").assertExists()
        // cleared from outside (the ✕ of the header, a song opened): the field follows
        shown = ""
        compose.waitForIdle()
        compose.onNodeWithText("Vo").assertDoesNotExist()
        compose.onNodeWithText("Música, artista").assertExists()
    }

    @Test fun `an echo is told from a text set from outside`() {
        val echo = br.app.stagebook.ui.components.TextEcho("")
        assertEquals(true, echo.typed("V")); assertEquals(true, echo.typed("Vo"))
        assertEquals(false, echo.fromOutside("V")); assertEquals(false, echo.fromOutside("Vo"))
        assertEquals(true, echo.fromOutside(""))
        // what was set from outside is not typed news; the next letter is
        assertEquals(false, echo.typed("")); assertEquals(true, echo.typed("a"))
        // the screen skipped a step: the latest echo settles the earlier ones
        assertEquals(true, echo.typed("ab")); assertEquals(false, echo.fromOutside("ab")); assertEquals(true, echo.fromOutside("a"))
    }
}
