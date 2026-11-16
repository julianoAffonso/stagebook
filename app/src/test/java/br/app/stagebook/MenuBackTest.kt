package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
}
