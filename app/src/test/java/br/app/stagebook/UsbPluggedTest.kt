package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import br.app.stagebook.domain.HostDelivery
import br.app.stagebook.domain.HostWarning
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.OutputInfo
import br.app.stagebook.ui.components.HostWarningDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The interface shows as soon as it's plugged in, and a USB that can't carry it warns (user, 10/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class UsbPluggedTest {
    @get:Rule val compose = createComposeRule()

    private val xr18 = OutputInfo("usb:1397:00d4", "X18/XR18", 18)
    private val nanocore = OutputInfo("usb:1234:0001", "Nanocore", 2)

    @Test fun `a known interface comes back with its outputs and routing - a new one with its outputs unread`() {
        // the Chuwi showed the Nanocore with the XR18 on the cable until play
        val m = MixerConfig().connected(nanocore).connected(xr18).withOutput(0, 12).connected(nanocore)
        assertEquals(nanocore, m.output)
        val back = m.copy(output = m.plugged(xr18.key, "X18/XR18"))
        assertEquals(xr18, back.output)
        assertEquals(12, back.outputOf(0))   // the routing saved for it
        val fresh = m.plugged("usb:0499:1111", "UMC404HD 192k")
        assertTrue(fresh.unread)
        assertEquals("UMC404HD 192k", fresh.name)
        assertFalse(fresh.lacks(8, pair = true))   // unread isn't "missing": nothing turns red
        assertEquals(OutputInfo(), m.plugged(null, ""))   // the cable out: the device
    }

    @Test fun `only an opened interface is remembered`() {
        val m = MixerConfig().connected(OutputInfo()).connected(OutputInfo("usb:1:2", "X", 0))
        assertTrue(m.known.isEmpty())
    }

    @Test fun `below 99_7 percent warns once - again only if it gets worse`() {
        // calibrated on the user's interfaces: clean ones gave 99.9-100%, the Chuwi with the XR18 98.4%
        assertFalse(HostDelivery.warns(100f, null))
        assertFalse(HostDelivery.warns(99.9f, null))
        assertFalse(HostDelivery.warns(0f, null))   // not measured
        assertTrue(HostDelivery.warns(99.5f, null))
        assertTrue(HostDelivery.warns(98.4f, null))
        assertFalse(HostDelivery.warns(98.4f, 98.4f))
        assertFalse(HostDelivery.warns(98.3f, 98.4f))
        assertTrue(HostDelivery.warns(97.5f, 98.4f))
    }

    @Test fun `the warning blames the channels only on an interface with many`() {
        compose.setContent { StagebookTheme { HostWarningDialog(HostWarning("X18/XR18", 1.6f, 18)) {} } }
        compose.onNodeWithText("A USB pode falhar").assertIsDisplayed()
        compose.onNodeWithText("perdeu 1,6% dos pacotes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Esta interface tem 18 canais", substring = true).assertIsDisplayed()
    }

    @Test fun `a small interface gets no word about channels`() {
        compose.setContent { StagebookTheme { HostWarningDialog(HostWarning("UMC404HD 192k", 0.5f, 4)) {} } }
        compose.onNodeWithText("por sua conta e risco", substring = true).assertIsDisplayed()
        compose.onNodeWithText("canais", substring = true).assertDoesNotExist()
    }
}
