package br.app.stagebook

import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import br.app.stagebook.domain.MidiKind
import br.app.stagebook.domain.SongMidi
import br.app.stagebook.ui.editor.MidiDialog
import br.app.stagebook.ui.editor.MidiPanel
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
 * The song's MIDI window (user, 07/11): messages on entering and leaving, held to MIDI's ranges. One message per test:
 * two text-field cards run Robolectric's line breaker out of memory (the window works on the device, Chuwi 07/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class MidiDialogTest {
    @get:Rule val compose = createComposeRule()
    private var done: SongMidi? = null

    private fun open(midi: SongMidi = SongMidi()) = compose.setContent { StagebookTheme { MidiDialog(midi, { done = it }, {}) } }

    @Test fun `a program change on entering, by default`() {
        open()
        compose.onNodeWithText("Nada é enviado.").assertExists()
        compose.onNodeWithText("Adicionar mensagem").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(MidiKind.PROGRAM, done!!.enter.single().kind)
        assertEquals(emptyList<Any>(), done!!.exit)
    }

    @Test fun `on leaving has its own list`() {
        open()
        compose.onNodeWithText("Ao sair").performClick()
        compose.onNodeWithText("Adicionar mensagem").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, done!!.exit.size)
        assertEquals(0, done!!.enter.size)
    }

    @Test fun `a number past the range stops at its end`() {
        open()
        compose.onNodeWithText("Adicionar mensagem").performClick()
        compose.onAllNodesWithText("Programa, 1–128")[0].performTextReplacement("300")
        compose.onNodeWithText("OK").performClick()
        assertEquals(128, done!!.enter.single().number)
    }

    @Test fun `a control change shows its two numbers`() {
        open(SongMidi(enter = listOf(br.app.stagebook.domain.MidiMessage(MidiKind.CONTROL, channel = 2, number = 7, value = 100))))
        compose.onNodeWithText("Controle, 0–127").assertExists()
        compose.onNodeWithText("Valor, 0–127").assertExists()
    }

    // phone width (S26, 07/11): side by side, "Banco, 0–16383" and "Programa, 1–128" were cut to "Banco," and
    // "Programa,", and the type lost its end
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `on a phone, bank and program show their whole labels and the type its whole name`() {
        val midi = SongMidi(enter = listOf(br.app.stagebook.domain.MidiMessage(MidiKind.BANK_PROGRAM, bank = 3, number = 12)))
        compose.setContent {
            StagebookTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(320.dp)) { MidiPanel(midi, {}, {}) }
            }
        }
        listOf("Banco, 0–16383", "Programa, 1–128").forEach { label ->
            val layout = layoutOf(compose.onNode(hasText(label) and !hasSetTextAction(), useUnmergedTree = true))
            // cut, the one line ended at "Banco," with the rest broken off below it
            assertEquals(label, label.length, layout.getLineEnd(0, visibleEnd = true))
        }
        val kind = compose.onNodeWithText("Banco e programa (CC0/CC32 + PC)")
        val fieldWidth = kind.fetchSemanticsNode().size.width
        assertTrue("the type's name fits its field", layoutOf(kind).size.width <= fieldWidth)
    }

    private fun layoutOf(node: androidx.compose.ui.test.SemanticsNodeInteraction): androidx.compose.ui.text.TextLayoutResult {
        val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
        node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }
}
