package br.app.stagebook.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit

/**
 * Android settings found essential for playing without failures (most of them measured on a TCL tablet at the mixing
 * desk), switched in Settings → Advanced (user, 31/10/2026). Writing them needs WRITE_SECURE_SETTINGS, granted once
 * from a computer (`adb shell pm grant br.app.stagebook android.permission.WRITE_SECURE_SETTINGS`); without it the
 * screen says how. All start on except [DEBUGGING]; the ones with [reapply] are written again on every app start,
 * because Android sometimes puts them back by itself.
 */
enum class DeviceTweak(
    internal val settings: List<Pair<Table, String>>,
    internal val on: String,
    internal val off: String,
    val default: Boolean = true,
    val reapply: Boolean = true,
    val group: Group,
    internal val onlyIfPresent: Boolean = false,   // a setting some makers have and others don't
    val shown: Boolean = true,   // in Settings; USB_ROUTING follows the Android route switch instead
    internal val restores: Boolean = true,   // off gives back what the device had; otherwise off is a value that has to hold
) {
    // TCL: with smart long charge or sleep charge on, its charge manager made the USB audio interface drop for a
    // moment every 3 to 30 s (23/10). Cost: charges up to 100%.
    CHARGE_MODES(
        listOf(Table.GLOBAL to "tct_smart_longcharge_mode", Table.SECURE to "tct_sleep_charge_mode_enable"),
        on = "0", off = "1", group = Group.PLAY, onlyIfPresent = true,
    ),
    // Android's own audio stays off the USB interface the player drives (with 0 it opens the same interface)
    USB_ROUTING(listOf(Table.SECURE to "usb_audio_automatic_routing_disabled"), on = "1", off = "0", group = Group.PLAY, shown = false, restores = false),
    // Android's battery manager suggests restricting apps that use battery, and the player uses it on purpose
    BATTERY_MANAGER(listOf(Table.GLOBAL to "app_auto_restriction_enabled"), on = "0", off = "1", group = Group.PLAY),
    ANIMATIONS(
        listOf(
            Table.GLOBAL to Settings.Global.WINDOW_ANIMATION_SCALE,
            Table.GLOBAL to Settings.Global.TRANSITION_ANIMATION_SCALE,
            Table.GLOBAL to Settings.Global.ANIMATOR_DURATION_SCALE,
        ),
        on = "0.5", off = "1.0", group = Group.BATTERY,
    ),
    // the stage keeps the screen on by itself; elsewhere the screen goes off after 10 minutes
    SCREEN_TIMEOUT(listOf(Table.SYSTEM to Settings.System.SCREEN_OFF_TIMEOUT), on = "600000", off = "1800000", group = Group.BATTERY),
    // Wi-Fi and Bluetooth searches with the location off serve nothing
    NO_BACKGROUND_SCANS(listOf(Table.GLOBAL to "wifi_scan_always_enabled", Table.GLOBAL to "ble_scan_always_enabled"), on = "0", off = "1", group = Group.BATTERY),
    NO_MOBILE_DATA_ALWAYS_ON(listOf(Table.GLOBAL to "mobile_data_always_on"), on = "0", off = "1", group = Group.BATTERY),
    // developer options + Wi-Fi debugging, for the computer to come in by adb without opening Android's settings.
    // Starts off and is never written again by itself. Order matters: the mode goes on first and off last (see write)
    DEBUGGING(listOf(Table.GLOBAL to "development_settings_enabled", Table.GLOBAL to "adb_wifi_enabled"), on = "1", off = "0", default = false, reapply = false, restores = false, group = Group.COMPUTER);

    enum class Table { SYSTEM, SECURE, GLOBAL }
    enum class Group { PLAY, BATTERY, COMPUTER }
}

object DeviceTweaks {
    fun canWrite(c: Context) = c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** This device has the setting (the TCL charge modes only exist on TCL). */
    fun available(c: Context, t: DeviceTweak) = !t.onlyIfPresent || t.settings.any { read(c, it) != null }

