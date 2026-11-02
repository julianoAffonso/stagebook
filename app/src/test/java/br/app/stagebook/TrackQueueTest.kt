package br.app.stagebook

import br.app.stagebook.domain.TrackQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live, changing songs never stops the track playing; the song on screen takes over when it stops (device test, 02/11). */
class TrackQueueTest {
    @Test fun `stopped, the song on screen loads its track`() {
        val q = TrackQueue<String>()
        assertTrue(q.open("a.ogg"))
        assertEquals("a.ogg", q.wanted)
        assertFalse(q.open("a.ogg"))
    }

    @Test fun `playing, going back a song keeps the track and loads the new one only when it stops`() {
        val q = TrackQueue<String>()
        q.open("b.ogg")
        q.started()
        assertFalse(q.open("a.ogg"))
        assertEquals("b.ogg", q.wanted)
        assertTrue(q.stopped())
        assertEquals("a.ogg", q.wanted)
        assertFalse(q.stopped())
    }

    @Test fun `playing, going away and coming back changes nothing`() {
        val q = TrackQueue<String>()
        q.open("b.ogg")
        q.started()
        q.open(null)
        q.open("b.ogg")
        assertFalse(q.stopped())
        assertEquals("b.ogg", q.wanted)
    }

    @Test fun `playing, a song without a track takes over when it stops`() {
        val q = TrackQueue<String>()
        q.open("b.ogg")
        q.started()
        q.open(null)
        assertTrue(q.stopped())
        assertEquals(null, q.wanted)
    }

    // a failed load counts as stopped: the song changed meanwhile gets its own track (audit 02/11)
    @Test fun `after a stop, song changes load right away again`() {
        val q = TrackQueue<String>()
        q.open("a.ogg")
        q.started()
        q.open("b.ogg")
        q.stopped()
        assertEquals("b.ogg", q.wanted)
        assertTrue(q.open("c.ogg"))
        assertEquals("c.ogg", q.wanted)
    }
}
