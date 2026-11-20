package br.app.stagebook

import br.app.stagebook.ui.drawer.DrawerGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The screens in the menu each add and remove their own question; none wipes another's (audit 20/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DrawerGuardTest {
    @Test fun `the latest question is asked first, and removing one keeps the other`() {
        val guard = DrawerGuard()
        val asked = mutableListOf<String>()
        val editing: () -> Boolean = { asked += "setlist"; true }
        val filter: () -> Boolean = { asked += "filter"; false }
        guard.add(editing)
        guard.add(filter)
        assertEquals(true, guard.asks?.invoke())
        assertEquals(listOf("filter", "setlist"), asked)
        guard.remove(filter)
        assertEquals(true, guard.asks?.invoke())
        guard.remove(editing)
        assertNull(guard.asks)
    }

    @Test fun `the dimmed area is held for a moment only`() {
        val guard = DrawerGuard()
        assertFalse(guard.scrimHeld())
        guard.holdScrim(500)
        assertTrue(guard.scrimHeld())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(600))
        assertFalse(guard.scrimHeld())
    }
}
