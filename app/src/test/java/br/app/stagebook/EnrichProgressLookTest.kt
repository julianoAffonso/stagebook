package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.data.LibraryEnricher
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
import org.robolectric.annotation.GraphicsMode

/** While looking up, the card keeps its height as the count grows (user, 22/11: at 10 the line broke and the page jumped). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w384dp-h832dp-450dpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EnrichProgressLookTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the Stop button stays put from 9 to 10 and from 99 to 100 (the S26's width)`() {
        var done by mutableStateOf(9)
        var total by mutableStateOf(62)
        compose.setContent {
            StagebookTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, songCount = 199, missingData = total, enrich = LibraryEnricher.Progress(running = true, done = done, total = total)),
                    SettingsActions(), Modifier.fillMaxSize(), section = SettingsSection.LIBRARY, onLeave = {}, onMenu = {},
                )
            }
        }
        fun stopTop() = compose.onNodeWithText("Parar").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Parar").performScrollTo()
        val at9 = stopTop()
        compose.runOnUiThread { done = 10 }
        compose.waitForIdle()
        assertEquals(at9, stopTop(), 0.5f)
        compose.runOnUiThread { done = 99; total = 199 }
        compose.waitForIdle()
        val at99 = stopTop()
        compose.runOnUiThread { done = 100 }
        compose.waitForIdle()
        assertEquals(at99, stopTop(), 0.5f)
        compose.onNodeWithText("Pode sair desta tela.").assertExists()
    }
}
