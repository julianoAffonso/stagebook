package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → About names the services the app talks to and the libraries it is made of (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class AboutCreditsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `About lists the services and the libraries with their licenses`() {
        compose.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(), Modifier.fillMaxSize(), section = SettingsSection.ABOUT, onLeave = {}, onMenu = {}) }
        }
        compose.onNodeWithText("Serviços e bibliotecas").performScrollTo().assertExists()
        compose.onNodeWithText("MusicBrainz: artista e ano", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("ntfy.sh", substring = true).assertExists()
        compose.onNodeWithText("PdfBox-Android (Apache 2.0)", substring = true).performScrollTo().assertExists()
    }
}
