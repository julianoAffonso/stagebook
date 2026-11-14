package br.app.stagebook

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import br.app.stagebook.ui.editor.EditorMode
import br.app.stagebook.ui.editor.EditorPosition
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's previous/next (the arrows and "3 / 12" at the top), the question with three answers when something is
 * unsaved, and the strip of tools in one line on a narrow phone (360 dp, system text 1.15, real text sizes).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class EditorStepsTest {
    @get:Rule val compose = createComposeRule()

    @Before fun bigText() = RuntimeEnvironment.setFontScale(1.15f)

    private val chordsText = "{key: G}\n[G]um [D]dois [Am]três [C]quatro [Em]cinco [F]seis [Bm]sete"

    private fun song(unsaved: Boolean = false) = SongEditorUiState(false, EditorMode.EDIT, "Evidências", "Chitãozinho & Xororó", "", chordsText, unsaved = unsaved)

    private fun show(
        position: EditorPosition?, unsaved: Boolean = false,
        onGo: (Int) -> Unit = {}, onSaveGo: (Int) -> Unit = {},
    ) = compose.setContent {
        StagebookTheme { SongEditorScreen(song(unsaved), {}, {}, {}, {}, {}, {}, {}, position = position, onGo = onGo, onSaveGo = onSaveGo) }
    }

    @Test fun `with a queue the top shows the place and both arrows`() {
        show(EditorPosition(2, 12))
        compose.onNodeWithText("3 / 12").assertExists()
        compose.onNodeWithContentDescription("Música anterior").assertIsEnabled()
        compose.onNodeWithContentDescription("Próxima música").assertIsEnabled()
    }

    @Test fun `without a queue there are no arrows`() {
        show(null)
        compose.onAllNodesWithText("3 / 12").assertCountEquals(0)
        compose.onAllNodesWithText("/", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Música anterior").assertDoesNotExist()
        compose.onNodeWithContentDescription("Próxima música").assertDoesNotExist()
    }

    @Test fun `at the first song previous is off, at the last next is off`() {
        show(EditorPosition(0, 12))
        compose.onNodeWithContentDescription("Música anterior").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Próxima música").assertIsEnabled()
    }

    @Test fun `at the last song next is off`() {
        show(EditorPosition(11, 12))
        compose.onNodeWithContentDescription("Música anterior").assertIsEnabled()
        compose.onNodeWithContentDescription("Próxima música").assertIsNotEnabled()
    }

    @Test fun `nothing unsaved - the arrow goes straight there`() {
        val went = mutableListOf<Int>()
        show(EditorPosition(2, 12), onGo = { went += it })
        compose.onNodeWithContentDescription("Próxima música").performClick()
        compose.onNodeWithContentDescription("Música anterior").performClick()
        assertEquals(listOf(1, -1), went)
        compose.onAllNodesWithText("Salvar e continuar").assertCountEquals(0)
    }

    @Test fun `unsaved - asks, and Save and continue saves first`() {
        val went = mutableListOf<Int>(); val saved = mutableListOf<Int>()
        show(EditorPosition(2, 12), unsaved = true, onGo = { went += it }, onSaveGo = { saved += it })
        compose.onNodeWithContentDescription("Próxima música").performClick()
        compose.onNodeWithText("Salvar e continuar").assertExists()
        compose.onNodeWithText("Descartar").assertExists()
        compose.onNodeWithText("Cancelar").assertExists()
        assertTrue(went.isEmpty() && saved.isEmpty())
        compose.onNodeWithText("Salvar e continuar").performClick()
        assertEquals(listOf(1), saved)
        assertTrue(went.isEmpty())
    }

    @Test fun `unsaved - Discard goes without saving`() {
        val went = mutableListOf<Int>(); val saved = mutableListOf<Int>()
        show(EditorPosition(2, 12), unsaved = true, onGo = { went += it }, onSaveGo = { saved += it })
        compose.onNodeWithContentDescription("Música anterior").performClick()
        compose.onNodeWithText("Descartar").performClick()
        assertEquals(listOf(-1), went)
        assertTrue(saved.isEmpty())
    }

    @Test fun `unsaved - Cancel stays, and the question is gone`() {
        val went = mutableListOf<Int>(); val saved = mutableListOf<Int>()
        show(EditorPosition(2, 12), unsaved = true, onGo = { went += it }, onSaveGo = { saved += it })
        compose.onNodeWithContentDescription("Próxima música").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(went.isEmpty() && saved.isEmpty())
        compose.onAllNodesWithText("Salvar e continuar").assertCountEquals(0)
    }

    private fun lines(text: String): Int {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
        val out = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(out)
        return out.first().lineCount
    }

    private fun node(label: String, description: Boolean = false) =
        (if (description) compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(label), useUnmergedTree = true)
        else compose.onAllNodesWithText(label, useUnmergedTree = true))
            .fetchSemanticsNodes().first { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText) == null }

    @Test fun `the tools are one line on a 360 dp phone with bigger text, nothing cut`() {
        show(null)
        compose.waitForIdle()
        val items = listOf(node("Marca-texto", true), node("Acorde", true), node("G"), node("D"), node("Am"), node("Verso"), node("Refrão"))
        // every button sits on the same line (a second line would put some lower)
        val first = items.first()
        items.forEach { n -> assertEquals("same line", first.positionInRoot.y + first.size.height / 2f, n.positionInRoot.y + n.size.height / 2f, 3f) }
        listOf("G", "D", "Am", "Verso", "Refrão").forEach { assertEquals("\"$it\" in one line", 1, lines(it)) }
    }

    // user's order: preview, highlighter, chords (with the new-chord button), then the sections; the chords are in view without scrolling
    @Test fun `chords come before the sections, and are in view without scrolling`() {
        show(null)
        compose.waitForIdle()
        val screen = 360f * compose.density.density
        val preview = node("Ver como fica no palco", true)
        val highlight = node("Marca-texto", true)
        val add = node("Acorde", true)
        val g = node("G"); val d = node("D")
        val verse = node("Verso")
        assertTrue(preview.positionInRoot.x < highlight.positionInRoot.x && highlight.positionInRoot.x < add.positionInRoot.x)
        assertTrue(add.positionInRoot.x < g.positionInRoot.x && g.positionInRoot.x < d.positionInRoot.x && d.positionInRoot.x < verse.positionInRoot.x)
        listOf(add, g, d).forEach { n ->
            assertTrue("at ${n.positionInRoot.x}..${n.positionInRoot.x + n.size.width} of $screen", n.positionInRoot.x >= 0f && n.positionInRoot.x + n.size.width <= screen)
        }
    }

    @Test fun `the strip stays on the lyrics screen, not on the details`() {
        show(null)
        compose.onNodeWithContentDescription("Marca-texto").assertExists()
        compose.onNode(androidx.compose.ui.test.hasText("Dados") and androidx.compose.ui.test.hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Verso").assertCountEquals(0)
        compose.onNodeWithContentDescription("Marca-texto").assertDoesNotExist()
    }
}
