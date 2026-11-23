package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → Lyrics and chords has the PDF card with the colour switch, the same one as the stage's button (user, 14/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h1800dp-port-xxhdpi", application = android.app.Application::class)
class PdfInvertSettingTest {
    @get:Rule val compose = createComposeRule()

    private fun show(look: Look, on: (Boolean) -> Unit = {}) = compose.setContent {
        StagebookTheme {
            SettingsScreen(
                SettingsUiState(loading = false, look = look, songCount = 3, setCount = 1), SettingsActions(onPdfInvert = on),
                section = SettingsSection.LYRICS,
            )
        }
    }

    @Test fun `the PDF card has the switch and turning it asks for the opposite`() {
        var asked: Boolean? = null
        show(Look(pdfInvert = false)) { asked = it }
        compose.onNodeWithText("PDF").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inverter as cores do PDF").performScrollTo().performClick()
        assertEquals(true, asked)
    }

    @Test fun `turned on it is turned off by the same switch`() {
        var asked: Boolean? = null
        show(Look(pdfInvert = true)) { asked = it }
        compose.onNodeWithText("Inverter as cores do PDF").performScrollTo().performClick()
        assertEquals(false, asked)
    }
}
