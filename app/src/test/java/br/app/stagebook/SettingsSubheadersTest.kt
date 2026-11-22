package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.IndexCtx
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.settings.searchSettings
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Subtitles group the cards inside the Settings sections, and the search tells them (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w800dp-h2400dp", application = android.app.Application::class)
class SettingsSubheadersTest {
    @get:Rule val compose = createComposeRule()

    private fun open(section: SettingsSection, look: Look = Look()) = compose.setContent {
        StagebookTheme { SettingsScreen(SettingsUiState(loading = false, look = look), SettingsActions(), Modifier.fillMaxSize(), section = section, onLeave = {}, onMenu = {}) }
    }

    @Test fun `the Library groups its cards under songs, sites and copies`() {
        open(SettingsSection.LIBRARY)
        for (t in listOf("Músicas", "Importar de sites", "Cópias")) compose.onNodeWithText(t).performScrollTo().assertExists()
    }

    @Test fun `the Band section groups band, broadcast and computer`() {
        open(SettingsSection.BAND, Look(bandOn = true))
        for (t in listOf("Tocar com a banda", "Transmissão", "Computador")) {
            compose.onNode(androidx.compose.ui.test.hasText(t) and androidx.compose.ui.test.isHeading()).performScrollTo().assertExists()
        }
    }

    @Test fun `with the extras off the broadcast subtitle goes with its cards`() {
        open(SettingsSection.BAND, Look(extrasOn = false))
        compose.onNodeWithText("Transmissão").assertDoesNotExist()
    }

    @Test fun `the search shows the subtitle between the section and the card`() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val hit = searchSettings("backup", IndexCtx(), res).first { it.item.id == "backup_make" }
        assertEquals("Cópias", hit.subgroup)
    }
}
