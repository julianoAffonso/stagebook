package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings is a page of its own (user, 15/11): its list has the back arrow, with the search as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h900dp-port-xxhdpi", application = android.app.Application::class)
class SettingsPageBackTest {
    @get:Rule val compose = createComposeRule()

    private fun page(onBack: (() -> Unit)?) = compose.setContent {
        StagebookTheme {
            SettingsScreen(SettingsUiState(loading = false, look = Look(), songCount = 3, setCount = 1), SettingsActions(), onBack = onBack)
        }
    }

    @Test fun `the list has the back arrow and tapping it leaves Settings`() {
        var left = 0
        page { left++ }
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, left)
    }

    @Test fun `the search is on the page`() {
        page { }
        compose.onNodeWithText("Ex.: midi, tela, pedal").assertIsDisplayed()
    }

    @Test fun `without a way back the list has no arrow, as when it was a tab`() {
        page(null)
        compose.onAllNodesWithContentDescription("Voltar").assertCountEquals(0)
    }
}
