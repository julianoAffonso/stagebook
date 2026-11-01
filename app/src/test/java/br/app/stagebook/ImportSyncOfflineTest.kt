package br.app.stagebook

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.share.ImportButton
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Without Wi-Fi the band can't be reached (device test B-26): "Sync with the band" is off and says why. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ImportSyncOfflineTest {
    @get:Rule val compose = createComposeRule()

    private fun open(localNet: Boolean) {
        compose.setContent { StagebookTheme { ImportButton(false, {}, {}, onSync = {}, localNet = localNet) } }
        compose.onNodeWithText("Importar").performClick()
    }

    @Test fun `without Wi-Fi the sync item is disabled with the reason`() {
        open(false)
        compose.onNodeWithText("Sincronizar com a banda (sem Wi-Fi)").assertIsNotEnabled()
    }

    @Test fun `on Wi-Fi without internet the band still syncs`() {
        open(true)
        compose.onNodeWithText("Sincronizar com a banda").assertIsEnabled()
    }
}
