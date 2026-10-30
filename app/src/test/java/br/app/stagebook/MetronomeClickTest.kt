package br.app.stagebook

import br.app.stagebook.domain.MetronomeClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Metronome sound (T33d): each click on the exact sample, without drifting, and beat 1 of the bar higher. */
class MetronomeClickTest {
    private val normal = ShortArray(10) { 100 }
    private val accent = ShortArray(10) { 200 }

    @Test fun `120 BPM at 48 thousand samples - one beat every 24 thousand samples`() =
        assertEquals(24_000L, MetronomeClick.samplesPerBeat(120))

    @Test fun `clicks land on the right sample even across chunks`() {
        val spb = 50L
        val all = ShortArray(400)
        // in chunks of 37 samples (the way the audio writes), joining everything
        var pos = 0L
        while (pos < all.size) {
            val chunk = ShortArray(minOf(37, all.size - pos.toInt()))
            MetronomeClick.render(chunk, pos, spb, normal, accent)
            chunk.copyInto(all, pos.toInt())
            pos += chunk.size
        }
        for (beat in 0 until 8) {
            val at = beat * 50
            assertEquals(if (beat % 4 == 0) 200.toShort() else 100.toShort(), all[at])
            assertEquals(0.toShort(), all[at + 10])   // the click ended: silence until the next one
        }
    }

    @Test fun `the high click and the normal one are different and short`() {
        val a = MetronomeClick.click(true)
        val n = MetronomeClick.click(false)
        assertTrue(a.size < MetronomeClick.RATE / 10)
        assertTrue(!a.contentEquals(n))
    }
}
