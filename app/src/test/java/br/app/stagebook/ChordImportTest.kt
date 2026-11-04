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
        val cp = ChordImport.toChordPro(page, key = "C", capo = "Capotraste na 2ª casa")
        assertEquals("C", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
    }

    @Test fun `Cifra Club's current page gives the shapes' key and the capo`() {
        // the chart is written in the shapes (Em); F#m is what it sounds like with the capo, which the app works out
        val cp = ChordImport.toChordPro(page, key = "F#m (com forma de Em)", capo = "2ª casa")
        assertEquals("Em", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
    }

    @Test fun `Cifra Club's English page gives the shapes' key and the capo`() {
        val cp = ChordImport.toChordPro(page, key = "F#m (in the shape of Em)", capo = "2nd fret")
        assertEquals("Em", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
    }

    // chart in Em shapes, the way a page with a capo prints it
    private val inEm = listOf("Em7        G", "Today is gonna be the day", "D4         A7(4)", "That they're gonna throw it back", "Em7        G", "By now you should've", "C9   D4     Em7", "Somehow realized").joinToString("\n")

    @Test fun `a page key that sounds with the capo becomes the chart's shapes, whatever the site`() {
        val cp = ChordImport.toChordPro(inEm, key = "F#m", capo = "2nd fret")
        assertEquals("Em", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
    }

    @Test fun `a page key already in the shapes stays`() {
        assertEquals("Em", Chords.key(ChordImport.toChordPro(inEm, key = "Em", capo = "2")))
        assertEquals("G", Chords.key(ChordImport.toChordPro(inEm, key = "G", capo = "2")))
    }

    @Test fun `the shape in parentheses counts in any language`() {
        assertEquals("Em", ChordImport.keyOf("F#m (con la forma de Em)"))
        assertEquals("Em", ChordImport.keyOf("Tonalidad: F#m (forme de Em)"))
        assertEquals("F#m", ChordImport.keyOf("Tonalidad: F#m"))
        // a chart line with a note in parentheses says nothing about the key
        assertEquals("", ChordImport.keyOf("Em (repete com G)\nletra da música"))
    }

    @Test fun `labels joined to the word before them are read, parts of other words aren't`() {
        val joined = "Tuning: E A D G B EKey: F#mCapo: \n2nd fret\nEm7  0-2-2-0-3-3"
        assertEquals("F#m", ChordImport.keyOf(joined))
        assertEquals(2, ChordImport.capoOf(joined))
        assertEquals("", ChordImport.keyOf("custom: A"))
        assertEquals("", ChordImport.keyOf("Tom: Dia de festa"))
        assertEquals(0, ChordImport.capoOf("eu escapo 2 vezes"))
    }

    @Test fun `the fret may come on the line after the label`() {
        assertEquals(2, ChordImport.capoOf("Capo: \n2nd fret"))
        assertEquals(2, ChordImport.capoOf("Cejilla: \n2nd fret"))
        assertEquals(3, ChordImport.capoOf("Capotraste na casa 3"))
    }

    @Test fun `the page's key and capo lines don't stay in the lyrics`() {
        val cp = ChordImport.toChordPro("Tom: F#m (com forma de Em)\nCapotraste: 2ª casa\n" + page.substringAfter('\n'))
        assertEquals("Em", Chords.key(cp))
        assertEquals(2, Chords.capo(cp))
        assertFalse(cp, cp.contains("forma de"))
        assertFalse(cp, cp.contains("Capotraste"))
    }

    @Test fun `a bare fret is a capo, a number starting the lyrics isn't`() {
        assertEquals(2, ChordImport.capoOf("2ª casa"))
        assertEquals(3, ChordImport.capoOf("3"))
        assertEquals(2, ChordImport.capoOf("Capotraste: 2ª casa"))
        assertEquals(0, ChordImport.capoOf("2 corações\nsem você"))
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
