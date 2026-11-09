package br.app.stagebook

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.model.Look
import br.app.stagebook.player.UsbOutputUi
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → USB output (user, 09/11): the plugged interface's own way, the app's choice said, and the power warning. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class UsbOutputSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun show(usb: UsbOutputUi, look: Look = Look(playerMode = true)) = compose.setContent {
        StagebookTheme { SettingsScreen(SettingsUiState(false, look), SettingsActions(usb = usb), section = SettingsSection.METRONOME) }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test fun `the power warning shows only when the interface charges the device`() {
        show(UsbOutputUi(name = "Mixer", powered = true))
        compose.onNodeWithText(res.getString(R.string.usb_power_warning)).performScrollTo().assertExists()
    }

    @Test fun `no interface, no warning and no note`() {
        show(UsbOutputUi())
        assertEquals(0, count(res.getString(R.string.usb_power_warning)))
        assertEquals(0, count(res.getString(R.string.usb_route_saved, "")))
    }

    @Test fun `the switch shows the plugged interface's way, not the default`() {
        show(UsbOutputUi(name = "Mixer", android = true, remembered = true, auto = true), Look(playerMode = true, usbViaAndroid = false))
        compose.onNodeWithText(res.getString(R.string.usb_via_android)).performScrollTo().assertIsOn()
        compose.onNodeWithText(res.getString(R.string.usb_route_auto, "Mixer")).performScrollTo().assertExists()
    }

    @Test fun `a way chosen by the person says it is saved`() {
        show(UsbOutputUi(name = "Mixer", android = false, remembered = true, auto = false), Look(playerMode = true, usbViaAndroid = true))
        compose.onNodeWithText(res.getString(R.string.usb_via_android)).performScrollTo().assertIsOff()
        compose.onNodeWithText(res.getString(R.string.usb_route_saved, "Mixer")).performScrollTo().assertExists()
    }

    @Test fun `with nothing plugged in the switch is the default`() {
        show(UsbOutputUi(), Look(playerMode = true, usbViaAndroid = true))
        compose.onNodeWithText(res.getString(R.string.usb_via_android)).performScrollTo().assertIsOn()
    }
}
