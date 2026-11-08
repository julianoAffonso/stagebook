package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
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
        settings(Look(extrasOn = false), SettingsSection.PEDAL)
        compose.onNodeWithText("Saída MIDI").assertDoesNotExist()
    }

    @Test fun `with the extras on the MIDI card is there`() {
        settings(Look(), SettingsSection.PEDAL)
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

    @Test fun `Advanced keeps the player card only with the player on`() {
        compose.setContent { StagebookTheme { AdvancedDetail(AdvancedUi(), { _, _ -> }, {}, playerMode = false) } }
        compose.onNodeWithText("Para tocar sem falhas").assertDoesNotExist()
    }

    @Test fun `Advanced shows the player card with the player on`() {
        compose.setContent { StagebookTheme { AdvancedDetail(AdvancedUi(), { _, _ -> }, {}, playerMode = true) } }
        compose.onNodeWithText("Para tocar sem falhas").assertExists()
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

    @Test fun `a section leaves the list when all its parts are off`() {
        val off = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false)
        assertFalse(SettingsSection.METRONOME.shown(off))
        assertFalse(SettingsSection.BAND.shown(off))
        assertTrue(SettingsSection.METRONOME.shown(off.copy(playerMode = true)))
        assertTrue(SettingsSection.METRONOME.shown(off.copy(extrasOn = true)))
        assertTrue(SettingsSection.BAND.shown(off.copy(internetOn = true)))
    }
}
