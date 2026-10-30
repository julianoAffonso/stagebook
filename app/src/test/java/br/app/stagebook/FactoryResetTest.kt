package br.app.stagebook

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import br.app.stagebook.domain.ResetCode
import br.app.stagebook.ui.settings.FactoryResetDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Apagar tudo (T26): só depois de digitar o código; dá para salvar o arquivo antes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FactoryResetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `code has four digits and matches without spaces`() {
        val code = ResetCode.generate(Random(7))
        assertEquals(4, code.length)
        assertTrue(code.all(Char::isDigit))
        assertTrue(ResetCode.matches(code, " $code "))
        assertFalse(ResetCode.matches(code, "0000".takeIf { it != code } ?: "1111"))
    }

    @Test fun `erase is only enabled with the right code - save first calls the backup`() {
        var reset = 0
        var backup = 0
        compose.setContent { StagebookTheme { FactoryResetDialog({ backup++ }, { reset++ }, {}, code = "4827") } }
        val apagar = compose.onAllNodesWithText("Apagar tudo")[1]   // [0] é o título da janela
        apagar.assertIsNotEnabled()
        compose.onNodeWithText("Código").performTextInput("4821")
        apagar.assertIsNotEnabled()
        compose.onNodeWithText("4821").performTextInput("")
        compose.onNodeWithText("Salvar arquivo antes (com tudo)").performClick()
        assertEquals(1, backup)
        assertEquals(0, reset)
    }

    @Test fun `with the right code it erases`() {
        var reset = 0
        compose.setContent { StagebookTheme { FactoryResetDialog({}, { reset++ }, {}, code = "4827") } }
        compose.onNodeWithText("Código").performTextInput("4827")
        val apagar = compose.onAllNodesWithText("Apagar tudo")[1]
        apagar.assertIsEnabled()
        apagar.performClick()
        assertEquals(1, reset)
    }
}
