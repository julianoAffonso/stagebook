package br.app.stagebook.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.domain.InstallNotice
import br.app.stagebook.domain.InstallPlan
import br.app.stagebook.data.InstallResults
import br.app.stagebook.data.ResumeAfterUpdate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// a second tap while the APK is still being copied would abandon the session being written and start another
private val installing = AtomicBoolean(false)

/**
 * Installs the downloaded APK. The first time, Android must allow "install apps from this source" for Stagebook: opens
 * that screen instead (then just tap install again). On Android 12+ it goes through an installer session, which updates
 * without a tap when the system allows it; otherwise (or if the session can't start) the system installer screen opens.
 * [resumeAt] is where the app opens again after the update (user, 09/11): marked only when an install really starts,
 * and before it does (the install can close the app at any moment).
 * True when a session started: the update window can close, so its message isn't hidden behind it.
 */
suspend fun Context.installApk(updater: AppUpdater, apk: File?, resumeAt: String): Boolean {
    apk ?: return false
    if (!installing.compareAndSet(false, true)) return false
    try {
        when (InstallPlan.choose(Build.VERSION.SDK_INT, updater.canInstall(), InstallResults.sessionFailed)) {
            InstallPlan.NEEDS_SOURCE_PERMISSION ->
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", packageName, null)))
            InstallPlan.SESSION -> {
                ResumeAfterUpdate.save(this, resumeAt)
                // not cut half way: a screen recreated during the copy (rotation) cancels the tap's work, and the session
                // was committed all the same, with nobody told that the update was going in
                val started = withContext(NonCancellable) {
                    updater.installWithSession(apk).also { if (it) InstallResults.post(InstallNotice.STARTED) }
                }
                if (started) return true
                startActivity(updater.installIntent(apk))
            }
            InstallPlan.SCREEN -> {
                ResumeAfterUpdate.save(this, resumeAt)
                startActivity(updater.installIntent(apk))
            }
        }
        return false
    } finally {
        installing.set(false)
    }
}
