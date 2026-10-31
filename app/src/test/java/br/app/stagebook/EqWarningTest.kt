package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
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

    private val notice = "Nenhum canal passa pelo equalizador"
    private val turnedOn = mutableListOf<Boolean>()

    private fun card(mixer: MixerConfig) = compose.setContent {
        StagebookTheme { EqCard(mixer, { turnedOn += it.eqOn }, { MixerLevels.SILENT }, monitoring = false) }
    }

    @Test fun `turning it on with no channel shows a notice and stays off`() {
        card(MixerConfig(channels = 2, eqOn = false))
        compose.onNodeWithText("Equalizador ligado").performClick()
        compose.onNodeWithText(notice).assertExists()
        org.junit.Assert.assertTrue(turnedOn.isEmpty())   // nothing saved: the switch stays off
    }

    @Test fun `turning it on with a channel going through it turns it on`() {
        card(MixerConfig(channels = 2, eqOn = false, strips = List(MixerConfig.MAX) { i -> MixChannel(eq = i == 1) }))
        compose.onNodeWithText("Equalizador ligado").performClick()
        compose.onAllNodesWithText(notice).assertCountEquals(0)
        org.junit.Assert.assertEquals(listOf(true), turnedOn)
    }
}
