package br.app.stagebook

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.device.StageQuiet
import br.app.stagebook.data.model.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Do not disturb on stage (user, 07/11): on by default, asked once, and the device's own mode goes back as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class StageQuietTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @Test fun `on by default`() = assertTrue(Look().stageDnd)

    @Test fun `without the permission it asks once and touches nothing`() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        assertTrue(StageQuiet.shouldAsk(context))
        StageQuiet.set(context, true)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        StageQuiet.asked(context)
        assertFalse(StageQuiet.shouldAsk(context))
    }

    @Config(sdk = [28])
    @Test fun `Android 9 puts the device's mode back`() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        StageQuiet.set(context, true)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        StageQuiet.set(context, true)   // the stage coming back to the front doesn't lose what was there
        StageQuiet.set(context, false)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)
    }

    // audit 07/11: the startup clean-up runs on another thread; if the stage already asked for quiet, it must not undo it
    @Config(sdk = [28])
    @Test fun `the startup clean-up never undoes a stage that already asked for quiet`() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        StageQuiet.set(context, true)
        StageQuiet.clearStale(context)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        StageQuiet.set(context, false)
        StageQuiet.clearStale(context)   // and with nothing asked, it is the same as turning it off: nothing left on
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }

    @Config(sdk = [35])
    @Test fun `a rule of its own, never the device's mode`() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        StageQuiet.set(context, true)
        assertEquals(1, nm.automaticZenRules.size)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        StageQuiet.set(context, false)
        StageQuiet.set(context, true)
        assertEquals(1, nm.automaticZenRules.size)   // the same rule, not a new one each time
    }
}
