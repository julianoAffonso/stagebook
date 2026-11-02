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
}
