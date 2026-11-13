package br.app.stagebook

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MidiState
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.PedalPress
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.settings.SettingsViewModel
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Pedal and MIDI → Buttons: a button pressed once stayed listed for good. It can be forgotten: off the list,
 * its command back to the default, and listed again the next time it's pressed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class PedalForgetTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val a = KeyEvent.KEYCODE_A
    private val b = KeyEvent.KEYCODE_B

    @Test fun `forgetting takes the button off the list with its command, and pressing it lists it again`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(15_000) { repo.loaded.first { it } }
        val vm = SettingsViewModel(
            repo, context, AppUpdater(context),
            br.app.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)),
            br.app.stagebook.data.Backup(context, repo, br.app.stagebook.data.TrackStore(context)),
            br.app.stagebook.data.MetronomeSound(context), br.app.stagebook.data.MidiOut(context, repo, CoroutineScope(Dispatchers.IO)),
            br.app.stagebook.player.StagePlayer(context, repo),
        )
        suspend fun until(ok: (Look) -> Boolean) = withTimeout(15_000) { repo.library.first { ok(it.look) } }.look

        vm.onKey(PedalPress(a, down = true))
        vm.onKey(PedalPress(b, down = true))
        vm.setPedalAction(a, PedalAction.NEXT_SONG)
        vm.setPedalAction(b, PedalAction.PREVIOUS_SONG)
        until { it.pedalSeen == listOf(a, b) && it.pedalKeys.size == 2 }

        vm.forgetPedalKey(a)
        val left = until { a !in it.pedalSeen }
        assertEquals(listOf(b), left.pedalSeen)
        assertEquals(mapOf(b to PedalAction.PREVIOUS_SONG), left.pedalKeys)
        assertEquals("back to what the key does by itself", PedalAction.default(a), PedalAction.of(a, left.pedalKeys))

        vm.onKey(PedalPress(a, down = true))
        assertEquals(listOf(b, a), until { a in it.pedalSeen }.pedalSeen)
    }

    @Test fun `the list asks before forgetting a button, and cancel keeps it`() {
        val forgotten = ArrayList<Int>()
        var look by mutableStateOf(Look(pedalSeen = listOf(a, b), pedalKeys = mapOf(b to PedalAction.NEXT_SONG)))
        compose.setContent {
            StagebookTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, look = look, midi = MidiState(supported = true)),
                    SettingsActions(onPedalForget = { code ->
                        forgotten += code
                        look = look.copy(pedalSeen = look.pedalSeen - code, pedalKeys = look.pedalKeys - code)
                    }),
                    section = SettingsSection.PEDAL,
                )
            }
        }
        val ask = "Esquecer este botão? Ele volta quando você apertar de novo."
        compose.onAllNodesWithContentDescription("Esquecer este botão").assertCountEquals(2)   // the default one too
        compose.onAllNodesWithContentDescription("Esquecer este botão")[1].performScrollTo().performClick()
        compose.onNodeWithText(ask).assertExists()
        assertEquals(emptyList<Int>(), forgotten)
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText(ask).assertDoesNotExist()
        assertEquals(emptyList<Int>(), forgotten)

        compose.onAllNodesWithContentDescription("Esquecer este botão")[1].performScrollTo().performClick()
        compose.onNodeWithText("Esquecer").performClick()
        compose.waitForIdle()
        assertEquals(listOf(b), forgotten)
        compose.onNodeWithText(ask).assertDoesNotExist()
        compose.onNodeWithContentDescription("Esquecer este botão").assertExists()   // one button left
    }

    // the list and the commands keep the names they were saved with: a file from before opens the same
    @Test fun `settings saved before still load their buttons`() {
        val old = """{"songs":[],"look":{"pedalSeen":[$a,$b],"pedalKeys":{"$b":"nextSong"}}}"""
        val look = LibraryCodec.decode(old) { "id" }.look
        assertEquals(listOf(a, b), look.pedalSeen)
        assertEquals(mapOf(b to PedalAction.NEXT_SONG), look.pedalKeys)
    }
}
