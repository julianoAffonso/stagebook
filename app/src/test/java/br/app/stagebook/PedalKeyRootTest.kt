package br.app.stagebook

import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.domain.PedalPress
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.PedalKeyRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pedal gets its keys before the keyboard and before Android's touch mode: right after a touch, the first arrow key
 * used to only leave touch mode and the first press did nothing (device test, 01/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PedalKeyRootTest {
    @Test fun `the root hands the key to the stage before the keyboard`() {
        val pedal = PedalInput()
        val got = mutableListOf<PedalPress>()
        pedal.register { got += it; true }
        val root = PedalKeyRoot(ApplicationProvider.getApplicationContext(), pedal)
        assertTrue(root.isFocusableInTouchMode)
        assertTrue(root.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, got.single().code)
    }

    @Test fun `with no screen listening the key goes on as usual`() {
        val root = PedalKeyRoot(ApplicationProvider.getApplicationContext(), PedalInput())
        assertFalse(root.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
    }
}
