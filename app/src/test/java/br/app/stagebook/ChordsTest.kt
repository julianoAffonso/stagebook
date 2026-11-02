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

    @Test fun `key capo and tempo read in one pass match the single readers`() {
        val text = "[G]um\n{Tempo: 128 bpm}\n{key: Em}\n{capo: 3}\n{key: G}\n[C]dois"
        val m = Chords.meta(text)
        assertEquals(Chords.key(text), m.key)
        assertEquals(Chords.capo(text), m.capo)
        assertEquals(Chords.tempo(text), m.tempo)
        assertEquals("128 bpm", m.tempoText)
        assertEquals(br.app.stagebook.domain.SongMeta(), Chords.meta("só a letra"))
        // the editor's state reads them from its text
        assertEquals("Em", br.app.stagebook.ui.editor.SongEditorUiState(text = text).meta.key)
        assertEquals(Chords.withMeta("nova", text), Chords.withMeta("nova", m))
    }

    @Test fun `chart notations from the library and Cifra Club are chords - a slash before a note is still the bass`() {
        listOf("E7/9", "A7/4", "A4/7", "C#7/9", "B7/13", "G6/9", "D7(2/4)", "F5(7M/9)/C", "A7(4/6)", "E7(9+)", "F#7(13-)", "Bm7+", "A5-", "C4+(9)")
            .forEach { assertTrue(it, Chords.isChord(it)) }
        assertEquals("D7/9", Chords.transposeChord("C#7/9", 1))
        assertEquals("A/C#", Chords.transposeChord("G/B", 2))
        assertEquals("C#", br.app.stagebook.domain.ChordTheory.notes("A/C#")!!.bass.let { listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[it] })
    }

    @Test fun `every chord feature reads the notations the same way`() {
        val t = br.app.stagebook.domain.ChordTheory
        assertEquals(t.notes("E7(9)"), t.notes("E7/9"))
        assertEquals(t.notes("F7M"), t.notes("F7+"))
        assertEquals(t.notes("E7(#9)"), t.notes("E7(9+)"))
        assertEquals(t.notes("F#7(b13)"), t.notes("F#7(13-)"))
        assertEquals(t.notes("A7(4)"), t.notes("A7/4"))
        listOf("E7/9", "A7/4", "D7(2/4)", "F5(7M/9)/C", "Bm7+", "F#7(13-)").forEach {
            assertTrue(it, br.app.stagebook.domain.Voicings.shapes(it, br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR).isNotEmpty())
        }
        // the key: m7(5-) is the half-diminished ii of a minor key, not a minor chord
        assertEquals(Chords.detectKey("[Am]a [Bm7(b5)]b [E7]c [Am]d"), Chords.detectKey("[Am]a [Bm7(5-)]b [E7/9]c [Am]d"))
        // Fullgás as in the library (chords only): the key is still B minor
        assertEquals("Bm", Chords.detectKey("[Bm]a [F#7(b13)]b [Bm7+]c [Em7M]d [G7+]e [A6]f [C#7/9]g [F#7]h [Bm]i"))
    }

    @Test fun `one reading of the chord name for everything`() {
        val n = br.app.stagebook.domain.ChordName.parse(" F5(7M/9)/C ")!!
        assertEquals("F", n.root); assertEquals("5(7M/9)", n.suffix); assertEquals("C", n.bass)
        assertEquals(5, n.rootPc); assertEquals(0, n.bassPc)
        assertEquals("F5(7M/9)/C", n.toString())
        assertEquals(11, br.app.stagebook.domain.ChordName.parse("Cb")!!.rootPc)
        assertEquals(null, br.app.stagebook.domain.ChordName.parse("Refrão"))
    }

    @Test fun `a marked chord keeps its mark and plays as the chord`() {
        assertTrue(Chords.isChordLine("D  D  E*  F*"))
        assertEquals("F#*", Chords.transposeChord("E*", 2))
        assertEquals(br.app.stagebook.domain.ChordTheory.notes("E"), br.app.stagebook.domain.ChordTheory.notes("E*"))
        assertTrue(br.app.stagebook.domain.Voicings.shapes("E*", br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR).isNotEmpty())
    }

    @Test fun `an imported chart keeps the site's chord notation`() {
        val body = br.app.stagebook.domain.ChordImport.toChordPro("A7/4   Bm7+\nlinha um aqui\nE*  C#7/9\nlinha dois aqui")
        listOf("[A7/4]", "[Bm7+]", "[E*]", "[C#7/9]").forEach { assertTrue(it, it in body) }
    }
    @Test fun `a half step up and back down gives the same spelling (device test B-06)`() {
        val wave = "{key: Dm}\n[Dm7] [G/D] [D7M(9)] [Bbº] [F#7(13)] [F#7(13-)] [B7(9-)] [Bb7(13)] [A7(13-)] [C/Bb] [Ab/Bb]"
        assertEquals(wave, Chords.transpose(Chords.transpose(wave, 1), -1))
        assertEquals(wave, Chords.transpose(Chords.transpose(wave, -1), 1))
        // each note by its degree in the new key: in Ebm the F#7 (major third of Dm) is G7, in C#m it is E#… → F7
        assertTrue(Chords.transpose(wave, 1), "[G7(13)]" in Chords.transpose(wave, 1))
        assertEquals("Bb", Chords.spell(10, "C"))
        assertEquals("F#", Chords.spell(6, "Dm"))
        assertEquals(null, Chords.spell(5, "C#m"))   // E# → the caller uses the key's sharps (F)
    }
}
