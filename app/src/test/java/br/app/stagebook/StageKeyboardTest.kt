package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.ChordShapePopup
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Keyboard with a capo chart: chords in the sounding key, the notice at the top, the first-time message and the line in
 * the window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageKeyboardTest {
    @get:Rule val compose = createComposeRule()

    private val text = "{key: G}\n{capo: 2}\n[G]linha [C]outra"

    @Test fun `keyboard - sounding key at the top and the message only the first time`() {
        var seen = 0
        val state = StageUiState(false, listOf(StageSong("Música", "", text, false)), look = Look(instrument = Instrument.KEYBOARD))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onKeyboardHintSeen = { seen++ }) } }
        compose.onNodeWithText("teclado · capo na 2ª").assertExists()
        compose.onNodeWithContentDescription("Tom: A", useUnmergedTree = true).assertExists()   // the sounding key
        compose.onNodeWithText("Esta cifra usa capo na 2ª casa", substring = true).assertExists()
        assertEquals(1, seen)
    }

    @Test fun `keyboard - a message already seen does not show again`() {
        val state = StageUiState(false, listOf(StageSong("Música", "", text, false)), look = Look(instrument = Instrument.KEYBOARD, keyboardCapoHintSeen = true))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }) } }
        compose.onNodeWithText("Esta cifra usa capo na 2ª casa", substring = true).assertDoesNotExist()
    }

    @Test fun `chord window on keyboard shows the chart's chord and the keyboard's`() {
        compose.setContent { StagebookTheme { ChordShapePopup("A", Instrument.KEYBOARD, text, {}, {}) } }
        compose.onNodeWithText("Na cifra é G, com capo na 2ª casa. No teclado, toque A.").assertExists()
    }
}
