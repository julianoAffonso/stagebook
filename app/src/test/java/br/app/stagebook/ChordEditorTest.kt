package br.app.stagebook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.ui.editor.insertChord
import br.app.stagebook.ui.editor.usedChords
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordEditorTest {
    @Test fun `chord goes in at the cursor and the cursor ends up after it`() {
        val v = insertChord(TextFieldValue("Hoje eu vou", TextRange(5)), "D")
        assertEquals("Hoje [D]eu vou", v.text)
        assertEquals(TextRange(8), v.selection)
    }

    @Test fun `chord replaces the selected text`() {
        assertEquals("[Am]eu vou", insertChord(TextFieldValue("Hoje eu vou", TextRange(0, 5)), "Am").text)
    }

    @Test fun `shortcuts are the song's chords, in order, without repeats or sections`() {
        assertEquals(listOf("G", "D", "Em"), usedChords("[Refrão]\n[G]a [D]b [G]c\n[Em]d [Solo]"))
    }
}
