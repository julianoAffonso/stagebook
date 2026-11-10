package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import br.app.stagebook.domain.MixChannel
import br.app.stagebook.ui.components.MixerStrip
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a channel's name renames it, like a setlist's (user, 01/11); the settings icon still opens the rest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class MixerRenameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `tapping the name renames the channel`() {
        var changed: MixChannel? = null
        var edited = false
        compose.setContent { StagebookTheme { MixerStrip(0, MixChannel(name = "Backing"), false, { changed = it }, { edited = true }, upright = true) } }
        compose.onNodeWithText("Backing").performClick()
        compose.onAllNodesWithText("Backing")[1].performTextReplacement("Playback")
        compose.onNodeWithText("OK").performClick()
        assertEquals("Playback", changed?.name)
        assertEquals(false, edited)
    }

    @Test fun `an output this way lacks says so - through Android only 1 to 8`() {
        // XR18 on the Chuwi through Android (10/11): the channel on 9-10 stayed chosen and went silent with no word
        val android = br.app.stagebook.domain.OutputInfo("usb:1397:00d4", "X18/XR18", 8)
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.components.MixerChannelForm(0, MixChannel(), pair = true, canPair = true, output = 8, outputs = android, onChange = {}, onOutput = {})
            }
        }
        compose.onNodeWithText("Esta saída não toca agora. Pela rota do Android, o sistema só entrega as saídas 1 a 8 desta interface.").assertExists()
        assertEquals(true, android.lacks(8, pair = true))
        assertEquals(false, android.lacks(6, pair = true))
        assertEquals(false, br.app.stagebook.domain.OutputInfo("usb:1397:00d4", "X18/XR18", 18).lacks(8, pair = true))
    }
}
