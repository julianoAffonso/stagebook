package br.app.stagebook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.InstallResultReceiver
import br.app.stagebook.data.InstallResults
import br.app.stagebook.data.ResumeAfterUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The installer session answers through this PendingIntent. The receiver is declared without an intent filter, so an
 * intent with only an action and the package reached nothing: Android asked "install this update?" and the app never
 * heard it, the session sat at 80% and nothing happened (TCL, 09/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InstallResultReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun fresh() = InstallResults.reset()

    @Test fun `the session's answer is addressed to the receiver itself`() {
        val sent = shadowOf(InstallResultReceiver.pending(context, 7)).savedIntent
        assertEquals(ComponentName(context, InstallResultReceiver::class.java), sent.component)
        assertTrue(context.packageManager.queryBroadcastReceivers(sent, 0).any { it.activityInfo.name == InstallResultReceiver::class.java.name })
    }

    @Test fun `when Android asks, its confirmation screen opens`() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        val answer = Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm)
        InstallResultReceiver().onReceive(context, answer)
        val opened = shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertNotNull(opened)
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", opened.action)
    }

    // the mark would otherwise open the next start on the saved screen, as if an update had just happened
    @Test fun `saying no to Android's confirmation leaves no place to come back to`() {
        ResumeAfterUpdate.save(context, ResumeAfterUpdate.HOME)
        InstallResultReceiver().onReceive(context, Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_ABORTED))
        assertFalse(ResumeAfterUpdate.pending(context))
    }

    @Test fun `an install that failed leaves no place to come back to`() {
        ResumeAfterUpdate.save(context, ResumeAfterUpdate.HOME)
        InstallResultReceiver().onReceive(context, Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_INVALID))
        assertFalse(ResumeAfterUpdate.pending(context))
    }

    // starting a session abandons the one left waiting, and Android answers "aborted" for it
    @Test fun `the answer of an older session keeps the place of the one under way`() {
        ResumeAfterUpdate.save(context, ResumeAfterUpdate.HOME)
        InstallResults.session = 8
        val older = Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_ABORTED).putExtra(PackageInstaller.EXTRA_SESSION_ID, 7)
        InstallResultReceiver().onReceive(context, older)
        assertTrue(ResumeAfterUpdate.pending(context))
        InstallResultReceiver().onReceive(context, Intent(older).putExtra(PackageInstaller.EXTRA_SESSION_ID, 8))
        assertFalse(ResumeAfterUpdate.pending(context))
    }
}
