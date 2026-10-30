package br.app.stagebook

import br.app.stagebook.domain.Measure
import br.app.stagebook.domain.MixChannel
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.Mixer
import br.app.stagebook.domain.NormMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MixerTest {
    private fun route(p: br.app.stagebook.domain.MixPlan, out: Int, lane: Int) = p.route[out * MixerConfig.MAX + lane]

    @Test fun `with no setup each channel goes to the output with the same number - no gain or EQ`() {
        val p = Mixer.plan(MixerConfig(channels = 4), lanes = 4)
        for (c in 0 until 4) {
            assertEquals(1f, p.gains[c])
            assertEquals(1f, p.norms[c])
            for (o in 0 until 8) assertEquals(if (o == c) 1f else 0f, route(p, o, c))
        }
        assertEquals(0, p.eqMask)
    }

    @Test fun `stereo pair follows the first channel - fader - EQ and normalization together`() {
        val cfg = MixerConfig(channels = 4)
            .withStrip(0) { it.copy(stereo = true, gainDb = -6f, eq = true, norm = NormMode.VOLUME) }
            .withStrip(1) { it.copy(gainDb = 6f) }   // ignored: follows channel 1
        val p = Mixer.plan(cfg, 4, mapOf(0 to Measure(lufs = -20f, peak = 0.5f)))
        assertEquals(listOf(0..1, 2..2, 3..3), cfg.groups(4))
        assertEquals(p.gains[0], p.gains[1])
        assertEquals(Mixer.lin(-6f), p.gains[0], 1e-6f)
        assertEquals(0b11, p.eqMask)
        assertEquals(Mixer.lin(4f), p.norms[0], 1e-4f)   // -20 LUFS → -16: +4 dB (the -6 dB peak allows it)
        assertEquals(p.norms[0], p.norms[1])
        assertEquals(1f, route(p, 0, 0)); assertEquals(1f, route(p, 1, 1))
    }

    @Test fun `loudness never goes over the peak ceiling and peak goes to -6 dB`() {
        assertEquals(-1f - Mixer.db(0.9f), Mixer.normDb(NormMode.VOLUME, Measure(-30f, 0.9f)), 1e-4f)
        assertEquals(-6f - Mixer.db(0.25f), Mixer.normDb(NormMode.PEAK, Measure(-30f, 0.25f)), 1e-4f)
        assertEquals(0f, Mixer.normDb(NormMode.VOLUME, null))
        assertEquals(Mixer.MAX_BOOST_DB, Mixer.normDb(NormMode.PEAK, Measure(-70f, 0.001f)))
    }

    @Test fun `center - mono goes the same to both sides of the output pair and a pair becomes a sum in the middle`() {
        val mono = MixerConfig(channels = 2).withStrip(1) { it.copy(center = true) }
        val p = Mixer.plan(mono, 2)
        assertEquals(route(p, 0, 1), route(p, 1, 1))
        assertEquals(0.7071f, route(p, 0, 1), 1e-3f)
        assertEquals(1f, route(p, 0, 0)); assertEquals(0f, route(p, 1, 0))

        val pair = MixerConfig(channels = 2).withStrip(0) { it.copy(stereo = true, center = true) }
        val q = Mixer.plan(pair, 2)
        for (o in 0..1) for (c in 0..1) assertEquals(0.5f, route(q, o, c))
    }

    @Test fun `backing on one side and click on the other - separate - mute and chosen output`() {
        val cfg = MixerConfig(channels = 4)
            .withStrip(0) { it.copy(name = "Backing", stereo = true) }
            .withStrip(2) { it.copy(name = "Click", stereo = true) }
            .withOutput(2, 6)
            .withStrip(3) { it.copy(on = false) }   // inside the pair: follows channel 3
        val p = Mixer.plan(cfg, 4)
        assertEquals(1f, route(p, 6, 2)); assertEquals(1f, route(p, 7, 3))
        assertEquals(0f, route(p, 2, 2))
        val muted = Mixer.plan(cfg.withStrip(2) { it.copy(on = false) }, 4)
        assertEquals(0f, muted.gains[2]); assertEquals(0f, muted.gains[3])
        assertEquals(0f, Mixer.plan(cfg.withStrip(0) { it.copy(gainDb = MixerConfig.MIN_DB) }, 4).gains[0])
    }

    @Test fun `each interface keeps its own routing`() {
        val usb = MixerConfig(channels = 2, output = br.app.stagebook.domain.OutputInfo("usb:1234:5678", "Interface USB", 10)).withOutput(1, 6)
        assertEquals(1f, route(Mixer.plan(usb, 2), 6, 1))
        // on the device output, channel 2 stays on output 2 (right): what was chosen for the USB interface doesn't
        // apply there
        val device = usb.copy(output = br.app.stagebook.domain.OutputInfo())
        assertEquals(1f, route(Mixer.plan(device, 2), 1, 1))
        assertEquals(6, usb.outputOf(1))
        assertEquals(1, device.outputOf(1))
    }

    @Test fun `file with fewer channels than the mixer uses only the ones it has`() {
        val cfg = MixerConfig(channels = 8).withStrip(0) { it.copy(stereo = true) }
        assertEquals(listOf(0..0), cfg.groups(1))   // a pair without the second channel stays alone
    }
}
