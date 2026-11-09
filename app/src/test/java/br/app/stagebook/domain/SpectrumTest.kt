package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The spectrum bands and the equalizer curve drawn over them (same math as the engine's filters). */
class SpectrumTest {
    private fun band(freq: Double) = Spectrum.centers.indices.minBy { abs(Spectrum.centers[it] - freq) }

    @Test fun `band centers go up from about 30 Hz to about 16 kHz`() {
        assertEquals(Spectrum.BINS, Spectrum.centers.size)
        assertTrue(Spectrum.centers.toList().zipWithNext().all { (a, b) -> b > a })
        assertTrue(Spectrum.centers.first() in 30.0..35.0)
        assertTrue(Spectrum.centers.last() in 14_000.0..16_000.0)
    }

    @Test fun `a flat EQ draws a flat line at 0 dB`() {
        val r = Spectrum.eqResponseDb(List(5) { 0f })
        assertEquals(Spectrum.BINS, r.size)
        r.forEach { assertEquals(0f, it, 0.01f) }
    }

    @Test fun `a bell boosts around its own frequency and leaves the far bands alone`() {
        val r = Spectrum.eqResponseDb(listOf(0f, 0f, 6f, 0f, 0f))   // +6 dB at 1 kHz
        assertEquals(6f, r[band(1000.0)], 0.6f)
        assertTrue(abs(r[band(40.0)]) < 0.5f)
        assertTrue(abs(r[band(14_000.0)]) < 0.5f)
    }

    @Test fun `the low shelf lifts the bass and the high shelf the treble`() {
        val low = Spectrum.eqResponseDb(listOf(6f, 0f, 0f, 0f, 0f))
        assertTrue(low[band(35.0)] > 4.5f)
        assertTrue(abs(low[band(10_000.0)]) < 0.3f)
        val high = Spectrum.eqResponseDb(listOf(0f, 0f, 0f, 0f, -6f))
        assertTrue(high[band(15_000.0)] < -4.5f)
        assertTrue(abs(high[band(100.0)]) < 0.3f)
    }

    @Test fun `a cut mirrors a boost`() {
        val up = Spectrum.eqResponseDb(listOf(3f, -2f, 5f, 1f, -4f))
        val down = Spectrum.eqResponseDb(listOf(-3f, 2f, -5f, -1f, 4f))
        up.indices.forEach { assertEquals(-up[it], down[it], 0.01f) }
    }

    @Test fun `each fader frequency sits in the middle of its fifth of the width`() {
        Spectrum.EQ_FREQ.forEachIndexed { i, f -> assertEquals((i + 0.5f) / 5, Spectrum.x(f), 0.0001f) }
        assertTrue(Spectrum.x(20.0) < Spectrum.x(80.0))
        assertTrue(Spectrum.x(16_000.0) > Spectrum.x(10_000.0))
        assertTrue(Spectrum.x(500.0) > Spectrum.x(250.0) && Spectrum.x(500.0) < Spectrum.x(1000.0))
    }

    @Test fun `the spectrum bar position is clamped`() {
        assertEquals(0f, Spectrum.fraction(-200f), 0f)
        assertEquals(1f, Spectrum.fraction(10f), 0f)
        assertEquals(0.5f, Spectrum.fraction(Spectrum.FLOOR_DB / 2), 0.0001f)
    }
}
