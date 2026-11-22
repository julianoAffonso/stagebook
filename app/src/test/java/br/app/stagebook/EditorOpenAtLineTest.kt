package br.app.stagebook

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.LyricsView
import br.app.stagebook.ui.editor.EditorMode
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor opened from the stage by holding a line (route SongEditor with a cursor): it opens on the lyrics screen, the
 * cursor is at the end of the held line even in a long song with the line near the end, and that line is in view (the
 * field scrolled to it). The held line is the only one with a highlight in a colour nothing else uses, so seeing that
 * colour in the field's picture means the line is on screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class EditorOpenAtLineTest {
    @get:Rule val compose = createComposeRule()

    private val marker = 0xFFFF00FF.toInt()

    // 40 lines; line 36 is the held one; a line with only chords every 4th (hidden in the lyrics only profile)
    private val lines = buildList {
        add("{key: G}"); add("{capo: 2}")
        for (i in 1..40) {
            if (i % 4 == 0) add("[G] [D] [Em]")
            add(if (i == 36) "[Am]==Linha segurada== fim" else "[G]Linha $i da letra [D]comprida")
        }
    }
    private val song = lines.joinToString("\n")
    private val heldAt = song.indexOf("[Am]==Linha segurada==")   // what the stage sends: the start of the held line

    private fun open(look: Look) {
        compose.setContent {
            StagebookTheme {
                SongEditorScreen(SongEditorUiState(false, EditorMode.EDIT, "Hazard", "Richard Marx", "", song), {}, {}, {}, {}, {}, {}, {}, cursor = heldAt, look = look)
            }
        }
        compose.waitForIdle()
    }

    private fun check(look: Look) {
        open(look)
        // (a) the lyrics screen, not the details
        val field = compose.onNode(hasSetTextAction() and hasText(if (look.chords) "Letra e cifra" else "Letra"))
        field.assertExists()
        compose.onNode(hasSetTextAction() and hasText("Nome da música")).assertDoesNotExist()
        // (b) the cursor at the end of the held line, in the text the field shows
        val shownText = Chords.hideMeta(if (look.chords) song else LyricsView.view(song))
        val lineStart = shownText.indexOf("Linha segurada").let { shownText.lastIndexOf('\n', it) + 1 }
        val lineEnd = shownText.indexOf('\n', lineStart).let { if (it < 0) shownText.length else it }
        val range = field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(lineEnd), range)
        // (c) the held line is on screen
        val pixels = field.captureToImage().let { img -> IntArray(img.width * img.height).also { img.readPixels(it) } }
        val seen = pixels.count { it == marker }
        assertTrue("the held line's highlight isn't in view ($seen pixels)", seen > 0)
    }

    @Test fun `from the stage with chords - the lyrics screen, cursor at the end of the held line, and it is in view`() = check(Look(highlightColor = marker))

    @Test fun `from the stage in the lyrics only profile - hidden chord lines are discounted`() = check(Look(chords = false, highlightColor = marker))
}
