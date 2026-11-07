package br.app.stagebook

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.components.OptionDropdown
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A choice the device can't honor stays on the list, off: the metronome's "device" when the click can't leave the USB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class OptionDropdownTest {
    @get:Rule val compose = createComposeRule()

    private val picked = mutableListOf<String>()
    private var opened = 0

    private fun menu() {
        compose.setContent {
            StagebookTheme {
                OptionDropdown("Saída", listOf("Aparelho", "USB"), "USB", { picked += it }, { it }, enabledOf = { it != "Aparelho" }, onOpen = { opened++ })
            }
        }
        compose.onNodeWithText("USB").performClick()
    }

    @Test fun `opening the menu tells who asked`() {
        menu()
        assertEquals(1, opened)
    }

    @Test fun `a disabled option can't be picked`() {
        menu()
        compose.onNodeWithText("Aparelho").assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), picked)
    }

    @Test fun `an enabled option still can`() {
        menu()
        compose.onAllNodesWithText("USB")[1].performClick()
        assertEquals(listOf("USB"), picked)
    }
}
