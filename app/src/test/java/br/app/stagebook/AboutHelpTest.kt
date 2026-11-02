package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun `the help address is the web page in each language`() {
        assertTrue(context.getString(R.string.help_url).startsWith("https://"))
    }
}
