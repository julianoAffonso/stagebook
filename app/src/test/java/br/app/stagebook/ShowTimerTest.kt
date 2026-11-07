package br.app.stagebook

import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageClockMode
import br.app.stagebook.domain.ShowTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stage clock's show counter (user, 07/11): show time and time left, the length in half hours. */
class ShowTimerTest {
    private val minute = 60_000L
    private val t0 = 1_000_000_000_000L

    @Test fun `a tap goes clock, show time, time left, clock`() {
        assertEquals(StageClockMode.ELAPSED, ShowTimer.next(StageClockMode.CLOCK))
        assertEquals(StageClockMode.LEFT, ShowTimer.next(StageClockMode.ELAPSED))
        assertEquals(StageClockMode.CLOCK, ShowTimer.next(StageClockMode.LEFT))
        assertEquals(StageClockMode.CLOCK, Look().clockMode)
    }

    @Test fun `hours and minutes`() {
        assertEquals("0:00", ShowTimer.format(0))
        assertEquals("1:05", ShowTimer.format(65))
        assertEquals("−0:05", ShowTimer.format(-5))
        assertEquals(65L, ShowTimer.elapsedMinutes(t0, t0 + 65 * minute + 59_000))
    }

    @Test fun `time left counts down from the length and goes past it`() {
        assertEquals(120L, ShowTimer.leftMinutes(t0, t0, 120))
        assertEquals(-5L, ShowTimer.leftMinutes(t0, t0 + 125 * minute, 120))
        assertFalse(ShowTimer.warn(t0, t0 + 110 * minute, 120))
        assertTrue(ShowTimer.warn(t0, t0 + 111 * minute, 120))
    }

    @Test fun `the length goes in half hours, within limits`() {
        assertEquals(150, ShowTimer.step(120, up = true))
        assertEquals(90, ShowTimer.step(120, up = false))
        assertEquals(ShowTimer.MIN_MINUTES, ShowTimer.step(ShowTimer.MIN_MINUTES, up = false))
        assertEquals(ShowTimer.MAX_MINUTES, ShowTimer.step(ShowTimer.MAX_MINUTES, up = true))
        assertEquals("2h", ShowTimer.lengthText(120))
        assertEquals("2h30", ShowTimer.lengthText(150))
        assertEquals(120, Look().showMinutes)
    }

    @Test fun `the show running keeps its start, yesterday's starts again`() {
        assertEquals(t0, ShowTimer.startFor(t0, t0 + 90 * minute))
        assertEquals(t0 + 13 * 60 * minute, ShowTimer.startFor(t0, t0 + 13 * 60 * minute))
        assertEquals(t0, ShowTimer.startFor(0L, t0))
    }

    @Test fun `the counter turns its minute from the start`() {
        assertEquals(30_000L, ShowTimer.untilNextMinute(t0, t0 + 30_000L))
        assertEquals(minute, ShowTimer.untilNextMinute(t0, t0 + 2 * minute))
    }
}
