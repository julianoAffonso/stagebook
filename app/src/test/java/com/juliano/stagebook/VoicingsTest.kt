package com.juliano.stagebook

import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.domain.Voicings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicingsTest {
    private fun first(chord: String, i: Instrument = Instrument.VIOLAO) =
        Voicings.shapes(chord, i).first().frets.joinToString("") { if (it < 0) "x" else it.toString() }

    @Test fun `violão começa pelas formas conhecidas`() {
        assertEquals("x32010", first("C"))
        assertEquals("320003", first("G"))
        assertEquals("xx0232", first("D"))
        assertEquals("x02210", first("Am"))
        assertEquals("022100", first("E"))
        assertEquals("022000", first("Em"))
        assertEquals("133211", first("F"))
        assertEquals("xx0231", first("Dm"))
        assertEquals("x02220", first("A"))
    }

    @Test fun `várias variações e baixo invertido`() {
        assertTrue(Voicings.shapes("G", Instrument.VIOLAO).size >= 4)
        // D/F#: o baixo é F#, na corda mais grave que toca
        val s = Voicings.shapes("D/F#", Instrument.VIOLAO).first().frets
        val low = s.indexOfFirst { it >= 0 }
        assertEquals(6, (intArrayOf(40, 45, 50, 55, 59, 64)[low] + s[low]) % 12)
    }

    @Test fun `ukulele`() {
        assertEquals("0003", first("C", Instrument.UKULELE))
        assertEquals("0232", first("G", Instrument.UKULELE))
        assertEquals("2000", first("Am", Instrument.UKULELE))
        assertEquals("2010", first("F", Instrument.UKULELE))
    }

    @Test fun `cavaquinho tem formas e teclado tem inversões`() {
        assertTrue(Voicings.shapes("G", Instrument.CAVAQUINHO).isNotEmpty())
        val keys = Voicings.keys("C")
        assertEquals(listOf(60, 64, 67), keys.first())
        assertEquals(3, keys.size)
    }

    @Test fun `forma escolhida vai e volta pelo define do ChordPro`() {
        val shape = Voicings.Shape(listOf(-1, 3, 5, 5, 5, 3))   // C com pestana na 3ª
        val line = Voicings.define(Instrument.VIOLAO, "C", shape)
        assertEquals("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}", line)
        val text = Voicings.saveDefine("[C]oi", Instrument.VIOLAO, "C", line)
        assertEquals(shape, Voicings.savedShape(text, Instrument.GUITARRA, "C"))   // violão e guitarra: mesmos desenhos
        // trocar a escolha não duplica a linha
        val again = Voicings.saveDefine(text, Instrument.VIOLAO, "C", Voicings.define(Instrument.VIOLAO, "C", Voicings.Shape(listOf(-1, 3, 2, 0, 1, 0))))
        assertEquals(1, again.lines().count { it.startsWith("{define-guitar: C ") })
    }
}
