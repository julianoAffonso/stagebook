package br.app.stagebook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.InstallResultReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
