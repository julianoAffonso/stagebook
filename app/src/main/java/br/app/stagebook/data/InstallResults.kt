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

    /** A session failed in this launch: the next try uses the system installer screen instead of another session. */
    @Volatile var sessionFailed = false
        private set

    /**
     * The session this launch is waiting on: [NO_SESSION] before it starts one (an answer is then about the last
     * launch's), [BETWEEN_SESSIONS] while the next one is prepared. Starting a session abandons the older ones, and
     * their "aborted" answers say nothing about the new one.
     */
    @Volatile var session = NO_SESSION
        internal set

    fun isAbout(sessionId: Int): Boolean = session == NO_SESSION || session == sessionId

    fun post(notice: InstallNotice) {
        if (notice == InstallNotice.FAILED) sessionFailed = true
        _notices.tryEmit(notice)
    }

    // as in a fresh launch (tests: this object outlives each of them)
    internal fun reset() { sessionFailed = false; session = NO_SESSION }

    const val ACTION = "br.app.stagebook.INSTALL_RESULT"
    const val NO_SESSION = 0
    const val BETWEEN_SESSIONS = -1
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
                    .onFailure {
                        Log.w("stagebook", "couldn't open the install confirmation", it)
                        InstallResults.post(InstallNotice.FAILED)
                        noUpdateComing(context, intent)
                    }
            }
            InstallOutcome.FAILED -> {
                Log.w("stagebook", "update install failed: status=$status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                InstallResults.post(InstallNotice.FAILED)
                noUpdateComing(context, intent)
            }
            InstallOutcome.CANCELLED -> noUpdateComing(context, intent)
            InstallOutcome.DONE -> Unit
        }
    }

    // the app keeps running: without this, closing and opening it within minutes opened on the saved screen, as if an
    // update had just gone in
    private fun noUpdateComing(context: Context, intent: Intent) {
        val about = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, InstallResults.NO_SESSION)
        if (InstallResults.isAbout(about)) ResumeAfterUpdate.clear(context)
    }

    internal companion object {
        /**
         * The PendingIntent the system fires with the result: mutable, because the installer adds its extras. Addressed
         * to this receiver by name: it has no intent filter, so an action alone reached nothing and Android's "install
         * this update?" never got to the app (the session waited forever, 09/11).
         */
        fun pending(context: Context, sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
            context, sessionId, Intent(context, InstallResultReceiver::class.java).setAction(InstallResults.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
