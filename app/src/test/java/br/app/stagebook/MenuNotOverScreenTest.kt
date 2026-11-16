package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import br.app.stagebook.navigation.MenuNotOverScreen
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A whole screen that opens while the menu is still sliding open is not left behind it (device test 16/11: a link kept
 * during the show opens as the menu starts to open, and the menu finished opening over the receive screen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class MenuNotOverScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: DrawerState
    private lateinit var scope: CoroutineScope
    private var besideStage by mutableStateOf(true)

    private fun show() = compose.setContent {
        StagebookTheme {
            state = rememberDrawerState(DrawerValue.Closed)
            scope = rememberCoroutineScope()
            MenuNotOverScreen(state, besideStage)
            ModalNavigationDrawer({ ModalDrawerSheet { Text("menu") } }, drawerState = state) { Box(Modifier.fillMaxSize()) }
        }
    }

    @Test fun `a screen that opens while the menu is still opening closes it`() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scope.launch { state.open() } }
        compose.mainClock.advanceTimeBy(64)   // the menu started to open and is not open yet
        assertEquals("still sliding", DrawerValue.Closed, state.currentValue)
        assertEquals(DrawerValue.Open, state.targetValue)
        besideStage = false                   // the receive screen opened under it
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(DrawerValue.Closed, state.currentValue)
        assertEquals(DrawerValue.Closed, state.targetValue)
    }

    @Test fun `a screen that opens with the menu open closes it, and beside the stage the menu stays`() {
        show()
        compose.runOnIdle { scope.launch { state.open() } }
        compose.waitForIdle()
        assertEquals("beside the stage it stays open", DrawerValue.Open, state.currentValue)
        besideStage = false
        compose.waitForIdle()
        assertEquals(DrawerValue.Closed, state.currentValue)
    }
}
