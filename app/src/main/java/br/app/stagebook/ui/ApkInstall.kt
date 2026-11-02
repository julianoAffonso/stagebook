package br.app.stagebook.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the Android installer with the downloaded APK. The first time, Android must allow "install apps from this
 * source" for Stagebook: opens that screen instead of the installer (then just tap install again).
 */
fun Context.installApk(canInstall: Boolean, installer: Intent?) {
    if (!canInstall) {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", packageName, null)))
    } else {
        installer?.let(::startActivity)
    }
}
