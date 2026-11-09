package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The capo directive, the triad read for the harmonic field and the key names. */
class ChordsCapoTriadTest {
    private val tab = "{start_of_tab}\ne|---0---2---|\n{end_of_tab}"

    @Test fun `setting a capo writes the directive and keeps the chords`() {
        val out = Chords.withCapo("[G]Linha um [D]dois", 2)
        assertEquals(2, Chords.capo(out))
        assertTrue(out.contains("[G]Linha um [D]dois"))
    }

    @Test fun `capo 0 removes the directive`() {
        val with = Chords.withCapo("[G]Linha", 3)
        val without = Chords.withCapo(with, 0)
        assertEquals(0, Chords.capo(without))
        assertFalse(without.contains("capo"))
    }

    @Test fun `the capo is kept between 0 and 12`() {
        assertEquals(12, Chords.capo(Chords.withCapo("[G]Linha", 20)))
        assertEquals(0, Chords.capo(Chords.withCapo("[G]Linha", -3)))
    }

    @Test fun `the tablature frets go up with the capo and come back without it`() {
        val two = Chords.withCapo(tab, 2)
        assertTrue(two, two.contains("e|---2---4---|"))
        // moving the capo from 2 to 3 moves the frets by one more, not by three
        assertTrue(Chords.withCapo(two, 3).contains("e|---3---5---|"))
        val back = Chords.withCapo(two, 0)
        assertTrue(back.contains("e|---0---2---|"))
    }

    @Test fun `triad reads root, quality and seventh`() {
        assertEquals(Chords.Triad(0, minor = false, dim = false), Chords.triad("C"))
        assertEquals(Chords.Triad(9, minor = true, dim = false), Chords.triad("Am"))
        assertEquals(Chords.Triad(0, minor = false, dim = false, seventh = Chords.Seventh.MAJOR), Chords.triad("C7M"))
        assertEquals(Chords.Triad(7, minor = false, dim = false, seventh = Chords.Seventh.MINOR), Chords.triad("G7"))
        assertEquals(Chords.Triad(2, minor = true, dim = false, seventh = Chords.Seventh.MINOR), Chords.triad("Dm7"))
    }

    @Test fun `half diminished and diminished seventh are told apart`() {
        val half = Chords.triad("Bm7(5-)")!!
        assertTrue(half.dim)
        assertEquals(Chords.Seventh.MINOR, half.seventh)
        val full = Chords.triad("Bdim7")!!
        assertTrue(full.dim)
        assertEquals(Chords.Seventh.DIMINISHED, full.seventh)
        assertEquals(Chords.Seventh.NONE, Chords.triad("Bdim")!!.seventh)
    }

    @Test fun `a suspended chord has no third and counts as major`() {
        val sus = Chords.triad("Dsus4")!!
        assertFalse(sus.minor)
        assertFalse(sus.dim)
    }

    @Test fun `not a chord has no triad`() {
        assertNull(Chords.triad("Refrão"))
        assertNull(Chords.triad(""))
    }

    @Test fun `key names wrap around the octave, with the usual spelling`() {
        assertEquals("C", Chords.keyName(0, minor = false))
        assertEquals("Bb", Chords.keyName(10, minor = false))
        assertEquals("F#", Chords.keyName(6, minor = false))
        assertEquals("C#m", Chords.keyName(1, minor = true))
        assertEquals("C", Chords.keyName(12, minor = false))
        assertEquals("B", Chords.keyName(-1, minor = false))
        assertEquals("Bbm", Chords.keyName(-14, minor = true))
    }

    @Test fun `notes are named with flats in flat keys and sharps elsewhere`() {
        assertEquals("Bb", Chords.noteIn("F", 10))
        assertEquals("A#", Chords.noteIn("B", 10))
        assertEquals("Eb", Chords.noteIn("Cm", 3))
    }

    @Test fun `an annotation starts with a star and has text`() {
        assertTrue(Chords.isAnnotation("*Riff"))
        assertFalse(Chords.isAnnotation("*"))
        assertFalse(Chords.isAnnotation("Am"))
    }
}
