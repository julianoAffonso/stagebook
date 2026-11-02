package br.app.stagebook

import br.app.stagebook.domain.ignoringShortDrops
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The USB "ready" seen by the stage ignores short drops (audit 02/11 #3: the clock reconnect restarted the metronome). */
class SteadyFlowTest {
    @Test fun `a short drop is ignored and a long one passes`() = runTest {
        val ready = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        val job = launch { ready.ignoringShortDrops(3_000).toList(seen) }
        runCurrent()
        ready.value = false; advanceTimeBy(1_000); ready.value = true; runCurrent()   // the clock reconnect
        assertEquals(listOf(true), seen)
        ready.value = false; advanceTimeBy(3_500); runCurrent()                        // the cable came out
        assertEquals(listOf(true, false), seen)
        job.cancel()
    }
}
