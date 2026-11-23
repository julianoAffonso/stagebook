package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import br.app.stagebook.navigation.MenuBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The device's back button on Settings, the editor and the importer opened from the side menu brings the menu again, as
 * the screen's arrow does (device test 16/11: it went to the stage). The screen's own back handlers still come first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi")
class MenuBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }

    @Test fun `from the menu the back button does what the arrow does`() {
        var toMenu = 0
        compose.setContent { MenuBack(fromMenu = true) { toMenu++ } }
        pressBack()
        assertEquals(1, toMenu)
    }

    @Test fun `a screen not opened from the menu leaves the back button alone`() {
        var toMenu = 0; var fallback = 0
        compose.setContent {
            BackHandler { fallback++ }   // stands for the navigation's own back
            MenuBack(fromMenu = false) { toMenu++ }
        }
        pressBack()
        assertEquals(0 to 1, toMenu to fallback)
    }

    @Test fun `what the screen itself does with back comes first`() {
        var toMenu = 0; var inner = 0
        var sectionOpen by mutableStateOf(true)
        compose.setContent {
            MenuBack(fromMenu = true) { toMenu++ }
            BackHandler(enabled = sectionOpen) { inner++; sectionOpen = false }   // as Settings closes its open section
        }
        pressBack()
        assertEquals(0 to 1, toMenu to inner)
        pressBack()
        assertEquals(1 to 1, toMenu to inner)
    }

    @Test fun `on a show's stage with the menu open over it, back closes the menu and asks nothing`() {
        var closed = 0; var left = 0
        var open by mutableStateOf(true)
        val state = br.app.stagebook.ui.stage.StageUiState(false, listOf(br.app.stagebook.ui.stage.StageSong("Uma", "Ana", "letra", false, id = "a")), look = br.app.stagebook.data.model.Look(), live = true)
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    br.app.stagebook.ui.drawer.LocalDrawer provides br.app.stagebook.ui.drawer.DrawerControl({}, { closed++; open = false }, isOpen = { open }),
                ) { br.app.stagebook.ui.stage.StageScreen(state, 0, 0, { _, _ -> }, onBack = { left++ }) }
            }
        }
        pressBack()
        assertEquals(1, closed)
        assertEquals(0, compose.onAllNodesWithText("Sair do palco?").fetchSemanticsNodes().size)
        // menu closed: now it is the stage's back, which asks in a show
        pressBack()
        assertEquals(1, compose.onAllNodesWithText("Sair do palco?").fetchSemanticsNodes().size)
        assertEquals(0, left)
    }
}
