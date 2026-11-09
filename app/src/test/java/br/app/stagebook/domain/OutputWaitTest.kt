package br.app.stagebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputWaitTest {
    @Test fun `a cable back within ten seconds carries on by itself`() {
        assertTrue(OutputWait.keepWaiting(waitedMs = 9_999, connected = false))
        assertFalse(OutputWait.givesUp(waitedMs = 9_999, connected = false))
        assertFalse(OutputWait.keepWaiting(waitedMs = 3_000, connected = true))
        assertFalse(OutputWait.givesUp(waitedMs = 3_000, connected = true))
    }

    @Test fun `past ten seconds without the output the wait ends`() {
        assertTrue(OutputWait.givesUp(waitedMs = 10_000, connected = false))
        assertFalse(OutputWait.keepWaiting(waitedMs = 10_000, connected = false))
    }

    @Test fun `the wait is the same as the idle stream's`() {
        org.junit.Assert.assertEquals(StreamSleep.IDLE_MS, OutputWait.GIVE_UP_MS)
    }

    @Test fun `a cable plugged back in time keeps the wait while it connects`() {
        assertTrue(OutputWait.keepWaiting(waitedMs = 11_000, connected = false, plugged = true))
        assertFalse(OutputWait.givesUp(waitedMs = 11_000, connected = false, plugged = true))
    }

    @Test fun `plugged in but never connected, the wait still ends`() {
        assertTrue(OutputWait.givesUp(waitedMs = OutputWait.PLUGGED_MAX_MS, connected = false, plugged = true))
        assertFalse(OutputWait.keepWaiting(waitedMs = OutputWait.PLUGGED_MAX_MS, connected = false, plugged = true))
    }

    @Test fun `an output that came back is never given up`() {
        assertFalse(OutputWait.givesUp(waitedMs = 60_000, connected = true))
    }
}
