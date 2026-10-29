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
        assertEquals("vi", HarmonicField.degreeIn("Em7", "G"))   // a sétima não muda a tríade
        assertNull(HarmonicField.degreeIn("F", "G"))
    }

    @Test fun `onde mais o C aparece - IV em G, V em F, III em Am`() {
        val where = HarmonicField.appearances("C").map { it.roman + " em " + it.key }
        assertTrue(where.containsAll(listOf("IV em G", "V em F", "III em Am", "VI em Em", "VII em Dm")))
        assertTrue(where.none { it.startsWith("I em") })
    }
}
