package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** Teclado com cifra de capo: acordes no tom que soa, o aviso no topo, a mensagem da primeira vez e a linha na janela. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageKeyboardTest {
    @get:Rule val compose = createComposeRule()

    private val text = "{key: G}\n{capo: 2}\n[G]linha [C]outra"

    @Test fun `teclado - tom que soa no topo e a mensagem só na primeira vez`() {
        var seen = 0
        val state = StageUiState(false, listOf(StageSong("Música", "", text, false)), look = Look(instrument = Instrument.TECLADO))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onKeyboardHintSeen = { seen++ }) } }
        compose.onNodeWithText("teclado · capo na 2ª").assertExists()
        compose.onNodeWithText("A", useUnmergedTree = true).assertExists()   // o tom que soa
        compose.onNodeWithText("Esta cifra usa capo na 2ª casa", substring = true).assertExists()
        assertEquals(1, seen)
    }

    @Test fun `teclado - mensagem já vista não aparece de novo`() {
        val state = StageUiState(false, listOf(StageSong("Música", "", text, false)), look = Look(instrument = Instrument.TECLADO, keyboardCapoHintSeen = true))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }) } }
        compose.onNodeWithText("Esta cifra usa capo na 2ª casa", substring = true).assertDoesNotExist()
    }

    @Test fun `janela do acorde no teclado mostra o da cifra e o do teclado`() {
        compose.setContent { StagebookTheme { ChordShapePopup("A", Instrument.TECLADO, text, {}, {}) } }
        compose.onNodeWithText("Na cifra é G, com capo na 2ª casa. No teclado, toque A.").assertExists()
    }
}
