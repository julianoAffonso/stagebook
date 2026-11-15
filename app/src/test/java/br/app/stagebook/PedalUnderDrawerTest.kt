package br.app.stagebook

import android.view.KeyEvent
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.PedalPress
import br.app.stagebook.domain.pedalTakenByDrawer
import br.app.stagebook.ui.PedalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** With the library drawer open over the stage, the pedal does not turn the stage behind it (user, 15/11). */
class PedalUnderDrawerTest {
    private fun press(code: Int) = PedalPress(code = code, down = true)

    @Test fun `a button that turns pages on the stage is taken by the drawer`() {
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_PAGE_DOWN), emptyMap()))
    }

    @Test fun `a button the person set to something is taken too`() {
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_F5), mapOf(KeyEvent.KEYCODE_F5 to PedalAction.NEXT)))
    }

    @Test fun `a letter typed on a Bluetooth keyboard goes on to the search`() {
        assertFalse(pedalTakenByDrawer(press(KeyEvent.KEYCODE_A), emptyMap()))
    }

    // the handler the drawer registers while it is open is the last one: it gets the press before the stage's
    @Test fun `while the drawer's handler is there the stage's gets nothing, and gets it again when it goes`() {
        val input = PedalInput()
        var stage = 0
        input.register { stage++; true }
        val leave = input.register { pedalTakenByDrawer(it, emptyMap()) }
        assertTrue(input.dispatch(press(KeyEvent.KEYCODE_PAGE_DOWN)))
        assertEquals("the page does not turn behind the drawer", 0, stage)
        leave()
        assertTrue(input.dispatch(press(KeyEvent.KEYCODE_PAGE_DOWN)))
        assertEquals(1, stage)
    }
}
