package br.app.stagebook

import androidx.compose.ui.test.getBoundsInRoot
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → Pedal: where the songs' MIDI goes (user, 07/11), and the ways to add a pedal. */
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

    // 08/11: to send MIDI over Bluetooth the person had to "look for a pedal"; the card is now for pedals and MIDI devices,
    // and the output says where to connect one when none is listed
    @Test fun `with no Bluetooth device listed the output says where to connect one`() {
        show(Look(), listOf(MidiOutput("1:Keystation", "Keystation", false)))
        compose.onNodeWithText("Não achou o seu aparelho Bluetooth? Conecte-o em Conectar pedal ou aparelho MIDI.", substring = true).performScrollTo()
        compose.onNodeWithText("Conectar pedal ou aparelho MIDI").assertExists()
    }

    @Test fun `with a Bluetooth device listed the hint is gone`() {
        show(Look(), listOf(MidiOutput("2:WIDI", "WIDI", true)))
        compose.onNodeWithText("Nenhum lugar").performScrollTo()
        compose.onNodeWithText("Não achou o seu aparelho Bluetooth?", substring = true).assertDoesNotExist()
    }

    @Test fun `the chosen device away still shows, as not connected`() {
        show(Look(midiOut = "1:Keystation", midiOutName = "Keystation"), emptyList())
        compose.onNodeWithText("Keystation, desconectado").performScrollTo()
    }

    // S26 and Chuwi, 07/11: a blank band between "Pedal Bluetooth comum" and "Pedal MIDI Bluetooth"
    @Test fun `the ways to add a pedal sit one right under the other`() {
        show(Look(), emptyList())
        val common = compose.onNodeWithText("Pedal Bluetooth comum").getBoundsInRoot()
        val midi = compose.onNodeWithText("Aparelho MIDI Bluetooth").getBoundsInRoot()
        assertEquals(common.bottom.value, midi.top.value, 0.5f)
        assertTrue(common.bottom - common.top <= br.app.stagebook.ui.theme.Sizes.touch)
    }
}
