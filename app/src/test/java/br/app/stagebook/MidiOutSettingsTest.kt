package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.data.MidiOutput
import br.app.stagebook.data.MidiState
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → Pedal: where the songs' MIDI goes (user, 07/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class MidiOutSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val chosen = mutableListOf<MidiOutput?>()
    private var opened = 0

    private fun show(look: Look, outputs: List<MidiOutput>) = compose.setContent {
        StagebookTheme {
            SettingsScreen(
                SettingsUiState(loading = false, look = look, midi = MidiState(supported = true)),
                SettingsActions(midiOutputs = outputs, onMidiOutputsOpen = { opened++ }, onMidiOut = { chosen += it }),
                section = SettingsSection.PEDAL,
            )
        }
    }

    @Test fun `nothing chosen says nowhere, and the list is read when it opens`() {
        show(Look(), listOf(MidiOutput("2:WIDI", "WIDI", true)))
        compose.onNodeWithText("Nenhum lugar").performScrollTo().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("WIDI").performClick()
        assertEquals("2:WIDI", chosen.single()?.key)
    }

    @Test fun `the chosen device away still shows, as not connected`() {
        show(Look(midiOut = "1:Keystation", midiOutName = "Keystation"), emptyList())
        compose.onNodeWithText("Keystation, desconectado").performScrollTo()
    }
}
