package br.app.stagebook

import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.PedalPress
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalHandler
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.PedalKeyRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first pedal press on a stage opened after typing somewhere (the search, the editor): the text field that had the
 * focus goes away and, in touch mode, Android leaves the window with nothing focused. The first arrow or page key then
 * only leaves touch mode and never reaches the pedal (two devices, 13/11: the first press after "Lead the band" did
 * nothing). The keys go through the window's own path here, the one a real key takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class PedalFirstPressTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var typing by mutableStateOf(true)
    private val pedal = PedalInput()
    private val got = mutableListOf<PedalPress>()
    private lateinit var root: PedalKeyRoot

    // a screen with a text field, then the stage (whoever listens to the pedal), as the app's window holds them
    private fun open() {
        compose.runOnUiThread {
            root = PedalKeyRoot(compose.activity, pedal).apply {
                addView(ComposeView(context).apply {
                    setContent {
                        CompositionLocalProvider(LocalPedalInput provides pedal) {
                            if (typing) BasicTextField("abc", {}, Modifier.testTag("field")) else PedalHandler { got += it; true }
                        }
                    }
                })
            }
            compose.activity.setContentView(root)
        }
        compose.waitForIdle()
        // a finger on the screen: the window is in touch mode, as on a device
        compose.runOnUiThread { window("ensureTouchModeLocally", Boolean::class.javaPrimitiveType!!, true) }
    }

    private fun window(method: String, type: Class<*>, value: Any) {
        val window = View::class.java.getMethod("getViewRootImpl").invoke(root)!!
        window.javaClass.getDeclaredMethod(method, type).apply { isAccessible = true }.invoke(window, value)
    }

    private fun pageDown() {
        compose.runOnUiThread {
            window("dispatchInputEvent", android.view.InputEvent::class.java, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0))
            window("dispatchInputEvent", android.view.InputEvent::class.java, KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0))
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.waitForIdle()
    }

    @Test fun `the first press reaches the stage after a text field had the focus and went away`() {
        open()
        compose.onNodeWithTag("field").performClick()
        compose.waitForIdle()
        typing = false
        compose.waitForIdle()
        assertTrue("the window keeps a focused view, or the keys don't pass through the root", root.hasFocus())
        pageDown()
        assertEquals("the press and its release", listOf(true, false), got.map { it.down })
    }

    @Test fun `the first press reaches the stage when nothing was typed`() {
        open()
        typing = false
        compose.waitForIdle()
        pageDown()
        assertEquals(listOf(true, false), got.map { it.down })
    }

    @Test fun `a text field keeps the focus while it is on screen`() {
        open()
        compose.onNodeWithTag("field").performClick()
        compose.waitForIdle()
        assertFalse("the root doesn't take the focus from the field", root.isFocused)
        assertTrue(root.hasFocus())
    }
}
