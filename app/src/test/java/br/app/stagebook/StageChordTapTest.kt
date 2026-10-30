package br.app.stagebook

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tocar num acorde do palco abre o desenho dele; tocar na letra continua virando a página. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageChordTapTest {
    @get:Rule val compose = createComposeRule()

    private val songs = listOf(StageSong("Teste", "", "[G]Primeira linha de letra comprida\n[D]Segunda linha", false, id = "s1"), StageSong("Outra", "", "x", false))
    private val saved = mutableListOf<String>()
    private var position = -1 to -1

    private fun show(look: Look) = compose.setContent {
        StagebookTheme {
            StageScreen(
                StageUiState(loading = false, songs = songs, look = look),
                0, 0, { s, p -> position = s to p },
                onSaveShape = { _, chord, define -> saved += "$chord|$define" },
            )
        }
    }

    // o acorde G fica em cima do começo da primeira linha de letra
    private fun tapFirstChord() {
        val lyric = compose.onNodeWithText("Primeira linha", substring = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(Offset(lyric.left + 6f, lyric.top + 6f)) }
        compose.waitForIdle()
    }

    @Test fun `tocar no acorde abre o desenho e Usar esta guarda a variação`() {
        show(Look(fontSp = 34f))
        tapFirstChord()
        compose.onNodeWithText("Usar esta").performClick()
        compose.waitForIdle()
        assertEquals(1, saved.size)
        assertTrue(saved.single(), saved.single().startsWith("G|{define-guitar: G "))
        assertEquals("não virou a página", -1 to -1, position)
    }

    @Test fun `com o desenho desligado nos ajustes o toque no acorde não abre nada`() {
        show(Look(fontSp = 34f, chordTap = false))
        tapFirstChord()
        assertEquals(0, compose.onAllNodesWithText("Usar esta").fetchSemanticsNodes().size)
    }

    @Test fun `no perfil só letra o toque vira a página`() {
        show(Look(fontSp = 34f, chords = false))
        tapFirstChord()
        assertEquals(0, compose.onAllNodesWithText("Usar esta").fetchSemanticsNodes().size)
    }

    @Test fun `toque longo no botão da faixa abre o menu dela e não oferece editar a linha`() {
        val withTrack = listOf(StageSong("Teste", "", "[G]Primeira linha de letra comprida\n[D]Segunda linha", false, id = "s1", track = "s1.ogg"))
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(loading = false, songs = withTrack, look = Look(fontSp = 34f, playerMode = true)), 0, 0, { _, _ -> }) }
        }
        compose.onNodeWithContentDescription("Tocar o backing track").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Trocar o backing track").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Editar esta linha").fetchSemanticsNodes().size)
    }
}
