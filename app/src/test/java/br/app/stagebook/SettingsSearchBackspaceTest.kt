package br.app.stagebook

import android.view.KeyEvent
import br.app.stagebook.ui.settings.settingsPedalTakes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings search took letters but not Backspace: "metro" stayed "metro" (device test, 0.1.1217). */
class SettingsSearchBackspaceTest {
    @Test fun `with the keyboard up the settings leave Backspace and the other keys to the text field`() {
        for (k in listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_LEFT)) {
            assertFalse("key $k", settingsPedalTakes(k, typing = true))
        }
    }

    @Test fun `without the keyboard the pedal test still takes the pedal keys, never the system's`() {
        assertTrue(settingsPedalTakes(KeyEvent.KEYCODE_DEL, typing = false))
        assertTrue(settingsPedalTakes(KeyEvent.KEYCODE_PAGE_DOWN, typing = false))
        assertFalse(settingsPedalTakes(KeyEvent.KEYCODE_BACK, typing = false))
        assertFalse(settingsPedalTakes(KeyEvent.KEYCODE_VOLUME_UP, typing = false))
    }
}