    /** What Android has now (the truth is the device, not the app). */
    fun isOn(c: Context, t: DeviceTweak) = t.settings.filter { !t.onlyIfPresent || read(c, it) != null }.all { same(read(c, it), t.on) }

    /** What the switch shows: turned off here stays off, even when the device's own value is the same as the on one. */
    fun shownOn(c: Context, t: DeviceTweak) = isOn(c, t) && prefs(c).getBoolean(t.name, true)

    /** Writes the choice and keeps it, so it's written again on the next start. */
    fun set(c: Context, t: DeviceTweak, on: Boolean) {
        if (!canWrite(c)) return
        prefs(c).edit { putBoolean(t.name, on) }
        write(c, t, on)
    }

    /** On app start: what Android put back by itself is written again (no choice saved = the default). */
    fun ensure(c: Context) {
        if (!canWrite(c)) return
        DeviceTweak.entries.filter { it.reapply && available(c, it) }.forEach { t ->
            val on = prefs(c).getBoolean(t.name, t.default)
            if (!on && t.restores) return@forEach   // turned off: the setting is the person's again
            if (isOn(c, t) != on) {
                Log.w(TAG, "${t.name} had changed: written again")
                write(c, t, on)
            }
        }
    }

    /** Stagebook is free from battery saving (only Android can grant it, with the user's tap). */
    fun batteryFree(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    private fun write(c: Context, t: DeviceTweak, on: Boolean) {
        for (s in if (on) t.settings else t.settings.reversed()) {
            if (t.onlyIfPresent && read(c, s) == null) continue
            val value = when {
                on -> { if (t.restores) keepOriginal(c, t, s); t.on }
                !t.restores -> t.off
                ORIGINAL + s.second !in prefs(c) -> continue   // it already had this value: nothing to give back
                else -> takeOriginal(c, s)
            }
            // a device that hides a setting must not close the app on start
            try {
                val cr = c.contentResolver
                when (s.first) {
                    DeviceTweak.Table.SYSTEM -> Settings.System.putString(cr, s.second, value)
                    DeviceTweak.Table.SECURE -> Settings.Secure.putString(cr, s.second, value)
                    DeviceTweak.Table.GLOBAL -> Settings.Global.putString(cr, s.second, value)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "couldn't write ${s.second}", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "couldn't write ${s.second}", e)
            }
        }
    }

    // what the device had before the first write, for turning off to give it back
    private fun keepOriginal(c: Context, t: DeviceTweak, s: Pair<DeviceTweak.Table, String>) {
        val now = read(c, s)
        if (ORIGINAL + s.second !in prefs(c) && !same(now, t.on)) prefs(c).edit { putString(ORIGINAL + s.second, now ?: NO_VALUE) }
    }

    private fun takeOriginal(c: Context, s: Pair<DeviceTweak.Table, String>): String? {
        val kept = prefs(c).getString(ORIGINAL + s.second, null)
        prefs(c).edit { remove(ORIGINAL + s.second) }
        return kept.takeUnless { it == NO_VALUE }
    }

    private fun read(c: Context, s: Pair<DeviceTweak.Table, String>): String? = runCatching {
        val cr = c.contentResolver
        when (s.first) {
            DeviceTweak.Table.SYSTEM -> Settings.System.getString(cr, s.second)
            DeviceTweak.Table.SECURE -> Settings.Secure.getString(cr, s.second)
            DeviceTweak.Table.GLOBAL -> Settings.Global.getString(cr, s.second)
        }
    }.getOrNull()

    // "0.5" and "0.50", "1" and "1.0" are the same value
    private fun same(value: String?, expected: String): Boolean {
        val v = value?.toFloatOrNull() ?: return value == expected
        return v == expected.toFloatOrNull()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("advanced", Context.MODE_PRIVATE)

    const val GRANT = "adb shell pm grant br.app.stagebook android.permission.WRITE_SECURE_SETTINGS"
    private const val TAG = "stagebook"
    private const val ORIGINAL = "original:"
    private const val NO_VALUE = "<none>"   // the setting had no value at all
}
