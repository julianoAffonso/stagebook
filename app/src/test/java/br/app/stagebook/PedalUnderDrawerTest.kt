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

    @Test fun `typing in the menu's search, Backspace, Space, Enter and the letters go to the field`() {
        // Backspace, Space and Enter turn pages on the stage by default, and N and P change songs
        listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_5).forEach {
            assertFalse("key $it", pedalTakenByDrawer(press(it), emptyMap(), typing = true))
        }
        // with nothing being typed they are the pedal's, as before: the stage behind must not turn
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_DEL), emptyMap()))
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_SPACE), emptyMap()))
    }

    @Test fun `typing, a pedal's own keys are still taken, so the stage behind does not turn`() {
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_PAGE_DOWN), emptyMap(), typing = true))
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_DPAD_DOWN), emptyMap(), typing = true))
        assertTrue(pedalTakenByDrawer(press(KeyEvent.KEYCODE_MEDIA_NEXT), emptyMap(), typing = true))
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
