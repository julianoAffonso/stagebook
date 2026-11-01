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
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A setlist created while the list is at the top shows at the top, not hidden above it (device test B-09). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistsNewOnTopTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a new setlist at the top of the list is on screen`() {
        val old = (1..15).map { SetlistRowUi("s$it", "Setlist $it", it) }
        var state by mutableStateOf(SetlistsUiState(false, old))
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("Setlist 1").assertIsDisplayed()
        state = SetlistsUiState(false, listOf(SetlistRowUi("novo", "01/11/2026 17:40", 0)) + old)
        compose.waitForIdle()
        compose.onNodeWithText("01/11/2026 17:40").assertIsDisplayed()
    }
}
