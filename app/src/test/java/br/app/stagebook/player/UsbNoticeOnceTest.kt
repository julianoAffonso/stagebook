package br.app.stagebook.player

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.R
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.ui.components.AppMessage
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.stage.rememberPlayerNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "USB output disconnected" notice shows once when the interface drops (user, 07/11): the stage is made again for
 * every song and setlist, and each new stage showed the same notice again, the whole time the interface was away. It
 * shows again only after the interface came back and dropped again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class UsbNoticeOnceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val lost get() = context.getString(R.string.player_usb_lost)

    @After fun reset() {
        PlayerService.running = false
        PlayerService.status = ""
    }

    @Test fun `a new stage doesn't show the same USB notice again`() {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val player = StagePlayer(context, repo)
        val host = SnackbarHostState()
        var stage by mutableIntStateOf(0)
        var note = ""
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppMessage provides remember { AppMessage(host, scope) }) {
                val ui by player.ui.collectAsState()
                // what each stage does with the player's notice (StageRoute); a new key = the stage made again
                key(stage) { note = rememberPlayerNote(ui.message, player::firstShow) }
            }
        }
        fun service(status: String) = compose.runOnIdle {
            PlayerService.running = true   // on, not connected: the stage waits for the interface
            PlayerService.status = status
            StagePlayer.onServiceChanged()
        }
        fun shown() = host.currentSnackbarData?.visuals?.message

        service(lost)
        compose.waitUntil(5_000) { shown() == lost }
        compose.runOnIdle { host.currentSnackbarData?.dismiss() }

        stage = 1   // the next song: a new stage, the interface still away
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertNull("the same notice came back on the new stage", shown())

        val ready = context.getString(R.string.player_usb_ready, "Interface")
        service(ready)   // it came back (a short note on the footer)…
        compose.waitUntil(5_000) { note == ready }
        service(lost)    // …and dropped again: the notice shows again
        compose.waitUntil(5_000) { shown() == lost }
    }

    // audit 07/11: the note's own timer is cancelled when the notice changes; nothing then took the note off the footer
    @Test fun `a short note leaves when the notice goes away, not ten seconds later`() {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val player = StagePlayer(context, repo)
        val host = SnackbarHostState()
        var note = ""
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppMessage provides remember { AppMessage(host, scope) }) {
                val ui by player.ui.collectAsState()
                note = rememberPlayerNote(ui.message, player::firstShow)
            }
        }
        fun service(status: String) = compose.runOnIdle {
            PlayerService.running = true
            PlayerService.status = status
            StagePlayer.onServiceChanged()
        }
        val ready = context.getString(R.string.player_usb_ready, "Interface")
        service(ready)
        compose.waitUntil(5_000) { note == ready }
        service("")   // the notice is gone a moment later: the footer must not keep the old one
        compose.waitUntil(5_000) { note == "" }
    }

    // user, 08/11: the stream closes after 10 s idle and the service clears the notice; on play the same "USB output" note
    // must show again, not be taken for the one already shown
    @Test fun `the ready note shows again when the stream opens after sleeping`() {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val player = StagePlayer(context, repo)
        val host = SnackbarHostState()
        var note = ""
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppMessage provides remember { AppMessage(host, scope) }) {
                val ui by player.ui.collectAsState()
                note = rememberPlayerNote(ui.message, player::firstShow)
            }
        }
        fun service(status: String) = compose.runOnIdle {
            PlayerService.running = true
            PlayerService.status = status
            StagePlayer.onServiceChanged()
        }
        val ready = context.getString(R.string.player_usb_ready, "Interface")
        service(ready)
        compose.waitUntil(5_000) { note == ready }
        service("")      // the stream went to sleep
        compose.waitUntil(5_000) { note == "" }
        service(ready)   // and opened again on play
        compose.waitUntil(5_000) { note == ready }
    }

    // the permission wait used to reach only the notification: now the stage says it as a short note, never a snackbar
    @Test fun `waiting for the USB permission shows a short note on the stage`() {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val player = StagePlayer(context, repo)
        val host = SnackbarHostState()
        var note = ""
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppMessage provides remember { AppMessage(host, scope) }) {
                val ui by player.ui.collectAsState()
                note = rememberPlayerNote(ui.message, player::firstShow)
            }
        }
        val wait = context.getString(R.string.player_usb_permission_note)
        compose.runOnIdle {
            PlayerService.running = true
            PlayerService.status = wait
            StagePlayer.onServiceChanged()
        }
        compose.waitUntil(5_000) { note == wait }
        assertNull(host.currentSnackbarData)
    }
}
