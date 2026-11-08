package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.setlists.ScanSetlistScreen
import br.app.stagebook.ui.setlists.ScanUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A setlist from the band has no QR behind it: reading again would open the camera for nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ScanAgainTest {
    @get:Rule val compose = createComposeRule()

    private fun review(canScanAgain: Boolean) = compose.setContent {
        StagebookTheme {
            ScanSetlistScreen(
                ScanUi.Review("Show", emptyList()), true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {},
                canScanAgain = canScanAgain,
            )
        }
    }

    @Test fun `a QR can be read again`() {
        review(canScanAgain = true)
        compose.onNodeWithText("Ler de novo").assertExists()
    }

    @Test fun `what came from the band can't`() {
        review(canScanAgain = false)
        compose.onAllNodesWithText("Ler de novo").assertCountEquals(0)
    }
}
