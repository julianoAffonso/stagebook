package br.app.stagebook

import br.app.stagebook.domain.ChordImport
import br.app.stagebook.domain.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordImportTest {
    // made-up chart, in the sites' format (chords on the line above, intro, tablature)
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

    @Test fun `recognizes a page with and without chords`() {
        assertTrue(ChordImport.hasChords(page))
        assertFalse(ChordImport.hasChords("só letra\nsem acorde\nnenhum"))
    }

    @Test fun `becomes ChordPro with the key, without tablature and without the key line`() {
        val cp = ChordImport.toChordPro(page)
        assertEquals("G", Chords.key(cp))
        assertFalse(cp, cp.contains("E|"))
        assertFalse(cp, cp.contains("Tom:"))
        assertTrue(cp, cp.contains("[G]Saio cedo pela [D]rua"))
        assertTrue(cp, cp.contains("[Em]Pra você [C]ouvir"))
        // "[Intro] G D Em C": the title on one line, the chords on the next
        assertTrue(cp, cp.contains("[Intro]\n[G] [D] [Em] [C]"))
    }

    @Test fun `key and capo from the page win over the text`() {
        val cp = ChordImport.toChordPro(page, key = "A", capo = "Capotraste na 2ª casa")
        assertEquals("A", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
    }

    @Test fun `key and capo in the 4 languages`() {
        assertEquals("A", ChordImport.keyOf("Key: A"))
        assertEquals("Bb", ChordImport.keyOf("Tono: Bb"))
        assertEquals("F#m", ChordImport.keyOf("Tonalità: F#m"))
        assertEquals(2, ChordImport.capoOf("Capo 2"))
        assertEquals(3, ChordImport.capoOf("Capo: 3rd fret"))
        assertEquals(4, ChordImport.capoOf("Cejilla en el 4º traste"))
        assertEquals(5, ChordImport.capoOf("Capotasto al 5º tasto"))
        assertEquals(2, ChordImport.capoOf("Capotraste na 2ª casa"))
        assertEquals(0, ChordImport.capoOf("sem capo aqui"))
        // "Tom" in the middle of the lyrics isn't the key
        assertEquals("", ChordImport.keyOf("o Tom chegou cedo"))
    }
}
