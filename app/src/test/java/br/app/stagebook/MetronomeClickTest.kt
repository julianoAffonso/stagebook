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

    // count-in (user, 07/11): "1 . 2 . 1 2 3 4", so it isn't taken for the song's start
    @Test fun `the count-in leaves beats 2 and 4 silent, then clicks every beat`() {
        val all = ShortArray(600)
        MetronomeClick.render(all, 0, 50, normal, accent, skip = MetronomeClick.COUNT_IN_SKIP)
        val heard = (0 until 12).filter { all[it * 50] != 0.toShort() }
        assertEquals(listOf(0, 2, 4, 5, 6, 7, 8, 9, 10, 11), heard)
    }

    // user, 07/11: the two slow clicks of the count-in sound the same (the accent, like a bar start)
    @Test fun `the count-in's two slow clicks have the same accent tone`() {
        val all = ShortArray(600)
        MetronomeClick.render(all, 0, 50, normal, accent, skip = MetronomeClick.COUNT_IN_SKIP)
        fun at(beat: Int) = all[beat * 50 + 3]
        assertEquals(at(0), at(2))
        assertEquals(accent[3], at(0))
        assertEquals(accent[3], at(2))
        assertEquals(accent[3], at(4))   // the bar that follows keeps its accent
        assertEquals(normal[3], at(5))
        assertEquals(listOf(true, false, true, false, true, false, false, false, true),
            (0L..8L).map { MetronomeClick.accented(it, 4, MetronomeClick.COUNT_IN_SKIP) })
        assertEquals(false, MetronomeClick.accented(2, 4, 0))   // no count-in: only the bar's first beat
        assertEquals(true, MetronomeClick.accented(0, 4, 0))
    }

    @Test fun `the stage shows 1, 2, then 1 2 3 4, and nothing on the pauses`() =
        assertEquals(listOf(1, 0, 2, 0, 1, 2, 3, 4, 0), (0..8).map(MetronomeClick::countLabel))

    @Test fun `the volume scales the click`() {
        val full = MetronomeClick.click(false).maxOf { kotlin.math.abs(it.toInt()) }
        val half = MetronomeClick.click(false, volume = 50).maxOf { kotlin.math.abs(it.toInt()) }
        assertEquals(full / 2.0, half.toDouble(), full * 0.01)
        assertEquals(0, MetronomeClick.click(false, volume = 0).maxOf { kotlin.math.abs(it.toInt()) })
        assertEquals(100, br.app.stagebook.data.model.Look().metroVolume)
    }
}
