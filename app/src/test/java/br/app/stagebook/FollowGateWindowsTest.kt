package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import br.app.stagebook.ui.components.FollowBusyWhile
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The open library menu holds the follower back like any menu, but it is not a window: the band's strip rises above Android's
 * navigation bar only for a window (it rose over the menu's tabs, device test 16/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FollowGateWindowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the library menu counts as a menu and not as a window, and a dialog counts as both`() {
        val gate = FollowGate()
        var menu by mutableStateOf(true)
        var dialog by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalFollowGate provides gate) {
                FollowBusyWhile(menu, window = false)
                if (dialog) FollowBusyWhile()
            }
        }
        compose.waitForIdle()
        assertEquals(1 to 0, gate.menus to gate.windows)
        dialog = true
        compose.waitForIdle()
        assertEquals(2 to 1, gate.menus to gate.windows)
        dialog = false; menu = false
        compose.waitForIdle()
        assertEquals(0 to 0, gate.menus to gate.windows)
    }
}
