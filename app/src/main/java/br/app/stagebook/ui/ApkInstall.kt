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
import java.io.File

/**
 * Installs the downloaded APK. The first time, Android must allow "install apps from this source" for Stagebook: opens
 * that screen instead (then just tap install again). On Android 12+ it goes through an installer session, which updates
 * without a tap when the system allows it; otherwise (or if the session can't start) the system installer screen opens.
 */
fun Context.installApk(updater: AppUpdater, apk: File?) {
    apk ?: return
    when (InstallPlan.choose(Build.VERSION.SDK_INT, updater.canInstall(), InstallResults.sessionFailed)) {
        InstallPlan.NEEDS_SOURCE_PERMISSION ->
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", packageName, null)))
        InstallPlan.SESSION ->
            if (updater.installWithSession(apk)) InstallResults.post(InstallNotice.STARTED) else startActivity(updater.installIntent(apk))
        InstallPlan.SCREEN -> startActivity(updater.installIntent(apk))
    }
}
