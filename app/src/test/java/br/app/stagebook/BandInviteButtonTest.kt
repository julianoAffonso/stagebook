package br.app.stagebook

import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import br.app.stagebook.ui.band.BandActions
import br.app.stagebook.ui.band.BandDetail
import br.app.stagebook.ui.band.BandInviteShare
import br.app.stagebook.ui.band.BandUi
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.ui.components.rememberAppMessage
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings → Band: "Invite over the internet" sends a link through Android's share sheet (user, 07/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class BandInviteButtonTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `the invite button asks for an invite`() {
        var asked = 0
        compose.setContent { StagebookTheme { BandDetail(BandUi(name = "Tablet"), BandActions(onInvite = { asked++ }), internet = true) } }
        compose.onNodeWithText("Convidar pela internet").performClick()
        assertEquals(1, asked)
    }

    @Test fun `while the invite is being created the button waits`() {
        compose.setContent { StagebookTheme { BandDetail(BandUi(name = "Tablet", inviting = true), BandActions(), internet = true) } }
        compose.onNodeWithText("Enviando…").assertIsNotEnabled()
    }

    @Test fun `with the internet off there is no invite`() {
        compose.setContent { StagebookTheme { BandDetail(BandUi(name = "Tablet"), BandActions(), internet = false) } }
        compose.onNodeWithText("Convidar pela internet").assertDoesNotExist()
    }

    @Test fun `the created invite goes to the share sheet with the link`() {
        compose.setContent {
            StagebookTheme { CompositionLocalProvider(LocalAppMessage provides rememberAppMessage()) { BandInviteShare(flowOf("47-violao-lua"), "Ana") } }
        }
        compose.waitForIdle()
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val text = (chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))!!.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text, text.startsWith("Ana te chamou para a banda") && text.contains("https://stagebook.app.br/r#47-violao-lua"))
    }
}
