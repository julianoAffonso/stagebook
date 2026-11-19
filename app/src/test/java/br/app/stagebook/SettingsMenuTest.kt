package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import br.app.stagebook.navigation.Settings
import br.app.stagebook.ui.settings.SettingsMenu
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings in the side menu (user, 19/11): only the groups and the search; a group, or what the search found, opens on
 * its own page, full screen like the stage, with the found setting flashing there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SettingsMenuTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<Pair<SettingsSection, String?>>()

    private fun show() = compose.setContent {
        StagebookTheme { SettingsMenu(SettingsUiState(loading = false), onOpen = { s, item -> opened += s to item }) }
    }

    @Test fun `the menu lists the groups and a group opens its page`() {
        show()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
        compose.onNodeWithText("Aparelho").performClick()
        assertEquals(listOf(SettingsSection.DEVICE to null), opened)
    }

    @Test fun `a search result opens its group's page with the setting to flash`() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("modo essencial")
        compose.waitUntil(2_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Ligar o modo essencial")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ligar o modo essencial").performClick()
        assertEquals(listOf(SettingsSection.DEVICE to "essential"), opened)
    }

    // a short phone: the language sits below the fold of the Device page
    @Config(qualifiers = "pt-rBR-w360dp-h400dp-port-xxhdpi")
    @Test fun `the found setting comes into view on its page`() {
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.settings.SettingsScreen(
                    SettingsUiState(loading = false), br.app.stagebook.ui.settings.SettingsActions(),
                    section = SettingsSection.DEVICE, onLeave = {}, focusItem = "language",
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(br.app.stagebook.ui.components.SettingsItemKey, "language")).assertIsDisplayed()
    }

    @Test fun `a group's page has no back arrow - the menu's handle opens the menu, as on the stage`() {
        var menu = 0
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.settings.SettingsScreen(
                    SettingsUiState(loading = false), br.app.stagebook.ui.settings.SettingsActions(),
                    section = SettingsSection.DEVICE, onLeave = {}, onMenu = { menu++ },
                )
            }
        }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Voltar")).assertCountEquals(0)
        compose.onNodeWithText("Aparelho").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasTestTag(br.app.stagebook.ui.stage.DRAWER_HANDLE_TAG)).performClick()
        assertEquals(1, menu)
    }

    @Test fun `with the menu pinned beside it, the page has neither arrow nor handle`() {
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.settings.SettingsScreen(
                    SettingsUiState(loading = false), br.app.stagebook.ui.settings.SettingsActions(), section = SettingsSection.DEVICE, onLeave = {},
                )
            }
        }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Voltar")).assertCountEquals(0)
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(br.app.stagebook.ui.stage.DRAWER_HANDLE_TAG)).assertCountEquals(0)
    }

    @Test fun `the page opened from the menu carries the setting found, and an old link has none`() {
        assertEquals("essential", Settings("DEVICE", alone = true, fromMenu = true, item = "essential").item)
        assertEquals("", Settings("DEVICE").item)
    }
}
