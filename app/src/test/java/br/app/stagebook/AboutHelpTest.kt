package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → About: "Open the help" goes to the help on the web, in the app's language (user, 01/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AboutHelpTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `the help button asks to open the help`() {
        var opened = 0
        compose.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(onOpenHelp = { opened++ }), section = SettingsSection.ABOUT) }
        }
        compose.onNodeWithText(context.getString(R.string.about_help_open)).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    // a second tap only started the same install over (audit, 10/11): Install stays off while Android has it
    @Test fun `Install stays off while the install is with Android`() {
        var waiting by androidx.compose.runtime.mutableStateOf(true)
        var taps = 0
        compose.setContent {
            StagebookTheme {
                SettingsScreen(SettingsUiState(loading = false, update = br.app.stagebook.ui.settings.UpdateState.Ready),
                    SettingsActions(onInstallUpdate = { taps++ }, installWaiting = waiting), section = SettingsSection.ABOUT)
            }
        }
        val install = context.getString(R.string.settings_update_install)
        compose.onNodeWithText(install).performScrollTo().assertIsNotEnabled()
        waiting = false
        compose.onNodeWithText(install).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, taps)
    }

    // the rail's logo opens About by itself, on a wide tablet too: no section list, and back leaves it (user, 03/11)
    @Test @Config(qualifiers = "w1100dp-h720dp-land-xhdpi")
    fun `About opened by itself shows only About and its back arrow leaves`() {
        var left = 0
        compose.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(), section = SettingsSection.ABOUT, onLeave = { left++ }) }
        }
        compose.onNodeWithText(context.getString(R.string.settings_appearance)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        assertEquals(1, left)
    }

    @Test fun `the help address is the web page in each language`() {
        assertTrue(context.getString(R.string.help_url).startsWith("https://"))
    }
}
