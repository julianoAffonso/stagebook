package br.app.stagebook

import br.app.stagebook.data.hasAudioStreaming
import br.app.stagebook.data.isUsbAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a mixer from a pedal by its USB interfaces (device test B-34: a mixer can also have MIDI). */
class UsbAudioTest {
    // a USB mixer as Android lists it: audio control, two streaming interfaces (UAC2), MIDI streaming, vendor
    private val mixer = listOf(Triple(1, 1, 0x20), Triple(1, 2, 0x20), Triple(1, 2, 0x20), Triple(1, 1, 0), Triple(1, 3, 0), Triple(254, 1, 1))
    private val midiPedal = listOf(Triple(1, 1, 0), Triple(1, 3, 0))

    @Test fun `a USB mixer with MIDI is an audio interface, never a pedal`() {
        assertTrue(isUsbAudio(mixer))
        assertTrue(hasAudioStreaming(mixer))
    }

    @Test fun `a USB MIDI pedal is a pedal`() {
        assertFalse(isUsbAudio(midiPedal))
        assertFalse(hasAudioStreaming(midiPedal))
    }
}
