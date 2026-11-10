package br.app.stagebook.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import br.app.stagebook.R
import br.app.stagebook.domain.ResumePoint

/**
 * Where to open again after the app installs its own update (user, 09/11): saved right before installing, taken once
 * on the next launch if still fresh ([ResumePoint.FRESH_MS]) and the app was replaced since, cleared when the install
 * doesn't happen. [currentRoute]
 * follows the navigation in memory.
 */
object ResumeAfterUpdate {
    @Volatile var currentRoute: String? = null
    const val HOME = "home"
    private const val NOTIFICATION = 4711
    private const val CHANNEL = "update"

    private fun prefs(c: Context) = c.getSharedPreferences("resume_after_update", Context.MODE_PRIVATE)

    fun save(c: Context, where: String) = prefs(c).edit { putString("where", where); putLong("at", System.currentTimeMillis()) }

    fun pending(c: Context): Boolean = ResumePoint.fresh(prefs(c).getLong("at", 0), System.currentTimeMillis())

    /** No update is coming (the person said no, or it failed): the next start is a plain one. */
    fun clear(c: Context) = prefs(c).edit { clear() }

    /**
     * The place to go back to, once: a fresh mark the update really followed is used up, any other only cleared.
     * Android's installer screen answers nothing when the person says no there (nor does a confirmation left open), so
     * the mark alone used to open the next start on the saved screen for 15 minutes (audit 10/11).
     */
    fun take(c: Context): String? {
        val at = prefs(c).getLong("at", 0)
        val where = if (pending(c) && ResumePoint.replacedSince(at, updatedAt(c))) prefs(c).getString("where", null) else null
        clear(c)
        return where
    }

    // when Android last installed this package (same clock as the mark)
    private fun updatedAt(c: Context): Long = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).lastUpdateTime }.getOrDefault(0L)

    /** The app is open again: the "tap to go back" notice has done its job. */
    fun opened(c: Context) = c.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION)

    internal fun reopen(c: Context) {
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        // Android 10+ doesn't let an app open itself from the background; where it does (Android 9), it opens now
        runCatching { c.startActivity(launch) }
        // and a tap on this always works: removed as soon as the app shows (opened)
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.update_channel), NotificationManager.IMPORTANCE_HIGH))
        val tap = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(c.getString(R.string.update_done_title))
            .setContentText(c.getString(R.string.update_done_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION, n) }
    }
}

/** The system says this app was just replaced by its update: if the update was installed from inside it, reopen. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && ResumeAfterUpdate.pending(context)) ResumeAfterUpdate.reopen(context)
    }
}
