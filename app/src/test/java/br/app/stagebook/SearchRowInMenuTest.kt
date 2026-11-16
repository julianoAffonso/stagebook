package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.components.SearchWithTags
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On a tablet the side menu is 420 dp wide: its search row must be laid out for that width, not for the screen's
 * (user, 16/11: "the tag field is huge and the one to type the song tiny on the tablet").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class SearchRowInMenuTest {
    @get:Rule val compose = createComposeRule()

    private fun show(width: Int) = compose.setContent {
        StagebookTheme {
            Box(Modifier.width(width.dp).testTag("row")) { SearchWithTags("", {}, "Buscar música ou artista", listOf("Nacional", "Rock"), emptySet(), {}) }
        }
    }

    @Test fun `in the menu of a tablet the search is wider than the tag filter`() {
        show(388)   // the menu's 420 dp less its side margins
        val row = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val tags = compose.onNodeWithText("Tags").getUnclippedBoundsInRoot()
        // the tag filter starts past the middle of the row: the search has more than half of it
        assertTrue("tags start at ${tags.left} of ${row.right}", tags.left.value > row.left.value + (row.right.value - row.left.value) * 0.55f)
    }

    @Test fun `on the whole width of a tablet the long hint still fits beside the filter`() {
        show(1200)
        compose.onNodeWithText("Buscar música ou artista").assertExists()
    }
}
