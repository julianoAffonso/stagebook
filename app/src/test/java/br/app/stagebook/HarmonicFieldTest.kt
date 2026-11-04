package br.app.stagebook

import br.app.stagebook.domain.HarmonicField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonicFieldTest {
    @Test fun `C major and Am fields`() {
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "B°"), HarmonicField.field("C").map { it.chord })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), HarmonicField.field("C").map { it.roman })
        assertEquals(listOf("Am", "B°", "C", "Dm", "Em", "F", "G"), HarmonicField.field("Am").map { it.chord })
    }

    @Test fun `flat key comes out with flats`() =
        assertEquals(listOf("F", "Gm", "Am", "Bb", "C", "Dm", "E°"), HarmonicField.field("F").map { it.chord })

    @Test fun `chord degree in the song's key`() {
        assertEquals("IV", HarmonicField.degreeIn("C", "G"))
        assertEquals("vi7", HarmonicField.degreeIn("Em7", "G"))
        assertNull(HarmonicField.degreeIn("F", "G"))
    }

    @Test fun `where else C shows up - IV in G, V in F, III in Am`() {
        val where = HarmonicField.appearances("C").map { it.roman + " em " + it.key }
        assertTrue(where.containsAll(listOf("IV em G", "V em F", "III em Am", "VI em Em", "VII em Dm")))
        assertTrue(where.none { it.startsWith("I em") })
    }

    @Test fun `the seventh counts - Cmaj7 is IV in G, C7 is not in the field`() {
        assertEquals("IV7M", HarmonicField.degreeIn("C7M", "G"))
        assertEquals("IV7M", HarmonicField.degreeIn("Cmaj7", "G"))
        assertNull(HarmonicField.degreeIn("C7", "G"))
        assertEquals("V7", HarmonicField.degreeIn("D7", "G"))
        assertNull(HarmonicField.degreeIn("D7M", "G"))
        assertEquals("viiø", HarmonicField.degreeIn("F#m7(b5)", "G"))
    }

    @Test fun `field with seventh chords`() =
        assertEquals(listOf("G7M", "Am7", "Bm7", "C7M", "D7", "Em7", "F#m7(b5)"), HarmonicField.field("G", sevenths = true).map { it.chord })

    @Test fun `V7 from harmonic minor counts - E7 in Am`() {
        assertEquals("V7", HarmonicField.degreeIn("E7", "Am"))
        assertEquals("V", HarmonicField.degreeIn("E", "Am"))
    }

    @Test fun `C7 only shows up as V7 of F - no IV in G`() {
        val where = HarmonicField.appearances("C7").map { it.roman + " em " + it.key }
        assertTrue("V7 em F" in where)
        assertTrue(where.none { it.endsWith("em G") })
    }

    @Test fun `every note counts - sus, ninth, sixth, alterations and the bass`() {
        assertEquals("Vsus4", HarmonicField.degreeIn("Dsus4", "G"))
        assertEquals("IVsus2", HarmonicField.degreeIn("Csus2", "G"))
        assertEquals("ii7(9)", HarmonicField.degreeIn("Am7(9)", "G"))
        assertEquals("I6", HarmonicField.degreeIn("G6", "G"))
        assertNull(HarmonicField.degreeIn("D7(b9)", "G"))   // Eb isn't in G
        assertNull(HarmonicField.degreeIn("C/Bb", "G"))     // the Bb bass isn't in G
        assertEquals("V7", HarmonicField.degreeIn("D7/F#", "G"))
        assertNull(HarmonicField.degreeIn("Caug", "G"))     // G# isn't in G
    }

    @Test fun `a key that can't be read follows the chords when transposing`() {
        val t = br.app.stagebook.domain.Chords.transposeSong("{key: C major}\n[C]um [F]dois [G]tres [C]fim", 2)
        assertEquals("D", br.app.stagebook.domain.Chords.key(t))
    }

    @Test fun `the sounding key has the usual name`() {
        assertEquals("Ab", br.app.stagebook.domain.Chords.soundingKey("G", 1))
        assertEquals("Bbm", br.app.stagebook.domain.Chords.soundingKey("Am", 1))
        assertEquals("A", br.app.stagebook.domain.Chords.soundingKey("G", 2))
    }
}
