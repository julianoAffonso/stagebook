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
import br.app.stagebook.navigation.menuBeside
import androidx.navigation.compose.composable
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
    private var besideStageNow: Boolean? = null   // what the navigation says at this instant, when it differs from what is composed

    private fun show() = compose.setContent {
        StagebookTheme {
            state = rememberDrawerState(DrawerValue.Closed)
            scope = rememberCoroutineScope()
            MenuNotOverScreen(state, besideStage) { besideStageNow ?: besideStage }
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

    @Test fun `leaving a screen and opening the menu in the same instant leaves the menu open`() {
        besideStage = false   // Settings is open, and what is composed still says so
        show()
        compose.waitForIdle()
        // the arrow of Settings: the navigation goes back and the menu is asked to open, before anything is composed again
        compose.runOnIdle { besideStageNow = true; scope.launch { state.open() } }
        compose.waitForIdle()
        assertEquals(DrawerValue.Open, state.currentValue)
        compose.runOnIdle { besideStage = true }
        compose.waitForIdle()
        assertEquals("and it stays open once the stage is composed again", DrawerValue.Open, state.currentValue)
    }

    // a Settings page is where the menu lives now (user, 19/11): its handle opened the menu and it closed at once, and a
    // pinned menu left the tablet as soon as a group opened; the editor and the importer still close it
    @Test fun `the menu may open beside a Settings page, and not over the editor or the importer`() {
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = androidx.navigation.compose.rememberNavController()
            androidx.navigation.compose.NavHost(nav, startDestination = br.app.stagebook.navigation.Home) {
                composable<br.app.stagebook.navigation.Home> {}
                composable<br.app.stagebook.navigation.Stage> {}
                composable<br.app.stagebook.navigation.Settings> {}
                composable<br.app.stagebook.navigation.SongEditor> {}
                composable<br.app.stagebook.navigation.Importer> {}
            }
        }
        fun at(route: Any): Boolean { compose.runOnIdle { nav.navigate(route) }; return compose.runOnIdle { nav.currentDestination.menuBeside() } }
        assertEquals(true, at(br.app.stagebook.navigation.Stage(songId = "a")))
        assertEquals(true, at(br.app.stagebook.navigation.Settings("DEVICE", alone = true)))
        assertEquals(false, at(br.app.stagebook.navigation.SongEditor("a")))
        assertEquals(false, at(br.app.stagebook.navigation.Importer()))
    }
}
