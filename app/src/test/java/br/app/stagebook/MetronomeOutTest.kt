package br.app.stagebook

import br.app.stagebook.domain.MetronomeOut
import br.app.stagebook.domain.OutputInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the metronome can sound (user, 02/11): the device, or a USB output (pairs first, like 3-4 into the desk's 7/8). */
class MetronomeOutTest {
    @Test fun `without a USB interface only the device`() {
        assertEquals(listOf(MetronomeOut.DEVICE), MetronomeOut.options(OutputInfo()))
    }

    @Test fun `a 4 output interface offers its pairs then each output`() {
        val o = MetronomeOut.options(OutputInfo("usb:1397:050d", "USB Mixer", 4))
        assertEquals(
            listOf(MetronomeOut.DEVICE, MetronomeOut(0, true), MetronomeOut(2, true), MetronomeOut(0, false), MetronomeOut(1, false), MetronomeOut(2, false), MetronomeOut(3, false)),
            o,
        )
    }
}
