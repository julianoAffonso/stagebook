package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import br.app.stagebook.domain.MixChannel
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.MixerLevels
import br.app.stagebook.ui.settings.EqCard
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Turning the EQ on with no channel going through it: a notice, and the switch stays off (user, 31/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class EqWarningTest {
    @get:Rule val compose = createComposeRule()

    private val hint = "Nenhum canal passa pelo equalizador. No mixer, toque num canal e ligue \"Equalizador ligado\"."

    private fun card(mixer: MixerConfig) = compose.setContent {
        StagebookTheme { EqCard(mixer, {}, { MixerLevels.SILENT }, monitoring = false) }
    }

    @Test fun `with no channel the curve stays and a hint says what's missing`() {
        card(MixerConfig(channels = 2))
        compose.onNodeWithText(hint).assertExists()
        compose.onNodeWithText("Zerar").assertIsEnabled()
    }

    @Test fun `with a channel going through it there's no hint and no switch`() {
        card(MixerConfig(channels = 2, strips = List(MixerConfig.MAX) { i -> MixChannel(eq = i == 1) }))
        compose.onAllNodesWithText(hint).assertCountEquals(0)
        compose.onAllNodesWithText("Equalizador ligado").assertCountEquals(0)
    }
}
