package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import br.app.stagebook.ui.components.AppMessage
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.rememberAppMessage
import br.app.stagebook.ui.share.BandResultMessage
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A screen opened only to share leaves once the band send's result is shown, not before (it would cancel the send). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class BandResultMessageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `onShown runs once for each result, after it comes - not before`() {
        val results = Channel<Int>(Channel.BUFFERED)
        var shown = 0
        compose.setContent {
            StagebookTheme {
                val message: AppMessage = rememberAppMessage()
                CompositionLocalProvider(LocalAppMessage provides message) { BandResultMessage(results.receiveAsFlow(), onShown = { shown++ }) }
            }
        }
        compose.waitForIdle()
        assertEquals("nothing came yet", 0, shown)
        runBlocking { results.send(2) }
        compose.waitUntil(5_000) { shown == 1 }
        runBlocking { results.send(0) }
        compose.waitUntil(5_000) { shown == 2 }
        assertEquals(2, shown)
    }
}
