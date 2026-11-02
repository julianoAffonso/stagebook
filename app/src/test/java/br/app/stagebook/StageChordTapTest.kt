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

/** Tapping a chord on stage opens its diagram; tapping the lyrics still turns the page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
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

    // the G chord sits above the start of the first lyrics line
    private fun tapFirstChord() {
        val lyric = compose.onNodeWithText("Primeira linha", substring = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(Offset(lyric.left + 6f, lyric.top + 6f)) }
        compose.waitForIdle()
    }

    @Test fun `tapping a chord opens the diagram and Use this one keeps the shape`() {
        show(Look(fontSp = 34f))
        tapFirstChord()
        compose.onNodeWithText("Usar esta").performClick()
        compose.waitForIdle()
        assertEquals(1, saved.size)
        assertTrue(saved.single(), saved.single().startsWith("G|{define-guitar: G "))
        assertEquals("não virou a página", -1 to -1, position)
    }

    @Test fun `a tap on the side of a chords-only line turns the page, it does not open the last chord`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = listOf(StageSong("Teste", "", "[G]  [D]\nPrimeira linha de letra comprida\nSegunda", false, id = "s1"), songs[1]), look = Look(fontSp = 34f)),
                    0, 0, { s, p -> position = s to p },
                )
            }
        }
        val block = compose.onNodeWithText("Primeira linha", substring = true).fetchSemanticsNode().boundsInRoot
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, block.top + 6f)) }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Usar esta").fetchSemanticsNodes().size)
        assertTrue("virou a página: $position", position != -1 to -1)
    }

    // on the left, to go back a page: the first page has nowhere to go, so only the diagram matters here
    @Test fun `a tap below the last chords-only line does not open its chord`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = listOf(StageSong("Teste", "", "Primeira linha de letra comprida\nSegunda\n[G]  [D]", false, id = "s1"), songs[1]), look = Look(fontSp = 34f)),
                    0, 0, { s, p -> position = s to p },
                )
            }
        }
        val block = compose.onNodeWithText("Primeira linha", substring = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(Offset(block.left + 6f, block.bottom - 6f)) }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Usar esta").fetchSemanticsNodes().size)
    }

    @Test fun `holding a line offers the highlighter, which marks the word under the finger`() {
        var marked: List<Any>? = null
        var marks = 0
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = songs, look = Look(fontSp = 34f)),
                    0, 0, { s, p -> position = s to p },
                    onHighlightWords = { id, line, shown, from, to, _ -> marked = listOf(id, line, shown, from, to); marks++ },
                )
            }
        }
        val lyric = compose.onNodeWithText("Segunda linha", substring = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { longClick(Offset(lyric.left + 40f, lyric.top + 180f)) }
        compose.waitForIdle()
        compose.onNodeWithText("Editar esta linha").assertExists()
        compose.onNodeWithText("Marca-texto").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Passe o dedo", substring = true).assertExists()
        // a tap on a word of the second line: it's marked, the page doesn't turn
        compose.onRoot().performTouchInput { click(Offset(lyric.left + 40f, lyric.top + 180f)) }
        compose.waitForIdle()
        val m = marked!!
        assertEquals("s1", m[0])
        assertEquals(1, m[1])
        assertTrue("linha mostrada: ${m[2]}", (m[2] as String).contains("Segunda"))
        assertEquals("não virou a página", -1 to -1, position)
        compose.onNodeWithText("Pronto").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Passe o dedo", substring = true).assertDoesNotExist()
        // below the last line there is no word: nothing is marked
        compose.onRoot().performTouchInput { click(Offset(lyric.left + 40f, lyric.bottom - 20f)) }
        compose.waitForIdle()
        assertEquals(1, marks)
        // Done only closes: it doesn't highlight the word under the bar (audit 02/11)
        assertEquals(1, marks)
    }

    @Test fun `with the diagram turned off in settings, tapping a chord opens nothing`() {
        show(Look(fontSp = 34f, chordTap = false))
        tapFirstChord()
        assertEquals(0, compose.onAllNodesWithText("Usar esta").fetchSemanticsNodes().size)
    }

    @Test fun `in the lyrics only profile, the tap turns the page`() {
        show(Look(fontSp = 34f, chords = false))
        tapFirstChord()
        assertEquals(0, compose.onAllNodesWithText("Usar esta").fetchSemanticsNodes().size)
    }

    @Test fun `long press on the track button opens its menu and does not offer editing the line`() {
        val withTrack = listOf(StageSong("Teste", "", "[G]Primeira linha de letra comprida\n[D]Segunda linha", false, id = "s1", track = "s1.ogg"))
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(loading = false, songs = withTrack, look = Look(fontSp = 34f, playerMode = true)), 0, 0, { _, _ -> }) }
        }
        compose.onNodeWithContentDescription("Tocar o backing track").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Trocar o backing track").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Editar esta linha").fetchSemanticsNodes().size)
    }

    // live, the track playing goes on when the song changes: on a song without a track the button stays, to stop it
    @Test fun `with a track playing, a song without a track still shows the stop button`() {
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(loading = false, songs = songs, look = Look(fontSp = 34f, playerMode = true)), 0, 0, { _, _ -> }, playing = true) }
        }
        compose.onNodeWithContentDescription("Parar o backing track e voltar ao início").assertExists()
    }
}
