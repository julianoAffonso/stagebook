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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // the update went in: Android stamps the package with the time it was replaced
    private fun replaced() {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).lastUpdateTime = System.currentTimeMillis()
    }

    private fun downloaded() = File(context.cacheDir, "atualizacao/stagebook.apk").apply { parentFile?.mkdirs(); writeBytes(ByteArray(1000)) }

    @Test fun `a session that starts marks the place and says the update is going in`() = runBlocking {
        allowInstalls()
        val said = async(start = CoroutineStart.UNDISPATCHED) { InstallResults.notices.first() }
        assertTrue(screen.installApk(updater, downloaded(), where))
        assertEquals(InstallNotice.STARTED, withTimeout(5_000) { said.await() })
        replaced()
        assertEquals(where, ResumeAfterUpdate.take(context))
    }

    @Test fun `a session that can't start opens Android's installer, with the place marked`() = runBlocking {
        allowInstalls()
        val missing = File(context.cacheDir, "atualizacao/stagebook.apk")   // nothing to copy: the session fails
        assertFalse(screen.installApk(updater, missing, where))
        assertEquals(Intent.ACTION_VIEW, shadowOf(screen).nextStartedActivity.action)
        replaced()
        assertEquals(where, ResumeAfterUpdate.take(context))
    }

    @Config(sdk = [28])
    @Test fun `older Android opens its installer, with the place marked`() = runBlocking {
        allowInstalls()
        assertFalse(screen.installApk(updater, downloaded(), where))
        assertEquals(Intent.ACTION_VIEW, shadowOf(screen).nextStartedActivity.action)
        replaced()
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

    // Android's installer screen answers nothing when the person says no there (audit 10/11): the mark stayed for 15
    // minutes and the next start opened on the saved screen, as if the update had gone in
    @Test fun `a mark no update followed leaves the next start a plain one`() {
        ResumeAfterUpdate.save(context, where)   // what a tap on Install leaves before Android's installer screen opens
        assertNull(ResumeAfterUpdate.take(context))
    }

    // rotating during the copy cancelled the tap's work after the session was already committed (audit 10/11): the
    // update went in and nobody said so
    @Test fun `a screen recreated during the copy still hears that the update is going in`() = runBlocking {
        allowInstalls()
        val copying = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val apk = object : File(downloaded().path) {
            override fun length(): Long { copying.countDown(); finish.await(10, TimeUnit.SECONDS); return super.length() }
        }
        val tap = launch(Dispatchers.Default) { screen.installApk(updater, apk, where) }
        withContext(Dispatchers.IO) { assertTrue(copying.await(10, TimeUnit.SECONDS)) }
        tap.cancel()   // the old screen is gone
        finish.countDown()
        tap.join()
        // the new screen starts listening only now
        assertEquals(InstallNotice.STARTED, withTimeout(5_000) { InstallResults.notices.first() })
    }

    @Test fun `a notice already shown is not said again to the next screen`() = runBlocking {
        InstallResults.post(InstallNotice.STARTED)
        assertEquals(InstallNotice.STARTED, withTimeout(5_000) { InstallResults.notices.first() })
        assertNull(withTimeoutOrNull(300) { InstallResults.notices.first() })
    }
}
