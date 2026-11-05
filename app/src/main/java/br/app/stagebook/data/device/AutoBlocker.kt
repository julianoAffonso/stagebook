package br.app.stagebook.data.device

import android.content.Context
import android.content.Intent

/**
 * Samsung's Auto Blocker stops installs from outside the stores, updates of this app included. Only Samsung's own apps
 * can switch it: its setting refuses everyone else, even with WRITE_SECURE_SETTINGS ("RAMPART: Not allowed to put", S26,
 * 05/11). So the app opens its screen and the person decides.
 */
object AutoBlocker {
    private const val PACKAGE = "com.samsung.android.rampart"
    private const val SCREEN = "$PACKAGE.action.MAIN_SETTING_ACTIVITY"

    val screen: Intent get() = Intent(SCREEN).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** This device has the screen: a Samsung with Auto Blocker. */
    fun available(c: Context) = c.packageManager.queryIntentActivities(screen, 0).isNotEmpty()

    fun open(c: Context) {
        runCatching { c.startActivity(screen) }
    }
}
