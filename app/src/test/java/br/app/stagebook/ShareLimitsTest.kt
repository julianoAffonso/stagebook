package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.share.ShareLimits
import br.app.stagebook.ui.share.ShareOptions
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each way of sending has its own limit (user, 01/11): over it, that option is off with the reason. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ShareLimitsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `too many songs for a code - internet off with the reason, band still on`() {
        compose.setContent {
            StagebookTheme { ShareOptions(70, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 3, songs = 70, setlists = true)) }
        }
        compose.onNodeWithText("Desligue “Levar as músicas”", substring = true).assertExists()
        compose.onNodeWithText("Mandar para a banda", substring = true).assertExists()
    }

    @Test fun `too many setlists for the band - band off with the reason`() {
        compose.setContent {
            StagebookTheme { ShareOptions(0, online = true, onQr = null, onInternet = {}, band = 2, limits = ShareLimits(lists = 31, songs = 0, setlists = true)) }
        }
        compose.onNodeWithText("Passa do limite da banda", substring = true).assertExists()
    }
}
