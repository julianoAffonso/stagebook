package br.app.stagebook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.ui.editor.toggleHighlight
import br.app.stagebook.ui.stage.ChordLook
import br.app.stagebook.ui.stage.chordLine
import br.app.stagebook.ui.stage.highlighted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Highlighting a phrase in the lyrics (vocalist, 31/10): `==phrase==` in the text, a marker background on stage. */
class HighlightTest {
    private val marker = Color.Yellow

    private fun backgrounds(a: androidx.compose.ui.text.AnnotatedString) =
        a.getStringAnnotations(br.app.stagebook.ui.stage.HIGHLIGHT_TAG, 0, a.length).map { a.text.substring(it.start, it.end) }

    @Test fun `the marks leave the line and the phrase gets the marker`() {
        val a = highlighted(androidx.compose.ui.text.AnnotatedString("eu quero ==você aqui== agora"), marker)
        assertEquals("eu quero você aqui agora", a.text)
        assertEquals(listOf("você aqui"), backgrounds(a))
    }

    @Test fun `an unpaired mark stays as text`() {
        assertEquals("a == b", highlighted(androidx.compose.ui.text.AnnotatedString("a == b"), marker).text)
    }

    @Test fun `with chords, every chord keeps its letter, one on a mark moves to the next letter`() {
        val a = highlighted(chordLine("[G]==Hey [C]Jude== don't", ChordLook(true, 1f, Color.White)), marker)
        val chords = a.getStringAnnotations("chord", 0, a.length).map { it.item to a.text[it.start] }
        assertEquals(listOf("G" to 'H', "C" to 'J'), chords)
        assertEquals(1, backgrounds(a).size)
        assert(!a.text.contains("=="))
    }

    @Test fun `editor - the selection is wrapped`() {
        val v = toggleHighlight(TextFieldValue("eu quero você", TextRange(3, 8)))
        assertEquals("eu ==quero== você", v.text)
    }

    @Test fun `editor - no selection highlights the word at the cursor`() {
        assertEquals("eu ==quero== você", toggleHighlight(TextFieldValue("eu quero você", TextRange(5))).text)
    }

    @Test fun `editor - tapping inside a highlight takes it off`() {
        assertEquals("eu quero você", toggleHighlight(TextFieldValue("eu ==quero== você", TextRange(6))).text)
    }

    @Test fun `editor - the selection stops at the end of the line`() {
        assertEquals("==um==\ndois", toggleHighlight(TextFieldValue("um\ndois", TextRange(0, 6))).text)
    }
    @Test fun `editor - the highlighted phrase shows with the marker and the marks fade, text unchanged (B-28)`() {
        val marker = androidx.compose.ui.graphics.Color.Yellow
        val faded = androidx.compose.ui.graphics.Color.Gray
        val text = "primeira\numa ==frase destacada== aqui"
        val out = br.app.stagebook.ui.editor.HighlightLook(marker, faded).filter(androidx.compose.ui.text.AnnotatedString(text)).text
        assertEquals(text, out.text)
        val open = text.indexOf("==")
        val bg = out.spanStyles.single { it.item.background == marker }
        assertEquals(open + 2 until text.lastIndexOf("=="), bg.start until bg.end)
        assertEquals(2, out.spanStyles.count { it.item.color == faded })
    }
    @Test fun `stage - highlight this line wraps the whole line, again takes it off (B-28)`() {
        val text = "{key: G}\n  [G]Saio cedo pela [D]rua  \noutra"
        val on = br.app.stagebook.ui.editor.toggleLineHighlight(text, 1)
        assertEquals("{key: G}\n  ==[G]Saio cedo pela [D]rua==  \noutra", on)
        assertTrue(br.app.stagebook.ui.editor.lineHighlighted(on, 1))
        assertEquals(text, br.app.stagebook.ui.editor.toggleLineHighlight(on, 1))
        assertEquals(text, br.app.stagebook.ui.editor.toggleLineHighlight(text, 9))
    }
}
