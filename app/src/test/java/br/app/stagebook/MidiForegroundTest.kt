package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MidiPedal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The saved Bluetooth pedal is looked for only with the app in front (deep audit, 30/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MidiForegroundTest {
    @Test fun `starts paused and follows the app coming to the front and leaving`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pedal = MidiPedal(context, LibraryRepository(context, scope), scope)
        @Suppress("UNCHECKED_CAST")
        val inFront = MidiPedal::class.java.getDeclaredField("inFront").apply { isAccessible = true }.get(pedal) as MutableStateFlow<Boolean>
        assertEquals(false, inFront.value)   // the reconnect loop waits here before any Bluetooth call
        pedal.setForeground(true)
        assertEquals(true, inFront.value)
        pedal.setForeground(false)
        assertEquals(false, inFront.value)
    }
}
