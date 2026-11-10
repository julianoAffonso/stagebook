package br.app.stagebook

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.MetronomeTest
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.MetronomeTestButton
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class MetronomeTestButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `toggle starts at 100 bpm and stops`() {
        val log = mutableListOf<String>()
        val test = MetronomeTest({ log += "start $it" }, { log += "stop" })
        test.toggle(); assertTrue(test.playing.value)
        test.toggle(); assertFalse(test.playing.value)
        test.stop()   // not playing: nothing more
        assertEquals(listOf("start 100", "stop"), log)
    }

    @Test fun `button switches its label and stops on leaving`() {
        val log = mutableListOf<String>()
        val test = MetronomeTest({ log += "start" }, { log += "stop" })
        var shown by mutableStateOf(true)
        compose.setContent {
            StagebookTheme {
                if (shown) {
                    val playing by test.playing.collectAsState()
                    MetronomeTestButton(playing, test::toggle, test::stop)
                }
            }
        }
        compose.onNodeWithText("Testar o metrônomo").assertExists()
        compose.onNodeWithText("Testar o metrônomo").performClick()
        compose.onNodeWithText("Parar o teste").assertExists()
        shown = false
        compose.waitForIdle()
        assertEquals(listOf("start", "stop"), log)
        assertFalse(test.playing.value)
    }

    @Test fun `device output says the order it picks - headphones, Bluetooth, speaker`() {
        // a Bluetooth speaker near the desk got the click and it looked like the USB (XR18 test, 10/11)
        compose.setContent {
            StagebookTheme {
                SettingsScreen(SettingsUiState(loading = false, look = Look(metroSound = true, playerMode = true)), SettingsActions(), section = SettingsSection.METRONOME)
            }
        }
        compose.onNodeWithText("Aparelho").assertExists()
        compose.onNodeWithText("No aparelho ele vai para o fone, depois o Bluetooth, depois o alto-falante. Usa o primeiro que estiver conectado.").assertExists()
    }

    @Test fun `changing the output stops a test that is playing`() {
        var stopped = 0
        var picked: br.app.stagebook.domain.MetronomeOut? = null
        val desk = br.app.stagebook.domain.OutputInfo("usb:1397:00d4", "X18/XR18", 18)
        val look = Look(metroSound = true, playerMode = true, mixer = br.app.stagebook.domain.MixerConfig(output = desk))
        compose.setContent {
            StagebookTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, look = look),
                    SettingsActions(clickTesting = true, onClickTestStop = { stopped++ }, onMetronomeOut = { picked = it }),
                    section = SettingsSection.METRONOME,
                )
            }
        }
        compose.onNodeWithText("Aparelho").performClick()
        compose.onNodeWithText("X18/XR18 · Saídas 9-10").performClick()
        assertEquals(1, stopped)
        assertEquals(br.app.stagebook.domain.MetronomeOut(8, true), picked)
    }
}
