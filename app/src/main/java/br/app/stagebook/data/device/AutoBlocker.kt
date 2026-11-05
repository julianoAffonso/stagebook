package br.app.stagebook.data.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit

/**
 * Samsung's Auto Blocker stops installs from outside the stores, updates of this app included. Only Samsung's own apps
 * can switch it officially, and it turns itself back on, so the app opens its screen and the person decides.
 *
 * Who granted the computer's permission can choose more: it's turned off only while this app's update installs, and put
 * back right after. "Back on" is saved before turning it off, so a crash in between can't leave the device unprotected.
 */
object AutoBlocker {
    private const val PACKAGE = "com.samsung.android.rampart"
    private const val SCREEN = "$PACKAGE.action.MAIN_SETTING_ACTIVITY"
    private const val SWITCH = "rampart_main_switch_enabled"
    private const val ON = "1"
    private const val OFF = "0"
    private const val BYPASS = "auto_blocker_bypass"
    private const val PENDING = "auto_blocker_back_on"
    private const val BACK_ON_MS = 10 * 60 * 1000L   // someone who leaves the installer open and never comes back

    val screen: Intent get() = Intent(SCREEN).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** This device has the screen: a Samsung with Auto Blocker. */
    fun available(c: Context) = c.packageManager.queryIntentActivities(screen, 0).isNotEmpty()

    fun open(c: Context) {
        runCatching { c.startActivity(screen) }
    }

    /** The choice counts only with the computer's permission: without it the setting can't be written. */
    fun bypass(c: Context) = DeviceTweaks.canWrite(c) && prefs(c).getBoolean(BYPASS, false)

    fun setBypass(c: Context, on: Boolean) = prefs(c).edit { putBoolean(BYPASS, on) }

    /** Right before opening the installer: only if it's on, and only if the person chose this. */
    fun beforeInstall(c: Context) {
        if (!bypass(c) || read(c) == OFF) return
        prefs(c).edit(commit = true) { putBoolean(PENDING, true) }
        alarm(c).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + BACK_ON_MS, backOn(c))
        write(c, OFF)
    }

    /** After the update, a cancelled install or the time limit: whatever came first. */
    fun afterInstall(c: Context) {
        if (!prefs(c).getBoolean(PENDING, false)) return
        write(c, ON)
        prefs(c).edit { remove(PENDING) }
        alarm(c).cancel(backOn(c))
    }

    private fun read(c: Context) = runCatching { Settings.Secure.getString(c.contentResolver, SWITCH) }.getOrNull()

    private fun write(c: Context, value: String) {
        runCatching { Settings.Secure.putString(c.contentResolver, SWITCH, value) }
    }

    private fun alarm(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun backOn(c: Context) = PendingIntent.getBroadcast(
        c, 0, Intent(c, AutoBlockerBackOn::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(c: Context) = c.getSharedPreferences("advanced", Context.MODE_PRIVATE)
}

/** The update replaced the app (Android doesn't reopen it), or the time ran out: Auto Blocker goes back on. */
class AutoBlockerBackOn : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) = AutoBlocker.afterInstall(c)
}
