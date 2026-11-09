package br.app.stagebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputWaitTest {
    @Test fun `a cable back within the minute carries on by itself`() {
        assertTrue(OutputWait.keepWaiting(waitedMs = 59_999, connected = false))
        assertFalse(OutputWait.givesUp(waitedMs = 59_999, connected = false))
        assertFalse(OutputWait.keepWaiting(waitedMs = 3_000, connected = true))
        assertFalse(OutputWait.givesUp(waitedMs = 3_000, connected = true))
    }

    @Test fun `past the minute without the output the wait ends`() {
        assertTrue(OutputWait.givesUp(waitedMs = 60_000, connected = false))
        assertFalse(OutputWait.keepWaiting(waitedMs = 60_000, connected = false))
    }

    @Test fun `a cable plugged back in time keeps the wait while it connects`() {
        assertTrue(OutputWait.keepWaiting(waitedMs = 61_000, connected = false, plugged = true))
        assertFalse(OutputWait.givesUp(waitedMs = 61_000, connected = false, plugged = true))
    }

    @Test fun `plugged in but never connected, the wait still ends`() {
        assertTrue(OutputWait.givesUp(waitedMs = OutputWait.PLUGGED_MAX_MS, connected = false, plugged = true))
        assertFalse(OutputWait.keepWaiting(waitedMs = OutputWait.PLUGGED_MAX_MS, connected = false, plugged = true))
    }

    @Test fun `an output that came back is never given up`() {
        assertFalse(OutputWait.givesUp(waitedMs = 120_000, connected = true))
    }
}
