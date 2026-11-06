package br.app.stagebook

import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.Voicings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicingsTest {
    private fun first(chord: String, i: Instrument = Instrument.ACOUSTIC_GUITAR) =
        Voicings.shapes(chord, i).first().frets.joinToString("") { if (it < 0) "x" else it.toString() }

    // a power chord lives on the low strings: asking for 4 strings ringing down to the highest left 11 of them undrawn
    @Test fun `every power chord has a shape on guitar`() {
        val missing = listOf("C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B").map { it + "5" }
            .filter { Voicings.shapes(it, Instrument.ACOUSTIC_GUITAR).isEmpty() }
        assertEquals(emptyList<String>(), missing)
        assertEquals("133xxx", first("F5"))
    }

    @Test fun `acoustic guitar starts with the known shapes`() {
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

    @Test fun `several shapes and slash bass`() {
        assertTrue(Voicings.shapes("G", Instrument.ACOUSTIC_GUITAR).size >= 4)
        // D/F#: the bass is F#, on the lowest string that plays
        val s = Voicings.shapes("D/F#", Instrument.ACOUSTIC_GUITAR).first().frets
        val low = s.indexOfFirst { it >= 0 }
        assertEquals(6, (intArrayOf(40, 45, 50, 55, 59, 64)[low] + s[low]) % 12)
    }

    @Test fun `ukulele`() {
        assertEquals("0003", first("C", Instrument.UKULELE))
        assertEquals("0232", first("G", Instrument.UKULELE))
        assertEquals("2000", first("Am", Instrument.UKULELE))
        assertEquals("2010", first("F", Instrument.UKULELE))
    }

    @Test fun `barre only when fingers run out`() {
        assertEquals(true, Voicings.needsBarre(intArrayOf(1, 3, 3, 2, 1, 1)))     // F
        assertEquals(false, Voicings.needsBarre(intArrayOf(0, 2, 3, 2)))         // G on ukulele: three fingers
        assertEquals(false, Voicings.needsBarre(intArrayOf(-1, 0, 2, 2, 2, 0)))  // A
    }

    @Test fun `cavaquinho has shapes and keyboard has inversions`() {
        assertTrue(Voicings.shapes("G", Instrument.CAVAQUINHO).isNotEmpty())
        val keys = Voicings.keys("C")
        assertEquals(listOf(60, 64, 67), keys.first())
        assertEquals(3, keys.size)
    }

    @Test fun `the chosen shape goes and comes back through the ChordPro define`() {
        val shape = Voicings.Shape(listOf(-1, 3, 5, 5, 5, 3))   // C with a barre on the 3rd
        val line = Voicings.define(Instrument.ACOUSTIC_GUITAR, "C", shape)
        assertEquals("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}", line)
        val text = Voicings.saveDefine("[C]oi", Instrument.ACOUSTIC_GUITAR, "C", line)
        assertEquals(shape, Voicings.savedShape(text, Instrument.ELECTRIC_GUITAR, "C"))   // acoustic and electric guitar: same diagrams
        // changing the choice doesn't duplicate the line
        val again = Voicings.saveDefine(text, Instrument.ACOUSTIC_GUITAR, "C", Voicings.define(Instrument.ACOUSTIC_GUITAR, "C", Voicings.Shape(listOf(-1, 3, 2, 0, 1, 0))))
        assertEquals(1, again.lines().count { it.startsWith("{define-guitar: C ") })
    }

    @Test fun `shapes are remembered - the same chord again is not searched again, and max still cuts`() {
        val first = Voicings.shapes("F#m7", Instrument.ACOUSTIC_GUITAR)
        assertSame(first, Voicings.shapes("F#m7", Instrument.ACOUSTIC_GUITAR))
        assertEquals(first.take(1), Voicings.shapes("F#m7", Instrument.ACOUSTIC_GUITAR, max = 1))
    }
}
