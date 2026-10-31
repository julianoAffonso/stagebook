package br.app.stagebook

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.device.DeviceTweak
import br.app.stagebook.data.device.DeviceTweaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings → Advanced: everything on by default except debugging, choices kept, nothing without the permission. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DeviceTweaksTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val cr get() = app.contentResolver

    @Before fun grant() = shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)

    @Test fun `on start everything is written on except debugging`() {
        Settings.Global.putString(cr, "development_settings_enabled", "0")
        Settings.Global.putString(cr, Settings.Global.WINDOW_ANIMATION_SCALE, "1.0")
        DeviceTweaks.ensure(app)
        assertEquals("1", Settings.Secure.getString(cr, "usb_audio_automatic_routing_disabled"))
        assertEquals("0", Settings.Global.getString(cr, "app_auto_restriction_enabled"))
        assertTrue(DeviceTweaks.isOn(app, DeviceTweak.ANIMATIONS))
        assertTrue(DeviceTweaks.isOn(app, DeviceTweak.SCREEN_TIMEOUT))
        assertFalse(DeviceTweaks.isOn(app, DeviceTweak.DEBUGGING))
    }

    @Test fun `a switch turned off stays off on the next start`() {
        DeviceTweaks.set(app, DeviceTweak.ANIMATIONS, false)
        DeviceTweaks.ensure(app)
        assertEquals("1.0", Settings.Global.getString(cr, Settings.Global.WINDOW_ANIMATION_SCALE))
    }

    @Test fun `debugging is never touched on start, only by the switch`() {
        Settings.Global.putString(cr, "development_settings_enabled", "1")
        Settings.Global.putString(cr, "adb_wifi_enabled", "1")
        DeviceTweaks.ensure(app)
        assertTrue(DeviceTweaks.isOn(app, DeviceTweak.DEBUGGING))   // someone testing by adb keeps the connection
        DeviceTweaks.set(app, DeviceTweak.DEBUGGING, false)
        assertEquals("0", Settings.Global.getString(cr, "adb_wifi_enabled"))
    }

    @Test fun `the TCL charge modes only on a device that has them`() {
        assertFalse(DeviceTweaks.available(app, DeviceTweak.CHARGE_MODES))
        DeviceTweaks.ensure(app)
        assertNull(Settings.Global.getString(cr, "tct_smart_longcharge_mode"))   // nothing invented on other makers
        Settings.Global.putString(cr, "tct_smart_longcharge_mode", "1")
        assertTrue(DeviceTweaks.available(app, DeviceTweak.CHARGE_MODES))
        DeviceTweaks.ensure(app)
        assertEquals("0", Settings.Global.getString(cr, "tct_smart_longcharge_mode"))
        assertNull(Settings.Secure.getString(cr, "tct_sleep_charge_mode_enable"))
        assertTrue(DeviceTweaks.isOn(app, DeviceTweak.CHARGE_MODES))
    }

    @Test fun `without the permission nothing is written`() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        DeviceTweaks.ensure(app)
        DeviceTweaks.set(app, DeviceTweak.USB_ROUTING, true)
        assertNull(Settings.Secure.getString(cr, "usb_audio_automatic_routing_disabled"))
    }
}
