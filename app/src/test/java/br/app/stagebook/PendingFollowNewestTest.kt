package br.app.stagebook

import br.app.stagebook.domain.FollowWait
import br.app.stagebook.domain.PendingFollow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What waits for the follower opens as where the band is NOW, never dropped for being out of date (v0.1.1134). */
class PendingFollowNewestTest {
    @Test fun `released, it opens where the band is now even if it moved without a new arrival`() {
        val p = PendingFollow()
        p.arrive("b", FollowWait.MENU)
        assertEquals("c", p.release(null, now = "c"))
        assertNull(p.waiting)
    }

    @Test fun `released with the band on nothing this device shows, nothing opens`() {
        val p = PendingFollow()
        p.arrive("b", FollowWait.MENU)
        assertNull(p.release(null, now = null))
        assertNull(p.waiting)
    }

    @Test fun `the band moving on changes what waits and keeps the reason`() {
        val p = PendingFollow()
        p.arrive("b", FollowWait.MENU)
        p.moved("c")
        assertEquals("c", p.waiting)
        assertEquals(FollowWait.MENU, p.reason)
        p.moved(null)
        assertNull(p.waiting)
    }

    @Test fun `nothing waiting, nothing opens`() {
        assertNull(PendingFollow().release(null, now = "x"))
    }
}
