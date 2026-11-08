package br.app.stagebook

import br.app.stagebook.domain.SerialQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The MIDI output's queue (audit 07/11): messages go out in the order they were sent, and the port is let go when idle. */
@OptIn(ExperimentalCoroutinesApi::class)
class SerialQueueTest {
    @Test fun `what is offered first is handled first, even when each takes a different time`() = runTest {
        val done = mutableListOf<Int>()
        val q = SerialQueue<Int>(CoroutineScope(StandardTestDispatcher(testScheduler)), 1000, { n -> kotlinx.coroutines.delay(if (n == 1) 50L else 1L); done += n }, {})
        q.offer(1); q.offer(2); q.offer(3)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), done)
    }

    @Test fun `idle after the last item once, and not before`() = runTest {
        var idle = 0
        val q = SerialQueue<Int>(CoroutineScope(StandardTestDispatcher(testScheduler)), 1000, {}, { idle++ })
        q.offer(1)
        advanceTimeBy(900)
        assertEquals(0, idle)
        advanceTimeBy(200)
        assertEquals(1, idle)
        advanceTimeBy(5000)
        assertEquals(1, idle)   // letting go happens once: nothing keeps ticking while nothing is sent
        q.offer(2)
        advanceTimeBy(1100)
        assertEquals(2, idle)
    }
}
