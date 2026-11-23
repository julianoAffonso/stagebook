package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.components.stickyOffset
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stage previews stay on top while their cards pass, then go up with the page (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h700dp", application = android.app.Application::class)
class StickyPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the preview follows the scroll inside its group and stops at the group's end`() {
        assertEquals(0, stickyOffset(scrolled = 0, top = 100, bodyHeight = 500))      // the group not reached yet
        assertEquals(150, stickyOffset(scrolled = 250, top = 100, bodyHeight = 500))  // held at the top
        assertEquals(500, stickyOffset(scrolled = 900, top = 100, bodyHeight = 500))  // past the group: it goes up with it
    }

    private var section by androidx.compose.runtime.mutableStateOf(SettingsSection.LYRICS)

    private fun open(s: SettingsSection) {
        section = s
        compose.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false, look = Look()), SettingsActions(), Modifier.fillMaxSize(), section = section, onLeave = {}, onMenu = {}) }
        }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test fun `on Appearance the preview stays over the section titles and leaves before the PDF card`() {
        open(SettingsSection.LYRICS)
        val start = top("lyricsPreview")
        compose.onNodeWithText("Nomes de seção no idioma do app").performScrollTo()
        assertEquals("held on top while the section titles card passes", start, top("lyricsPreview"), 2f)
        // the PDF card isn't previewed: the preview stops at the end of its cards and never covers it
        compose.onNodeWithText("Inverter as cores do PDF").performScrollTo()
        val previewBottom = compose.onNodeWithTag("lyricsPreview").fetchSemanticsNode().boundsInRoot.bottom
        val pdfTop = compose.onNodeWithText("Inverter as cores do PDF").fetchSemanticsNode().boundsInRoot.top
        assertTrue("preview bottom $previewBottom over the PDF card at $pdfTop", previewBottom <= pdfTop)
    }

    @Test fun `the chords' look moved to Chords with its own preview`() {
        open(SettingsSection.CHORDS)
        compose.onNodeWithTag("chordsPreview").assertExists()
        compose.onNodeWithText("Acordes na letra").assertExists()
        compose.runOnUiThread { section = SettingsSection.LYRICS }
        compose.onNodeWithText("Acordes na letra").assertDoesNotExist()
    }
}
