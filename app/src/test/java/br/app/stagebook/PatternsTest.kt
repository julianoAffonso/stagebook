package br.app.stagebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.QuestionDialog
import br.app.stagebook.ui.components.SwitchRow
import br.app.stagebook.ui.components.rememberAppMessage
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * App usage patterns (T35): a short message is a snackbar, a switch toggles from the whole row, a question has two
 * answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PatternsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a message from any screen shows in the root snackbar and the next one replaces it`() {
        compose.setContent {
            StagebookTheme {
                val message = rememberAppMessage()
                CompositionLocalProvider(LocalAppMessage provides message) {
                    Column {
                        val show = LocalAppMessage.current
                        Text("copiar", Modifier.clickable { show.show("Código copiado") })
                        Text("enviar", Modifier.clickable { show.show("Mandado para 2 aparelhos") })
                        SnackbarHost(message.host)
                    }
                }
            }
        }
        compose.onNodeWithText("copiar").performClick()
        compose.onNodeWithText("Código copiado").assertExists()
        compose.onNodeWithText("enviar").performClick()
        compose.mainClock.advanceTimeBy(2_000)   // the previous one animates out before the next one comes in
        compose.onNodeWithText("Mandado para 2 aparelhos").assertExists()
        compose.onNodeWithText("Código copiado").assertDoesNotExist()
    }

    @Test fun `switch with an explanation - tapping the text also toggles`() {
        compose.setContent {
            StagebookTheme {
                var on by remember { mutableStateOf(true) }
                SwitchRow("Com as 12 músicas", on, { on = it }, detail = "Desligado, vão só os títulos")
            }
        }
        compose.onNodeWithText("Com as 12 músicas", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Desligado, vão só os títulos", useUnmergedTree = true).performClick()
        compose.onNode(androidx.compose.ui.test.isToggleable()).assertIsOff()
        compose.onNode(androidx.compose.ui.test.isToggleable()).performClick().assertIsOn()
    }

    @Test fun `question - each answer does its own thing`() {
        var answer: Boolean? = null
        compose.setContent {
            StagebookTheme {
                QuestionDialog("Tem tablatura", "Importar junto?", "Importar junto", { answer = true }, "Só a cifra", { answer = false })
            }
        }
        compose.onNodeWithText("Só a cifra").performClick()
        assertEquals(false, answer)
        compose.onNodeWithText("Importar junto").performClick()
        assertEquals(true, answer)
    }
}
