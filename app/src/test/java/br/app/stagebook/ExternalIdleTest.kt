package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ExternalImageStore
import br.app.stagebook.data.model.ExternalIdle
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.components.LOGO_TAG
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.stage.EXTERNAL_IMAGE_TAG
import br.app.stagebook.ui.stage.ExternalStageContent
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The external screen with the stage closed: the logo by default, black, or the person's image (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExternalIdleTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun picture(): File = File(context.filesDir, "foto.png").also { f ->
        f.outputStream().use { Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `with the stage closed the logo shows, black shows nothing, and the image shows itself`() {
        var look by mutableStateOf(Look())
        val image = picture()
        compose.setContent { StagebookTheme { ExternalStageContent(null, look, { File(it) }, Modifier.fillMaxSize(), idleImage = image) } }
        compose.onNodeWithTag(LOGO_TAG).assertExists()
        compose.runOnUiThread { look = look.copy(externalIdle = ExternalIdle.BLACK) }
        compose.onNodeWithTag(LOGO_TAG).assertDoesNotExist()
        compose.onNodeWithTag(EXTERNAL_IMAGE_TAG).assertDoesNotExist()
        compose.runOnUiThread { look = look.copy(externalIdle = ExternalIdle.IMAGE) }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(EXTERNAL_IMAGE_TAG)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun `an image that is gone leaves the screen black`() {
        compose.setContent { StagebookTheme { ExternalStageContent(null, Look(externalIdle = ExternalIdle.IMAGE), { File(it) }, idleImage = File(context.filesDir, "nada.png")) } }
        compose.waitForIdle()
        compose.onNodeWithTag(EXTERNAL_IMAGE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(LOGO_TAG).assertDoesNotExist()
    }

    @Test fun `only an image is kept, and not a huge file`() {
        assertTrue(ExternalImageStore.isImage(picture()))
        val text = File(context.filesDir, "texto.png").apply { writeText("não sou imagem") }
        assertFalse(ExternalImageStore.isImage(text))
        assertFalse(ExternalImageStore.copyAtMost(ByteArray(2048).inputStream(), java.io.ByteArrayOutputStream(), 1024))
        assertTrue(ExternalImageStore.copyAtMost(ByteArray(512).inputStream(), java.io.ByteArrayOutputStream(), 1024))
        // only the image in use is kept: choosing the logo or black deletes it
        val store = ExternalImageStore(context)
        picture().copyTo(store.file, overwrite = true)
        store.forget()
        assertFalse(store.file.exists())
    }

    @Test fun `Settings chooses what shows with the stage closed`() {
        var chosen: ExternalIdle? = null
        var state by mutableStateOf(SettingsUiState(loading = false))
        compose.setContent {
            StagebookTheme {
                SettingsScreen(state, SettingsActions(onExternalIdle = { chosen = it }), Modifier.fillMaxSize(), section = SettingsSection.BAND, onLeave = {}, onMenu = {})
            }
        }
        compose.onNodeWithText("Logo do Stagebook").performScrollTo().performClick()
        compose.onNodeWithText("Tela preta").performClick()
        assertEquals(ExternalIdle.BLACK, chosen)
        compose.onNodeWithText("Trocar a imagem").assertDoesNotExist()
        compose.runOnUiThread { state = state.copy(look = state.look.copy(externalIdle = ExternalIdle.IMAGE)) }
        compose.onNodeWithText("Trocar a imagem").performScrollTo().performClick()
        assertEquals(ExternalIdle.IMAGE, chosen)
        // Nothing: Android mirrors the tablet, the choices of what the TV shows go away
        compose.runOnUiThread { state = state.copy(look = state.look.copy(externalShow = br.app.stagebook.data.model.ExternalShow.OFF)) }
        compose.onNodeWithText("Com o palco fechado").assertDoesNotExist()
        compose.onNodeWithText("Trocar a imagem").assertDoesNotExist()
        compose.onNodeWithText("Tela externa").performScrollTo().assertExists()
    }
}
