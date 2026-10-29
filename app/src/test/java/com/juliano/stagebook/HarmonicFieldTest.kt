package com.juliano.stagebook

import com.juliano.stagebook.domain.HarmonicField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonicFieldTest {
    @Test fun `campo de C maior e de Am`() {
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "B°"), HarmonicField.field("C").map { it.chord })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), HarmonicField.field("C").map { it.roman })
        assertEquals(listOf("Am", "B°", "C", "Dm", "Em", "F", "G"), HarmonicField.field("Am").map { it.chord })
    }

    @Test fun `tom de bemol sai com bemol`() =
        assertEquals(listOf("F", "Gm", "Am", "Bb", "C", "Dm", "E°"), HarmonicField.field("F").map { it.chord })

    @Test fun `grau do acorde no tom da música`() {
        assertEquals("IV", HarmonicField.degreeIn("C", "G"))
        assertEquals("vi7", HarmonicField.degreeIn("Em7", "G"))
        assertNull(HarmonicField.degreeIn("F", "G"))
    }

    @Test fun `onde mais o C aparece - IV em G, V em F, III em Am`() {
        val where = HarmonicField.appearances("C").map { it.roman + " em " + it.key }
        assertTrue(where.containsAll(listOf("IV em G", "V em F", "III em Am", "VI em Em", "VII em Dm")))
        assertTrue(where.none { it.startsWith("I em") })
    }

    @Test fun `a sétima conta - C7M é o IV em G, C7 não é do campo`() {
        assertEquals("IV7M", HarmonicField.degreeIn("C7M", "G"))
        assertEquals("IV7M", HarmonicField.degreeIn("Cmaj7", "G"))
        assertNull(HarmonicField.degreeIn("C7", "G"))
        assertEquals("V7", HarmonicField.degreeIn("D7", "G"))
        assertNull(HarmonicField.degreeIn("D7M", "G"))
        assertEquals("viiø", HarmonicField.degreeIn("F#m7(b5)", "G"))
    }

    @Test fun `campo com tétrades`() =
        assertEquals(listOf("G7M", "Am7", "Bm7", "C7M", "D7", "Em7", "F#m7(b5)"), HarmonicField.field("G", sevenths = true).map { it.chord })

    @Test fun `V7 da menor harmônica conta - E7 em Am`() {
        assertEquals("V7", HarmonicField.degreeIn("E7", "Am"))
        assertEquals("V", HarmonicField.degreeIn("E", "Am"))
    }

    @Test fun `C7 só aparece como V7 de F - nada de IV em G`() {
        val where = HarmonicField.appearances("C7").map { it.roman + " em " + it.key }
        assertTrue("V7 em F" in where)
        assertTrue(where.none { it.endsWith("em G") })
    }

    @Test fun `todas as notas contam - sus, nona, sexta, alterações e o baixo`() {
        assertEquals("Vsus4", HarmonicField.degreeIn("Dsus4", "G"))
        assertEquals("IVsus2", HarmonicField.degreeIn("Csus2", "G"))
        assertEquals("ii7(9)", HarmonicField.degreeIn("Am7(9)", "G"))
        assertEquals("I6", HarmonicField.degreeIn("G6", "G"))
        assertNull(HarmonicField.degreeIn("D7(b9)", "G"))   // Eb não é de G
        assertNull(HarmonicField.degreeIn("C/Bb", "G"))     // o baixo Bb não é de G
        assertEquals("V7", HarmonicField.degreeIn("D7/F#", "G"))
        assertNull(HarmonicField.degreeIn("Caug", "G"))     // G# não é de G
    }
}
