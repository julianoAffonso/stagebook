package com.juliano.stagebook.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Abre o instalador do Android com o APK baixado. Na primeira vez o Android precisa liberar "instalar apps
 * desta fonte" para o Stagebook: abre essa tela em vez do instalador (depois é só tocar em instalar de novo).
 */
fun Context.installApk(canInstall: Boolean, installer: Intent?) {
    if (!canInstall) {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", packageName, null)))
    } else {
        installer?.let(::startActivity)
    }
}
