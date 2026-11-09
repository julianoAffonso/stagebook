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

    @Test fun `an output that came back is never given up`() {
        assertFalse(OutputWait.givesUp(waitedMs = 60_000, connected = true))
    }
}
