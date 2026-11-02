package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With only the player on, the item is "Player" and its summary just "On", not "Player on" again (user, 02/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class MetroPlayerListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `player alone reads Player and On`() {
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(false, Look(extrasOn = false, playerMode = true)), SettingsActions()) } }
        compose.onNodeWithText("Player").assertExists()
        compose.onNodeWithText("Ligado").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Player ligado").fetchSemanticsNodes().size)
    }
}
