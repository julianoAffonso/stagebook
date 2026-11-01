package br.app.stagebook

import br.app.stagebook.domain.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTest {
    @Test fun `recognizes chords and does not mistake them for a section or a word`() {
        listOf("G", "Am", "F#m7/E", "Cm7(b5)", "G7M", "Bb", "Asus4", "C/G", "E7(9)", "D°", "Bbmaj7", "A4", "C#m").forEach {
            assertTrue(it, Chords.isChord(it))
        }
        listOf("Refrão", "Chorus", "Bad", "Dim", "Eu", "Intro", "Am7x", "H").forEach { assertFalse(it, Chords.isChord(it)) }
    }

    @Test fun `chords on the line above become ChordPro in the right column`() {
        // each chord above the lyrics where it starts: G on "Hoje", D on "vou", Em on "Pra", C on "ouvir"
        val chart = listOf(
            "G           D",
            "Hoje eu     vou cantar",
            "Em       C",
            "Pra você ouvir",
        ).joinToString("\n")
        assertEquals("[G]Hoje eu     [D]vou cantar\n[Em]Pra você [C]ouvir", Chords.fromChordsOverLyrics(chart))
    }

    @Test fun `chords-only line, label and section`() {
        val chart = "[Intro] G  D  Em  C\n\n[Refrão]\nG     D\nOh oh oh\nIntro: G D (2x)"
        val out = Chords.fromChordsOverLyrics(chart).split('\n')
        // "[Intro] G D…" becomes the title and, below, the chords
        assertEquals("[Intro]", out[0])
        assertEquals("[G] [D] [Em] [C]", out[1])
        assertEquals("[Refrão]", out[3])
        assertEquals("[G]Oh oh [D]oh", out[4])
        assertEquals("Intro: [G] [D] (2x)", out[5])
    }

    @Test fun `chord after the end of the lyrics stays at the end`() {
        // columns 3 and 5, after the end of "Oi": they stay at the end, with the space between them
        assertEquals("Oi [G]  [D]", Chords.fromChordsOverLyrics("   G D\nOi"))
    }

    @Test fun `ready ChordPro passes unchanged`() {
        val cp = "{key: G}\n[G]Hoje eu [D]vou\n[Refrão]\n[Em]Oh"
        assertEquals(cp, Chords.fromChordsOverLyrics(cp))
    }

    @Test fun `transpose chords, bass and key, with flats in flat keys`() {
        assertEquals("A", Chords.transposeChord("G", 2))
        assertEquals("F#m7/E", Chords.transposeChord("Em7/D", 2))
        assertEquals("Bb", Chords.transposeChord("A", 1, useFlats = true))
        assertEquals("G", Chords.transposeChord("A", -2))
        val song = "{key: C}\n[C]Hoje [G/B]eu [Am7]vou [F]lá"
        val up = Chords.transpose(song, 3)   // C -> Eb: flat key
        assertEquals("Eb", Chords.key(up))
        assertTrue(up, up.contains("[Eb]Hoje [Bb/D]eu [Cm7]vou [Ab]lá"))
        assertEquals(song, Chords.transpose(Chords.transpose(song, 5), -5))
    }

    @Test fun `transpose leaves section titles alone`() {
        assertEquals("[Refrão]\n[A]oi", Chords.transpose("[Refrão]\n[G]oi", 2))
    }

    @Test fun `capo changes the sounding key and not the chords`() {
        val song = "{key: G}\n{capo: 2}\n[G]oi"
        assertEquals(2, Chords.capo(song))
        assertEquals("A", Chords.soundingKey(Chords.key(song), Chords.capo(song)))
        assertEquals("G", Chords.soundingKey("G", 0))
    }

    @Test fun `directive with a dash in the name (define-guitar) is a directive`() {
        assertTrue(Chords.isDirective("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}"))
        assertEquals("", Chords.stripChords("{define-guitar: C base-fret 1 frets x 3 2 0 1 0}"))
    }

    @Test fun `chords hidden from the list go and come back through the x_hide_chords directive`() {
        var t = "[G]a [D]b [C]c"
        t = Chords.setHiddenChords(t, setOf("G", "D"))
        assertEquals(setOf("G", "D"), Chords.hiddenChords(t))
        assertEquals(listOf("C"), br.app.stagebook.ui.stage.stripChords(t))
        assertEquals("[G]a [D]b [C]c", Chords.setHiddenChords(t, emptySet()))
        assertTrue(Chords.isDirective(t.lines().first()))   // the stage doesn't show the line
    }

    @Test fun `directives are saved, replaced and removed`() {
        var t = "[G]oi"
        t = Chords.setDirective(t, "capo", "3")
        assertEquals("{capo: 3}\n[G]oi", t)
        t = Chords.setDirective(t, "capo", "5")
        assertEquals(5, Chords.capo(t))
        assertEquals("[G]oi", Chords.setDirective(t, "capo", ""))
    }

    @Test fun `lyrics only removes chords, chord lines and directives and keeps sections`() {
        val song = "{key: G}\n[Refrão]\n[G]Hoje eu [D] vou\n[Em] [C]\nfim"
        assertEquals("[Refrão]\nHoje eu vou\nfim", Chords.stripChords(song))
        assertTrue(Chords.hasChords(song))
        assertFalse(Chords.hasChords("[Refrão]\nsó letra"))
    }

    @Test fun `a long malformed directive line is read fast - normal ones still read`() {
        val started = System.nanoTime()
        assertEquals(null, Chords.directive("{c:" + " ".repeat(5000) + "x", "c"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 500)
        assertEquals("G", Chords.directive("{key: G }", "key"))
        assertEquals("Refrão", Chords.directive("  { comment :  Refrão  }  ", "comment"))
        assertTrue(Chords.isDirective("{start_of_chorus}"))
    }
}
