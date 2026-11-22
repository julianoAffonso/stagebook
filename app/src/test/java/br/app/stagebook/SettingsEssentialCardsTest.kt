package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.data.MidiState
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.ui.settings.AdvancedDetail
import br.app.stagebook.ui.settings.AdvancedUi
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.settings.pedalActions
import br.app.stagebook.ui.settings.shown
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Essential mode (T19), user 08/11: a function turned off leaves its cards and options in Settings too, not only the
 * stage. Each case turns one of the four switches off and checks that what belongs to it is gone (and stays with it on).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SettingsEssentialCardsTest {
    @get:Rule val compose = createComposeRule()

    private fun settings(look: Look, section: SettingsSection) = compose.setContent {
        StagebookTheme {
            SettingsScreen(
                SettingsUiState(loading = false, look = look, midi = MidiState(supported = true)),
                SettingsActions(),
                section = section,
            )
        }
    }

    @Test fun `the songs MIDI card is one of the extras`() {
        settings(Look(extrasOn = false), SettingsSection.PAGE)
        compose.onNodeWithText("Saída MIDI").assertDoesNotExist()
    }

    @Test fun `with the extras on the MIDI card is there`() {
        // the songs' MIDI goes out of the app: with Now Playing and the external screen (user, 22/11)
        settings(Look(), SettingsSection.BAND)
        compose.onNodeWithText("Saída MIDI").assertExists()
    }

    @Test fun `the pedal commands list Play and Pause only with the player on, unless the button already has it`() {
        assertTrue(PedalAction.PLAY_PAUSE in pedalActions(playerMode = true, current = PedalAction.NEXT))
        assertFalse(PedalAction.PLAY_PAUSE in pedalActions(playerMode = false, current = PedalAction.NEXT))
        // a button set to it before the player went off keeps showing what it does
        assertTrue(PedalAction.PLAY_PAUSE in pedalActions(playerMode = false, current = PedalAction.PLAY_PAUSE))
        // nothing else leaves
        assertEquals(PedalAction.entries.size - 1, pedalActions(playerMode = false, current = PedalAction.NEXT).size)
    }

    @Test fun `the play card of the Android settings is in the player group only with the player on`() {
        settings(Look(extrasOn = true, playerMode = false), SettingsSection.PLAYER)
        compose.onNodeWithText("Para tocar sem falhas").assertDoesNotExist()
    }

    @Test fun `the player group shows the play card with the player on`() {
        settings(Look(playerMode = true), SettingsSection.PLAYER)
        compose.onNodeWithText("Para tocar sem falhas").assertExists()
    }

    @Test fun `Device Advanced no longer carries the player card`() {
        compose.setContent { StagebookTheme { AdvancedDetail(AdvancedUi(canWrite = true), { _, _ -> }) } }
        compose.onNodeWithText("Para tocar sem falhas").assertDoesNotExist()
        compose.onNodeWithText("Bateria do aparelho").assertExists()
    }

    @Test fun `the library points to the band section only when it has band content`() {
        val link = "Os ajustes do arquivo substituem os daqui"
        settings(Look(bandOn = false, internetOn = false), SettingsSection.LIBRARY)
        compose.onNodeWithText(link, substring = true).assertDoesNotExist()
    }

    @Test fun `the library points to the band section with the band on`() {
        settings(Look(), SettingsSection.LIBRARY)
        compose.onNodeWithText("Os ajustes do arquivo substituem os daqui", substring = true).assertExists()
    }

    @Test fun `a group leaves the list when all its cards are gone`() {
        val off = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false)
        assertFalse(SettingsSection.PLAYER.shown(off))
        assertFalse(SettingsSection.BAND.shown(off))
        assertTrue(SettingsSection.PLAYER.shown(off.copy(playerMode = true)))
        assertTrue(SettingsSection.PLAYER.shown(off.copy(extrasOn = true)))
        assertTrue(SettingsSection.BAND.shown(off.copy(internetOn = true)))
    }
}
