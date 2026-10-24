package com.juliano.stagebook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.juliano.stagebook.ui.editor.insertChord
import com.juliano.stagebook.ui.editor.usedChords
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordEditorTest {
    @Test fun `acorde entra no cursor e o cursor fica depois dele`() {
        val v = insertChord(TextFieldValue("Hoje eu vou", TextRange(5)), "D")
        assertEquals("Hoje [D]eu vou", v.text)
        assertEquals(TextRange(8), v.selection)
    }

    @Test fun `acorde troca o trecho selecionado`() {
        assertEquals("[Am]eu vou", insertChord(TextFieldValue("Hoje eu vou", TextRange(0, 5)), "Am").text)
    }

    @Test fun `atalhos são os acordes da música, na ordem e sem repetir, sem as seções`() {
        assertEquals(listOf("G", "D", "Em"), usedChords("[Refrão]\n[G]a [D]b [G]c\n[Em]d [Solo]"))
    }
}
