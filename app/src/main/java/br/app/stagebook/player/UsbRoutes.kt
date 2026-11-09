package br.app.stagebook.player

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import br.app.stagebook.domain.UsbPower
import br.app.stagebook.domain.UsbRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The plugged USB audio interface as Settings shows it: its route, whether the app chose it, and the power warning. */
@Immutable
data class UsbOutputUi(
    val name: String = "",                 // empty: no interface plugged in
    val android: Boolean = false,          // the way it opens: through Android's audio
    val remembered: Boolean = false,       // a way is saved for it on this device
    val auto: Boolean = false,             // saved by the app, after the other way failed
    val powered: Boolean = false,          // it is charging this device
) {
    /** The way shown on the switch: this interface's, or [default] (the library's switch) with none plugged in. */
    fun androidOr(default: Boolean) = if (name.isEmpty()) default else android
}

/**
 * The way each USB audio interface opens on this device ([UsbRoute]), kept in this device's own preferences: the
 * library and its backups go to other devices, where the answer can be different. Read by the player service on each
 * connection and by Settings while it shows the player; nothing here runs on its own.
 */
object UsbRoutes {
    private const val PREFS = "usb_route"

    private val _changes = MutableStateFlow(0)
    /** Bumped whenever a way is saved: Settings reads the interface again. */
    val changes: StateFlow<Int> = _changes

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // the device's own name in the key: an Android backup restored on another device doesn't bring this one's answers
    private fun key(d: UsbDevice) = UsbRoute.key(Build.DEVICE.orEmpty(), d.vendorId, d.productId)

    fun remembered(c: Context, d: UsbDevice): UsbRoute.Choice? = UsbRoute.decode(prefs(c).getString(key(d), null))

    /** Open [d] through Android's audio? */
    @JvmStatic
    fun android(c: Context, d: UsbDevice, androidDefault: Boolean): Boolean =
        UsbRoute.way(remembered(c, d), androidDefault) == UsbRoute.Way.ANDROID

    /** The way in use failed for [d]: true when the other way was saved and should be opened now. */
    @JvmStatic
    fun fallback(c: Context, d: UsbDevice, failedAndroid: Boolean): Boolean {
        val next = UsbRoute.fallback(remembered(c, d), if (failedAndroid) UsbRoute.Way.ANDROID else UsbRoute.Way.ENGINE)
            ?: return false
        save(c, d, next)
        return true
    }

    /** The person chose the way in Settings for the plugged interface: saved, it wins from now on. */
    fun choose(c: Context, d: UsbDevice, android: Boolean) =
        save(c, d, UsbRoute.Choice(if (android) UsbRoute.Way.ANDROID else UsbRoute.Way.ENGINE, manual = true))

    private fun save(c: Context, d: UsbDevice, choice: UsbRoute.Choice) {
        prefs(c).edit { putString(key(d), UsbRoute.encode(choice)) }
        _changes.update { it + 1 }
    }

    /** The USB audio interface the player would open (the same pick as the service), or null. */
    fun plugged(c: Context): UsbDevice? = c.getSystemService(UsbManager::class.java)?.let { UsbLink.find(it) }

    /**
     * What Settings shows for the plugged interface. Reads the battery's last state (a sticky broadcast: no receiver is
     * left registered) and the Type-C ports; off the main thread.
     */
    fun read(c: Context, androidDefault: Boolean): UsbOutputUi {
        val d = plugged(c) ?: return UsbOutputUi(android = androidDefault)
        val saved = remembered(c, d)
        val battery: Intent? = ContextCompat.registerReceiver(c, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return UsbOutputUi(
            name = d.productName?.trim().takeUnless { it.isNullOrEmpty() } ?: "USB",
            android = UsbRoute.way(saved, androidDefault) == UsbRoute.Way.ANDROID,
            remembered = saved != null,
            auto = saved?.manual == false,
            powered = UsbPower.warn(true, plugged, UsbPower.port()),
        )
    }
}
