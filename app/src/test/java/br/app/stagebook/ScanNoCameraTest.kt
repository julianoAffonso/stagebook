package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.setlists.ScanSetlistScreen
import br.app.stagebook.ui.setlists.ScanUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Scanning without the camera (device test B-21): an empty state that says why, with the button, and no "point the camera". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class ScanNoCameraTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `without the camera, the screen says why and the button asks for it`() {
        var asked = 0
        compose.setContent {
            StagebookTheme {
                ScanSetlistScreen(ScanUi.Scanning(), false, { asked++ }, {}, {}, { _, _ -> }, {}, { null }, {}, {})
            }
        }
        compose.onNodeWithText("Sem a câmera não dá para ler o QR.").assertExists()
        compose.onNodeWithText("Para ler o QR, o Stagebook precisa da câmera.").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Aponte a câmera", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Permitir a câmera").performClick()
        assertEquals(1, asked)
    }
}
