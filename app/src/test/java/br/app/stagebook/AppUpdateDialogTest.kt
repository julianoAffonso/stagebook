package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.UpdateOffer
import br.app.stagebook.ui.AppUpdateDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Only the "Not now" button puts the version off: leaving the window any other way just closes it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class AppUpdateDialogTest {
    @get:Rule val compose = createComposeRule()

    private var later = false
    private var closed = false

    private fun show() = compose.setContent {
        StagebookTheme { AppUpdateDialog(UpdateOffer.Available("0.1.733"), {}, { later = true }, {}, { closed = true }) }
    }

    @Test fun `not now puts the version off`() {
        show()
        compose.onNodeWithText("Agora não").performClick()
        assertTrue(later)
        assertFalse(closed)
    }

    @Test fun `back only closes the window`() {
        show()
        compose.waitForIdle()
        ShadowDialog.getLatestDialog().onBackPressed()
        compose.waitForIdle()
        assertTrue(closed)
        assertFalse(later)
    }
}
