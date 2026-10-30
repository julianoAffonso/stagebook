package br.app.stagebook

import br.app.stagebook.data.backgroundScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A failing background job is logged and the others keep running (deep audit, 30/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackgroundScopeTest {
    @Test fun `one job throwing doesn't stop the scope or crash`() {
        val scope = backgroundScope(Dispatchers.IO)
        scope.launch { error("boom") }
        val ran = CountDownLatch(1)
        scope.launch { ran.countDown() }
        assertTrue(ran.await(2, TimeUnit.SECONDS))
    }
}
