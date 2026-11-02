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
}
