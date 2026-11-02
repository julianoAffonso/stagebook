package br.app.stagebook

import br.app.stagebook.ui.stage.autoScrollNanos
import br.app.stagebook.ui.stage.metroBeatAt
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stage clocks (audit 01/11): the metronome and auto-scroll follow the time, they don't catch up after a pause. */
class StageClockTest {
    private val period = 500_000_000L   // 120 BPM

    @Test fun `the beat comes from the clock - after 5 minutes off it is the current beat, not the next one`() {
        assertEquals(0, metroBeatAt(0, period))
        assertEquals(1, metroBeatAt(period, period))
        assertEquals(1, metroBeatAt(period - 10_000_000L, period))   // a frame a little early still lands the beat
        assertEquals(0, metroBeatAt(period - 100_000_000L, period))
        assertEquals(600, metroBeatAt(300_000_000_000L, period))
    }

    @Test fun `auto-scroll moves at most 100 ms of text after a long gap`() {
        assertEquals(16_000_000L, autoScrollNanos(16_000_000L))
        assertEquals(100_000_000L, autoScrollNanos(5_000_000_000L))
        assertEquals(0L, autoScrollNanos(-1))
    }
}
