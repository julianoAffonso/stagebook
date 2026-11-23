package br.app.stagebook

import android.util.TypedValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.LOGO_TAG
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.drawer.BaseScreen
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The logo left the top of the menu (user, 19/11) and shows where it takes no room from playing: Settings → About, the
 * empty first-use screens (no stage, no songs) and the system's opening screen on Android 12 and newer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class AppLogoPlacesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `About opens with the logo, the name and what the app is`() {
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(), section = SettingsSection.ABOUT) } }
        compose.onNodeWithTag(LOGO_TAG).assertIsDisplayed()
        compose.onNodeWithText("Stagebook").assertIsDisplayed()
    }

    @Test fun `the first-use screen behind the menu shows the logo`() {
        compose.setContent { StagebookTheme { BaseScreen(openDrawer = false) } }
        compose.onNodeWithTag(LOGO_TAG).assertIsDisplayed()
    }

    @Test fun `an empty song list shows the logo`() {
        compose.setContent { StagebookTheme { SongsScreen(SongsUiState(loading = false), "", {}, {}, {}, {}) } }
        compose.onNodeWithTag(LOGO_TAG).assertIsDisplayed()
    }

    @Test fun `an empty setlist list keeps its own icon`() {
        compose.setContent { StagebookTheme { SetlistsScreen(SetlistsUiState(false, emptyList()), {}, {}, {}, {}) } }
        compose.onAllNodesWithTag(LOGO_TAG).assertCountEquals(0)
    }

    @Test fun `an empty state without the logo keeps the icon`() {
        compose.setContent { StagebookTheme { EmptyState(AppIcons.Search, "Nada", "") } }
        compose.onAllNodesWithTag(LOGO_TAG).assertCountEquals(0)
    }

    @Test fun `the opening screen is the logo on the app's dark background`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        context.setTheme(R.style.Theme_Stagebook)
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true)
        assertEquals(context.getColor(R.color.window_background), value.data)
        context.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true)
        assertEquals(R.mipmap.ic_launcher, value.resourceId)
    }
}
