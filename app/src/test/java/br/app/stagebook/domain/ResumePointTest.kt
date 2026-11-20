package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePointTest {
    @Test fun `a mark from minutes ago is fresh, an old or missing one isn't`() {
        assertTrue(ResumePoint.fresh(savedAt = 1_000, now = 1_000 + 60_000))
        assertTrue(ResumePoint.fresh(savedAt = 1_000, now = 1_000 + ResumePoint.FRESH_MS))
        assertFalse(ResumePoint.fresh(savedAt = 1_000, now = 1_000 + ResumePoint.FRESH_MS + 1))
        assertFalse(ResumePoint.fresh(savedAt = 0, now = 5_000))
        assertFalse(ResumePoint.fresh(savedAt = 9_000, now = 5_000))   // clock went back: not trusted
    }

    @Test fun `the mark counts only when the app was replaced after it`() {
        assertTrue(ResumePoint.replacedSince(savedAt = 5_000, updatedAt = 5_000))
        assertTrue(ResumePoint.replacedSince(savedAt = 5_000, updatedAt = 9_000))
        assertFalse(ResumePoint.replacedSince(savedAt = 5_000, updatedAt = 4_999))   // the install that was already here
        assertFalse(ResumePoint.replacedSince(savedAt = 5_000, updatedAt = 0))
    }

    @Test fun `a route is filled with its arguments`() {
        assertEquals("p.SetlistDetail/abc%20d", ResumePoint.fill("p.SetlistDetail/{setId}", mapOf("setId" to "abc d")) { it.replace(" ", "%20") })
        assertEquals("p.Stage?setId=s1&songId=x&start=2",
            ResumePoint.fill("p.Stage?setId={setId}&songId={songId}&start={start}", mapOf("setId" to "s1", "songId" to "x", "start" to "2")) { it })
    }

    @Test fun `a route with a missing or null argument isn't used`() {
        assertNull(ResumePoint.fill("p.Stage?setId={setId}&songId={songId}", mapOf("setId" to "s1", "songId" to null)) { it })
        assertNull(ResumePoint.fill("p.SetlistDetail/{setId}", emptyMap()) { it })
    }

    // user, 19/11: the update window with the menu open over a Settings page brought the app back on that page
    @Test fun `with the menu open the update comes back to the stage, not to the screen behind the menu`() {
        val page = "p.Settings?section=LYRICS&alone=true&fromMenu=true&item="
        assertEquals("home", ResumePoint.place(page, menuOpen = true, home = "home"))
        assertEquals("home", ResumePoint.place("p.Stage?songId=x", menuOpen = true, home = "home"))
    }

    @Test fun `with the menu closed the update comes back to the screen in use`() {
        val page = "p.Settings?section=LYRICS&alone=true&fromMenu=true&item="
        assertEquals(page, ResumePoint.place(page, menuOpen = false, home = "home"))
        assertEquals("home", ResumePoint.place(null, menuOpen = false, home = "home"))
    }
}
