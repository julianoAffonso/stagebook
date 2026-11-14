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

/** With only the player on, the group keeps its fixed name and says "Player on" (user, 14/11: the name no longer changes). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class MetroPlayerListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `player alone keeps the fixed name and says Player on`() {
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(false, Look(extrasOn = false, playerMode = true)), SettingsActions()) } }
        compose.onNodeWithText("Player e metrônomo").assertExists()
        compose.onNodeWithText("Player ligado").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Metrônomo e player").fetchSemanticsNodes().size)
    }
}
