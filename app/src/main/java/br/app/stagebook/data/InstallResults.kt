package br.app.stagebook.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import br.app.stagebook.domain.InstallNotice
import br.app.stagebook.domain.InstallOutcome
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Messages about an update being installed, from the installer session to the screen (the receiver has no screen). */
object InstallResults {
    private val _notices = MutableSharedFlow<InstallNotice>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices: SharedFlow<InstallNotice> = _notices.asSharedFlow()

    fun post(notice: InstallNotice) { _notices.tryEmit(notice) }

    const val ACTION = "br.app.stagebook.INSTALL_RESULT"
}

/**
 * What the system answers once the update session is committed. [InstallOutcome.ASK]: the system wants a tap (first
 * update by this installer, or it decided to ask): its screen opens. Success replaces the app, so there's nothing to say.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (InstallOutcome.of(status)) {
            InstallOutcome.ASK -> {
                @Suppress("DEPRECATION")
                val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                runCatching { confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity) }
                    .onFailure { Log.w("stagebook", "couldn't open the install confirmation", it); InstallResults.post(InstallNotice.FAILED) }
            }
            InstallOutcome.FAILED -> {
                Log.w("stagebook", "update install failed: status=$status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                InstallResults.post(InstallNotice.FAILED)
            }
            InstallOutcome.DONE, InstallOutcome.CANCELLED -> Unit
        }
    }

    internal companion object {
        /** The PendingIntent the system fires with the result: mutable, because the installer adds its extras. */
        fun pending(context: Context, sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
            context, sessionId, Intent(InstallResults.ACTION).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
