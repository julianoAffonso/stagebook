package br.app.stagebook

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.MetronomeTest
import br.app.stagebook.ui.settings.MetronomeTestButton
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
}
