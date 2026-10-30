package br.app.stagebook

import br.app.stagebook.domain.ChordTheory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChordTheoryTest {
    private fun tones(c: String) = ChordTheory.notes(c)!!.tones

    @Test fun `triads, sevenths and the Brazilian way of writing them`() {
        assertEquals(setOf(0, 4, 7), tones("C"))
        assertEquals(setOf(9, 0, 4), tones("Am"))
        assertEquals(setOf(7, 11, 2, 5), tones("G7"))
        assertEquals(setOf(7, 11, 2, 6), tones("G7M"))
        assertEquals(setOf(0, 4, 7, 2), tones("C9"))           // 9 sozinho = nona adicionada
        assertEquals(setOf(0, 4, 7, 10, 2), tones("C7(9)"))
        assertEquals(setOf(9, 2, 4), tones("A4"))               // 4 = suspenso
        assertEquals(setOf(6, 9, 0, 4), tones("F#m7(b5)"))
        assertEquals(setOf(11, 2, 5), tones("B°"))
        assertEquals(setOf(0, 7), tones("C5"))
    }

    @Test fun `slash bass`() {
        val n = ChordTheory.notes("D/F#")!!
        assertEquals(2, n.root)
        assertEquals(6, n.bass)
        assertEquals(setOf(2, 6, 9), n.tones)
    }

    @Test fun `not a chord`() {
        assertNull(ChordTheory.notes("Refrão"))
    }
}
