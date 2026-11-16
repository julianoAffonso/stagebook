package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A setlist that opens empty (just created) opens ready to take songs, with the + its own hint points at; one with songs
 * opens to be looked at (help review, 16/11: the empty one opened looking, and its hint named a button not on screen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistOpensEmptyTest {
    @get:Rule val compose = createComposeRule()

    private fun show(songs: Int) = compose.setContent {
        val items = (0 until songs).map { SetItemUi("k$it", "s$it", "Música $it", "", false) }
        StagebookTheme { SetlistDetailScreen(SetlistDetailUiState(loading = false, name = "Show", items = items, breaks = emptyList()), null, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) }
    }

    @Test fun `an empty setlist opens in editing, with the + its hint talks about`() {
        show(0)
        compose.waitForIdle()
        compose.onNodeWithText("Toque no + para escolher o que entra no setlist.").assertExists()
        compose.onNodeWithContentDescription("Músicas").assertExists()
    }

    @Test fun `a setlist with songs opens to be looked at, with the pencil`() {
        show(2)
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Músicas").assertCountEquals(0)
        compose.onNodeWithContentDescription("Editar setlist").assertExists()
    }
}
