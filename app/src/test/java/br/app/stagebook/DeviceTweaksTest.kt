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
        assertNull(Settings.Secure.getString(cr, "usb_audio_automatic_routing_disabled"))   // only when the player runs
        assertEquals("0", Settings.Global.getString(cr, "app_auto_restriction_enabled"))
        assertTrue(DeviceTweaks.isOn(app, DeviceTweak.ANIMATIONS))
        assertTrue(DeviceTweaks.isOn(app, DeviceTweak.SCREEN_TIMEOUT))
        assertFalse(DeviceTweaks.isOn(app, DeviceTweak.DEBUGGING))
    }

    @Test fun `a switch turned off stays off on the next start`() {
        DeviceTweaks.set(app, DeviceTweak.ANIMATIONS, false)
        DeviceTweaks.ensure(app)
        assertFalse(DeviceTweaks.shownOn(app, DeviceTweak.ANIMATIONS))
        assertNull(Settings.Global.getString(cr, Settings.Global.WINDOW_ANIMATION_SCALE))   // never written: still the device's
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

    private val timeout get() = Settings.System.getString(cr, Settings.System.SCREEN_OFF_TIMEOUT)

    @Test fun `turning off gives back what the device had`() {
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "120000")
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, true)
        assertEquals("600000", timeout)
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, false)
        assertEquals("120000", timeout)
    }

    @Test fun `what the first start wrote over comes back too`() {
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "120000")
        DeviceTweaks.ensure(app)
        assertEquals("600000", timeout)
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, false)
        assertEquals("120000", timeout)
    }

    @Test fun `the value changed between two uses is the one kept`() {
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "120000")
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, true)
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, false)
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "300000")
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, true)
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, false)
        assertEquals("300000", timeout)
    }

    @Test fun `a device that already had the value keeps it, and the switch shows off`() {
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "600000")
        DeviceTweaks.ensure(app)
        assertTrue(DeviceTweaks.shownOn(app, DeviceTweak.SCREEN_TIMEOUT))
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, false)
        assertEquals("600000", timeout)
        assertFalse(DeviceTweaks.shownOn(app, DeviceTweak.SCREEN_TIMEOUT))
        DeviceTweaks.ensure(app)
        assertEquals("600000", timeout)
    }

    @Test fun `after turning off the setting is left alone on start`() {
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "120000")
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, true)
        DeviceTweaks.set(app, DeviceTweak.SCREEN_TIMEOUT, false)
        Settings.System.putString(cr, Settings.System.SCREEN_OFF_TIMEOUT, "600000")
        DeviceTweaks.ensure(app)
        assertEquals("600000", timeout)
    }

    @Test fun `a setting with no value goes back to none`() {
        Settings.Global.putString(cr, "wifi_scan_always_enabled", null)
        Settings.Global.putString(cr, "ble_scan_always_enabled", "1")
        DeviceTweaks.set(app, DeviceTweak.NO_BACKGROUND_SCANS, true)
        assertEquals("0", Settings.Global.getString(cr, "wifi_scan_always_enabled"))
        DeviceTweaks.set(app, DeviceTweak.NO_BACKGROUND_SCANS, false)
        assertNull(Settings.Global.getString(cr, "wifi_scan_always_enabled"))
        assertEquals("1", Settings.Global.getString(cr, "ble_scan_always_enabled"))
    }

    @Test fun `debugging turned on here gives back the developer mode the device had`() {
        Settings.Global.putString(cr, "development_settings_enabled", "1")
        Settings.Global.putString(cr, "adb_wifi_enabled", "0")
        DeviceTweaks.set(app, DeviceTweak.DEBUGGING, true)
        assertEquals("1", Settings.Global.getString(cr, "adb_wifi_enabled"))
        DeviceTweaks.set(app, DeviceTweak.DEBUGGING, false)
        assertEquals("1", Settings.Global.getString(cr, "development_settings_enabled"))
        assertEquals("0", Settings.Global.getString(cr, "adb_wifi_enabled"))
    }

    @Test fun `debugging on by other hands turns off with the switch`() {
        Settings.Global.putString(cr, "adb_wifi_enabled", "1")
        Settings.Global.putString(cr, "development_settings_enabled", "1")
        DeviceTweaks.set(app, DeviceTweak.DEBUGGING, false)
        assertEquals("0", Settings.Global.getString(cr, "adb_wifi_enabled"))
    }

    private val routing = "usb_audio_automatic_routing_disabled"

    @Test fun `the USB routing is written when the player runs on the engine route and given back when it stops`() {
        Settings.Secure.putString(cr, routing, "0")
        DeviceTweaks.ensure(app)
        assertEquals("0", Settings.Secure.getString(cr, routing))   // app start writes nothing
        DeviceTweaks.routeUsb(app, true)
        assertEquals("1", Settings.Secure.getString(cr, routing))
        DeviceTweaks.routeUsb(app, true)   // a second start keeps the first original
        DeviceTweaks.routeUsb(app, false)
        assertEquals("0", Settings.Secure.getString(cr, routing))
    }

    @Test fun `the device's own USB routing value comes back, even when it was on or absent`() {
        Settings.Secure.putString(cr, routing, "1")
        DeviceTweaks.routeUsb(app, true)
        DeviceTweaks.routeUsb(app, false)
        assertEquals("1", Settings.Secure.getString(cr, routing))
        Settings.Secure.putString(cr, routing, null)
        DeviceTweaks.routeUsb(app, true)
        DeviceTweaks.routeUsb(app, false)
        assertNull(Settings.Secure.getString(cr, routing))
    }

    @Test fun `giving back the USB routing never written touches nothing`() {
        Settings.Secure.putString(cr, routing, "1")   // someone else's choice
        DeviceTweaks.routeUsb(app, false)
        assertEquals("1", Settings.Secure.getString(cr, routing))
    }

    @Test fun `without the permission nothing is written`() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        DeviceTweaks.ensure(app)
        DeviceTweaks.set(app, DeviceTweak.USB_ROUTING, true)
        assertNull(Settings.Secure.getString(cr, "usb_audio_automatic_routing_disabled"))
    }
}
