package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A file tapped outside the app while the stage or the editor is open: only the latest one waits. */
class PendingFileTest {
    @Test fun `with nothing open the file opens and nothing waits`() {
        val p = PendingFile<String>()
        assertEquals(PendingFile.Arrival.OPEN, p.arrive("a", onStage = false))
        assertNull(p.pending)
        assertNull(p.release())
    }

    @Test fun `only the latest held file waits`() {
        val p = PendingFile<String>()
        p.arrive("a", onStage = true)
        p.arrive("b", onStage = true)
        assertEquals("b", p.release())
        assertNull(p.release())   // released once
    }

    @Test fun `a rejected file doesn't drop the one waiting`() {
        val p = PendingFile<String>()
        p.arrive("a", onStage = true)
        assertEquals(PendingFile.Arrival.REJECTED, p.arrive(null, onStage = true))
        assertEquals(PendingFile.Arrival.REJECTED, p.arrive(null, onStage = false))
        assertEquals("a", p.release())
    }

    @Test fun `a file opened after leaving replaces an older one still waiting`() {
        val p = PendingFile<String>()
        p.arrive("old", onStage = true)
        // the stage was left and a new file came before the release ran: the new one opens, the old one is dropped
        assertEquals(PendingFile.Arrival.OPEN, p.arrive("new", onStage = false))
        assertNull(p.release())
    }
}
