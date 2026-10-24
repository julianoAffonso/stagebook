package com.juliano.stagebook

import com.juliano.stagebook.domain.ChordImport
import com.juliano.stagebook.domain.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordImportTest {
    // cifra inventada, no formato dos sites (acordes na linha de cima, intro, tablatura)
    private val page = listOf(
        "Tom: G",
        "[Intro] G  D  Em  C",
        "",
        "E|-----3-----|",
        "B|---0---0---|",
        "",
        "[Primeira Parte]",
        "G              D",
        "Saio cedo pela rua",
        "Em       C",
        "Pra você ouvir",
    ).joinToString("\n")

    @Test fun `reconhece página com cifra e sem`() {
        assertTrue(ChordImport.hasChords(page))
        assertFalse(ChordImport.hasChords("só letra\nsem acorde\nnenhum"))
    }

    @Test fun `vira ChordPro com o tom, sem tablatura e sem a linha do tom`() {
        val cp = ChordImport.toChordPro(page)
        assertEquals("G", Chords.key(cp))
        assertFalse(cp, cp.contains("E|"))
        assertFalse(cp, cp.contains("Tom:"))
        assertTrue(cp, cp.contains("[G]Saio cedo pela [D]rua"))
        assertTrue(cp, cp.contains("[Em]Pra você [C]ouvir"))
        // "[Intro] G D Em C": o título numa linha, os acordes na de baixo
        assertTrue(cp, cp.contains("[Intro]\n[G] [D] [Em] [C]"))
    }

    @Test fun `tom e capo vindos da página valem mais que o texto`() {
        val cp = ChordImport.toChordPro(page, key = "A", capo = "Capotraste na 2ª casa")
        assertEquals("A", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
    }
}
