package br.app.stagebook

import br.app.stagebook.domain.PendingLink
import br.app.stagebook.domain.PendingLink.Arrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingLinkTest {
    @Test fun `opens right away off stage`() {
        assertEquals(Arrival.OPEN, PendingLink().arrive("a", onStage = false, openCode = null))
    }

    @Test fun `holds on stage and releases on leaving`() {
        val link = PendingLink()
        assertEquals(Arrival.HELD, link.arrive("a", onStage = true, openCode = null))
        assertEquals("a", link.release(null))
        assertNull(link.release(null))
    }

    @Test fun `same code twice is held once`() {
        val link = PendingLink()
        assertEquals(Arrival.HELD, link.arrive("a", true, null))
        assertEquals(Arrival.IGNORED, link.arrive("a", true, null))
        assertEquals("a", link.release(null))
    }

    @Test fun `newer code replaces the older pending one`() {
        val link = PendingLink()
        link.arrive("a", true, null)
        assertEquals(Arrival.HELD, link.arrive("b", true, null))
        assertEquals("b", link.release(null))
    }

    @Test fun `code already open is not replaced`() {
        val link = PendingLink()
        assertEquals(Arrival.IGNORED, link.arrive("a", false, "a"))
        link.arrive("b", true, null)
        assertNull(link.release("b"))
    }

    @Test fun `a different code off stage opens and clears the pending one`() {
        val link = PendingLink()
        link.arrive("a", true, null)
        assertEquals(Arrival.OPEN, link.arrive("b", false, "a"))
        assertNull(link.release(null))
    }
}
