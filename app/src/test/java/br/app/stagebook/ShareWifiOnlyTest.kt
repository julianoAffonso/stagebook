package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.share.ShareLimits
import br.app.stagebook.ui.share.ShareOptions
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Only this Wi-Fi" when sending to the band: nothing over the internet (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ShareWifiOnlyTest {
    @get:Rule val compose = createComposeRule()

    private var sent: Pair<Boolean, Boolean>? = null

    private fun show(internetOn: Boolean = true) = compose.setContent {
        StagebookTheme { ShareOptions(3, online = true, onQr = null, onInternet = {}, band = 2, onBand = { s, w -> sent = s to w }, internetOn = internetOn, limits = ShareLimits(lists = 1, songs = 3)) }
    }

    @Test fun `the band gets it by Wi-Fi and internet unless Only this Wi-Fi is on`() {
        show()
        compose.onNodeWithText("Mandar para a banda", substring = true).performClick()
        assertEquals(true to false, sent)
        compose.onNodeWithText("Só deste Wi-Fi").performClick()
        compose.onNodeWithText("Mandar para a banda", substring = true).performClick()
        assertEquals(true to true, sent)
    }

    @Test fun `with the internet off there is no switch, it is Wi-Fi only already`() {
        show(internetOn = false)
        compose.onNodeWithText("Só deste Wi-Fi").assertDoesNotExist()
    }

    // the last choice is kept on this device (user, 22/11)
    @Test fun `Only this Wi-Fi is remembered the next time the sheet opens`() {
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().getSharedPreferences("share", 0).edit().clear().commit()
        var open by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            StagebookTheme { if (open) ShareOptions(3, online = true, onQr = null, onInternet = {}, band = 2, onBand = { s, w -> sent = s to w }, limits = ShareLimits(lists = 1, songs = 3)) }
        }
        compose.onNodeWithText("Só deste Wi-Fi").performClick()
        open = false
        compose.waitForIdle()
        open = true
        compose.waitForIdle()
        compose.onNodeWithText("Mandar para a banda", substring = true).performClick()
        assertEquals(true to true, sent)
    }
}
