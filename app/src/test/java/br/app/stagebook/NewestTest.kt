package br.app.stagebook

import br.app.stagebook.domain.Newest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead the band: a late answer to an older song never brings it back (deep audit, 30/10). */
class NewestTest {
    @Test fun `only the newest ticket may write back`() {
        val n = Newest()
        val a = n.ticket()
        val b = n.ticket()
        assertFalse(n.isNewest(a))
        assertTrue(n.isNewest(b))
    }

    @Test fun `song 5 answering after song 6 doesn't take the band back to 5`() = runTest {
        val newest = Newest()
        var current = 0
        // the same shape as BandConduct.send: ticket, slow network, write back only if still the newest
        suspend fun send(song: Int, networkMs: Long) {
            val t = newest.ticket()
            current = song
            delay(networkMs)
            if (newest.isNewest(t)) current = song
        }
        launch { send(5, networkMs = 20_000) }   // a follower on weak Wi-Fi
        delay(1_000)
        launch { send(6, networkMs = 500) }
        advanceUntilIdle()
        assertEquals(6, current)
    }
}
