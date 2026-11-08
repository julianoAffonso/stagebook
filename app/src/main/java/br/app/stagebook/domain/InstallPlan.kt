package br.app.stagebook.domain

import android.content.pm.PackageInstaller

/** How an update gets installed, decided from the Android version and whether this app may install packages. */
enum class InstallPlan {
    /** Android must first allow "install apps from this source" for Stagebook. */
    NEEDS_SOURCE_PERMISSION,

    /** Android 12+: an installer session that doesn't ask for a tap when the system lets an update through. */
    SESSION,

    /** Older Android (or a session that couldn't start): the system installer screen, which always asks. */
    SCREEN,
    ;

    companion object {
        /** First Android version where an installer session can skip the confirmation tap (S, API 31). */
        const val SESSION_SDK = 31

        fun choose(sdk: Int, canInstall: Boolean): InstallPlan = when {
            !canInstall -> NEEDS_SOURCE_PERMISSION
            sdk >= SESSION_SDK -> SESSION
            else -> SCREEN
        }
    }
}

/** What the system reported for an installer session. */
enum class InstallOutcome {
    /** The system needs the person to confirm: its own screen opens. */
    ASK,

    /** Installed (the app is replaced and restarts, so this is rarely seen). */
    DONE,

    /** The person said no on the system screen. */
    CANCELLED,

    /** It didn't install. */
    FAILED,
    ;

    companion object {
        fun of(status: Int): InstallOutcome = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> ASK
            PackageInstaller.STATUS_SUCCESS -> DONE
            PackageInstaller.STATUS_FAILURE_ABORTED -> CANCELLED
            else -> FAILED
        }
    }
}

/** What the person sees while the update goes in: the two short messages. */
enum class InstallNotice { STARTED, FAILED }
