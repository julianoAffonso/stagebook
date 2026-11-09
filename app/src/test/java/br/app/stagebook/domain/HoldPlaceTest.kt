package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldPlaceTest {
    @Test fun `the stage holds first, then the editor, else nothing holds`() {
        assertEquals(HoldPlace.STAGE, HoldPlace.of(onStage = true, editing = true))
        assertEquals(HoldPlace.STAGE, HoldPlace.of(onStage = true, editing = false))
        assertEquals(HoldPlace.EDITOR, HoldPlace.of(onStage = false, editing = true))
        assertEquals(HoldPlace.NONE, HoldPlace.of(onStage = false, editing = false))
    }

    @Test fun `a file and a link wait in the editor and open on leaving it`() {
        val file = PendingFile<String>()
        val link = PendingLink()
        val place = HoldPlace.of(onStage = false, editing = true)
        assertTrue(place.holds)
        assertEquals(PendingFile.Arrival.HELD, file.arrive("a.stagebook", place.holds))
        assertEquals(PendingLink.Arrival.HELD, link.arrive("abc", place.holds, null))
        assertFalse(HoldPlace.of(onStage = false, editing = false).holds)
        assertEquals("a.stagebook", file.release())
        assertEquals("abc", link.release(null))
    }
}
