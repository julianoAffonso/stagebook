package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The player's level bars: rise at once, fall 24 dB/s, the peak mark holds 1 s. */
class MeterBallisticsTest {
    private val eps = 0.001f

    @Test fun `linear to dB, with silence and very quiet sound at the floor`() {
        assertEquals(0f, Meters.toDb(1f), eps)
        assertEquals(-6.0206f, Meters.toDb(0.5f), eps)
        assertEquals(Meters.FLOOR_DB, Meters.toDb(0f), eps)
        assertEquals(Meters.FLOOR_DB, Meters.toDb(-1f), eps)
        assertEquals(Meters.FLOOR_DB, Meters.toDb(1e-6f), eps)
        assertEquals(-20f, Meters.toDb(1e-6f, floor = -20f), eps)
    }

    @Test fun `the bar position goes from the floor to the top and is clamped`() {
        assertEquals(0f, Meters.fraction(Meters.FLOOR_DB), eps)
        assertEquals(1f, Meters.fraction(Meters.TOP_DB), eps)
        assertEquals(0f, Meters.fraction(-100f), eps)
        assertEquals(1f, Meters.fraction(12f), eps)
        assertEquals(0.5f, Meters.fraction((Meters.FLOOR_DB + Meters.TOP_DB) / 2), eps)
    }

    @Test fun `silent meters are at the floor`() {
        val m = Meters.silent(3)
        assertEquals(List(3) { Meters.FLOOR_DB }, m.levelDb)
        assertEquals(List(3) { Meters.FLOOR_DB }, m.holdDb)
    }

    @Test fun `the bar rises at once and falls 24 dB per second`() {
        val b = MeterBallistics(1)
        assertEquals(-6f, b.update(floatArrayOf(-6f), 66).levelDb[0], eps)
        // silence: half a second later the bar is 12 dB lower
        assertEquals(-18f, b.update(floatArrayOf(Meters.FLOOR_DB), 500).levelDb[0], eps)
        assertEquals(-30f, b.update(floatArrayOf(Meters.FLOOR_DB), 500).levelDb[0], eps)
        // never below the floor
        assertEquals(Meters.FLOOR_DB, b.update(floatArrayOf(Meters.FLOOR_DB), 5_000).levelDb[0], eps)
    }

    @Test fun `a louder peak while falling takes the bar up again`() {
        val b = MeterBallistics(1)
        b.update(floatArrayOf(-20f), 66)
        assertEquals(-3f, b.update(floatArrayOf(-3f), 66).levelDb[0], eps)
    }

    @Test fun `the peak mark holds one second and then falls with the bar`() {
        val b = MeterBallistics(1)
        b.update(floatArrayOf(0f), 66)
        // still held after 900 ms of silence
        var m = b.update(floatArrayOf(Meters.FLOOR_DB), 900)
        assertEquals(0f, m.holdDb[0], eps)
        // 1000 ms: hold time used up, the mark is still there for this frame
        m = b.update(floatArrayOf(Meters.FLOOR_DB), 100)
        assertEquals(0f, m.holdDb[0], eps)
        // then it falls 24 dB/s (not below the bar)
        m = b.update(floatArrayOf(Meters.FLOOR_DB), 250)
        assertEquals(-6f, m.holdDb[0], eps)
        assertEquals(true, m.holdDb[0] >= m.levelDb[0])
    }

    @Test fun `an equal or higher peak restarts the hold`() {
        val b = MeterBallistics(1)
        b.update(floatArrayOf(-10f), 66)
        b.update(floatArrayOf(Meters.FLOOR_DB), 900)
        b.update(floatArrayOf(-10f), 66)   // same level again: held for another second
        b.update(floatArrayOf(Meters.FLOOR_DB), 900)
        // without the restart the mark would already be falling here
        val m = b.update(floatArrayOf(Meters.FLOOR_DB), 100)
        assertEquals(-10f, m.holdDb[0], eps)
    }

    @Test fun `each channel moves on its own`() {
        val b = MeterBallistics(2)
        val m = b.update(floatArrayOf(-1f, -40f), 66)
        assertEquals(listOf(-1f, -40f), m.levelDb)
        assertEquals(listOf(-1f, -40f), m.holdDb)
    }
}
