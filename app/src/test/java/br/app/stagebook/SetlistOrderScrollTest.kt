package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.setlists.setlistOrder
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Changing the order starts the list at the top (audit 03/11: it followed the old first setlist down the list). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistOrderScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `switching to A to Z shows the first setlist`() {
        // newest first: "Zebra" on top; in A to Z it goes to the end
        val sets = (listOf("Zebra") + ('A'..'P').map { "Show $it" }).mapIndexed { i, n -> SetlistRowUi("$i", n, 3) }
        var state by mutableStateOf(SetlistsUiState(loading = false, sets = sets))
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("Zebra").assertIsDisplayed()
        state = SetlistsUiState(loading = false, sets = setlistOrder(sets, abc = true), abc = true)
        compose.waitForIdle()
        compose.onNodeWithText("Show A").assertIsDisplayed()
    }
}
