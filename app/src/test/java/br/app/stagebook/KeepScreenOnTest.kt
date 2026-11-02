package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import br.app.stagebook.ui.stage.KeepScreenOn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Following the leader, the old stage leaving doesn't let the screen dim (deep audit, 30/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class KeepScreenOnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the screen stays on while any stage is there`() {
        var old by mutableStateOf(true)
        var new by mutableStateOf(false)
        var view: android.view.View? = null
        compose.setContent {
            view = LocalView.current
            if (old) KeepScreenOn()
            if (new) KeepScreenOn()
        }
        compose.waitForIdle()
        assertTrue(view!!.keepScreenOn)
        new = true   // the next song's stage comes in
        compose.waitForIdle()
        old = false  // the old one leaves after the fade
        compose.waitForIdle()
        assertTrue(view!!.keepScreenOn)
        new = false  // leaving the stage
        compose.waitForIdle()
        assertFalse(view!!.keepScreenOn)
    }
}
