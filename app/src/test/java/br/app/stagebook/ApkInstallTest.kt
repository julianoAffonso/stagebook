package br.app.stagebook

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.InstallResults
import br.app.stagebook.data.ResumeAfterUpdate
import br.app.stagebook.domain.InstallNotice
import br.app.stagebook.ui.installApk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A tap on Install: the place to open again after the update is marked only when an install really starts, and the
 * APK is copied into the session once, however many taps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ApkInstallTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val screen = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val updater = AppUpdater(context)
    private val where = "settings:ABOUT"

    @After fun fresh() {
        InstallResults.reset()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)   // Robolectric doesn't reset it between tests
    }

    private fun allowInstalls() = shadowOf(context.packageManager).setCanRequestPackageInstalls(true)

    private fun downloaded() = File(context.cacheDir, "atualizacao/stagebook.apk").apply { parentFile?.mkdirs(); writeBytes(ByteArray(1000)) }

    @Test fun `a session that starts marks the place and says the update is going in`() = runBlocking {
        allowInstalls()
        val said = async(start = CoroutineStart.UNDISPATCHED) { InstallResults.notices.first() }
        assertTrue(screen.installApk(updater, downloaded(), where))
        assertEquals(InstallNotice.STARTED, withTimeout(5_000) { said.await() })
        assertEquals(where, ResumeAfterUpdate.take(context))
    }

    @Test fun `a session that can't start opens Android's installer, with the place marked`() = runBlocking {
        allowInstalls()
        val missing = File(context.cacheDir, "atualizacao/stagebook.apk")   // nothing to copy: the session fails
        assertFalse(screen.installApk(updater, missing, where))
        assertEquals(Intent.ACTION_VIEW, shadowOf(screen).nextStartedActivity.action)
        assertEquals(where, ResumeAfterUpdate.take(context))
    }

    @Config(sdk = [28])
    @Test fun `older Android opens its installer, with the place marked`() = runBlocking {
        allowInstalls()
        assertFalse(screen.installApk(updater, downloaded(), where))
        assertEquals(Intent.ACTION_VIEW, shadowOf(screen).nextStartedActivity.action)
        assertEquals(where, ResumeAfterUpdate.take(context))
    }

    @Test fun `a second tap while the APK is being copied starts no other session`() = runBlocking {
        allowInstalls()
        val copies = AtomicInteger()
        val copying = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val path = downloaded().path
        // the first copy waits, as a slow tablet would
        val apk = object : File(path) {
            override fun length(): Long {
                if (copies.incrementAndGet() == 1) { copying.countDown(); finish.await(10, TimeUnit.SECONDS) }
                return super.length()
            }
        }
        val first = async { screen.installApk(updater, apk, where) }
        withContext(Dispatchers.IO) { assertTrue(copying.await(10, TimeUnit.SECONDS)) }
        assertFalse(screen.installApk(updater, apk, where))
        finish.countDown()
        assertTrue(first.await())
        assertEquals(1, copies.get())
        assertTrue(ResumeAfterUpdate.pending(context))   // the tap that did nothing didn't undo the first one's mark
    }
}
