package br.app.stagebook

import androidx.compose.ui.graphics.Color
import br.app.stagebook.domain.HighlightRange
import br.app.stagebook.domain.WORD_JOIN
import br.app.stagebook.ui.stage.CHORD_TAG
import br.app.stagebook.ui.stage.ChordLook
import br.app.stagebook.ui.stage.chordLine
import br.app.stagebook.ui.stage.emWidth
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chords close together never land on top of each other (user, 31/10: A Little Respect). */
class ChordSpacingTest {
    private fun room(raw: String, scale: Float) {
        val line = chordLine(raw, ChordLook(true, scale, Color.Yellow))
        val marks = line.getStringAnnotations(CHORD_TAG, 0, line.length)
        marks.zipWithNext().forEach { (a, b) ->
            val between = line.text.substring(a.start, b.start)
            assertTrue("${a.item} has no room before ${b.item}", emWidth(between) >= emWidth(a.item) * scale * 1.1f)
        }
    }

    @Test fun `sus chords two letters apart get room`() = room("x[Csus4] ab c[C]d [Csus4]efghij[C]kl", 0.8f)

    @Test fun `bigger chords get more room`() = room("[Fsus4]il [C]ab[Csus4] cd [C]ef", 1.2f)

    // a chord after the last word sits on spaces of its own, on the same line (it went down onto the next title)
    @Test fun `a chord at the end of the line keeps a place on the line`() {
        val line = chordLine("Até o sol voltar [G]", ChordLook(true, 0.8f, Color.Yellow))
        val g = line.getStringAnnotations(CHORD_TAG, 0, line.length).single()
        assertTrue(g.end > g.start && g.end <= line.length)
    }

    @Test fun `lyrics already wide enough are not changed`() {
        val line = chordLine("[C]long enough words [G]here", ChordLook(true, 0.8f, Color.Yellow))
        assertTrue(line.text == "long enough words here")
    }

    // the room for a chord inside a word keeps the word readable as one (user, 03/11: a word came out as two)
    @Test fun `room inside a word gets a hyphen`() {
        val line = chordLine("[Csus4]gu[A9]erra e [G]paz", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text.startsWith("gu") && line.text.contains(WORD_JOIN) && line.text.contains("erra"))
        assertTrue(line.text, !line.text.substringAfter("erra").contains(WORD_JOIN))
    }

    @Test fun `room between words stays blank`() {
        val line = chordLine("[Csus4]oi [A9]erra", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, !line.text.contains(WORD_JOIN))
    }

    // the highlighter matches stage letters to saved ones: the hyphen isn't a saved letter
    @Test fun `highlighting a word with a chord inside skips the hyphen`() {
        val saved = "[Csus4]gu[A9]erra e [G]paz"
        val shown = chordLine(saved, ChordLook(true, 1f, Color.Yellow)).text
        val out = HighlightRange.toggle(saved, shown, 0, 0)
        assertTrue(out, out.startsWith("[Csus4]==gu[A9]erra=="))
    }

    // "x4" after the chords stays on their line, not under them as lyrics (user, 03/11)
    @Test fun `a repeat mark stays on the chords line`() {
        val line = chordLine("[Em]   [G]   [C]   [B]   x4", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text == "Em   G   C   B   x4")
        assertTrue(line.getStringAnnotations(CHORD_TAG, 0, line.length).isEmpty())
    }

    @Test fun `marks between chords keep their order`() {
        val line = chordLine("[G7/B] - [Cm] - [Fm] (2x)", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text == "G7/B   -   Cm   -   Fm   (2x)")
    }

    @Test fun `a real word is still lyrics`() {
        val line = chordLine("[A]   [B]   bis", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.getStringAnnotations(CHORD_TAG, 0, line.length).isNotEmpty())
    }

    // "B/F#  224442" at the start of a song: the chord like the others, the frets beside it as a note (user, 03/11)
    @Test fun `a chord with its frets written beside shows as a chord line`() {
        val raw = br.app.stagebook.domain.Chords.bracketChordLine("B/F#  224442")
        assertTrue(raw, raw == "[B/F#]  224442")
        val line = chordLine(raw, ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text == "B/F#   224442")
    }

    @Test fun `the written frets are the chord's guitar shape`() {
        val text = "B/F#  224442\nA/E    x02220\n[B/F#]letra"
        assertTrue(br.app.stagebook.domain.Chords.shapeHint(text, "B/F#") == listOf(2, 2, 4, 4, 4, 2))
        assertTrue(br.app.stagebook.domain.Chords.shapeHint(text, "A/E") == listOf(-1, 0, 2, 2, 2, 0))
        val shape = br.app.stagebook.domain.Voicings.savedShape(text, br.app.stagebook.data.model.Instrument.ACOUSTIC_GUITAR, "B/F#")
        assertTrue(shape?.frets == listOf(2, 2, 4, 4, 4, 2))
    }

    // transposing moves the frets with the chord, and never drops them into the lyric below (audit 03/11)
    @Test fun `transposing moves a fret note with its chord`() {
        val out = br.app.stagebook.domain.Chords.transposeSong("B/F#  224442\nA/E    002220\n[B/F#]letra", 1)
        assertTrue(out, out.contains("[C/G] 335553") || out.contains("[C/G]  335553") || out.lineSequence().any { it.replace(" ", "") == "[C/G]335553" })
        assertTrue(out, out.lineSequence().any { it.replace(" ", "") == "[Bb/F]113331" || it.replace(" ", "") == "[A#/F]113331" })
        val c = br.app.stagebook.domain.Chords.shapeHint(out, "C/G")
        assertTrue("$c", c == listOf(3, 3, 5, 5, 5, 3))
    }

    @Test fun `a fret note above a lyric stays on its own line`() {
        val out = br.app.stagebook.domain.Chords.fromChordsOverLyrics("B/F#  224442\numa linha de letra")
        assertTrue(out, out.lines().first().replace(" ", "") == "[B/F#]224442" && out.lines()[1] == "uma linha de letra")
    }

    @Test fun `a repeat mark above a lyric goes after it`() {
        val out = br.app.stagebook.domain.Chords.fromChordsOverLyrics("G       D   2x\numa linha de letra")
        assertTrue(out, out.startsWith("[G]uma lin") && out.endsWith("2x"))
    }

    @Test fun `a shape past the 12th fret takes dashes and is still read`() {
        val out = br.app.stagebook.domain.Chords.transposeSong("[B/F#] 224442\n[B/F#]letra", 8)
        val g = out.lines().first { it.contains(" ") && it.startsWith("[") }
        assertTrue(out, g.contains("10-10-12-12-12-10"))
        assertTrue(br.app.stagebook.domain.Chords.shapeHint(out, g.substringAfter('[').substringBefore(']')) == listOf(10, 10, 12, 12, 12, 10))
    }

    // the keyboard's capo transposes on the fly: a chords-only line without brackets moves with the rest (audit 03/11)
    @Test fun `transposing moves a chords-only line written without brackets`() {
        val out = br.app.stagebook.domain.Chords.transpose("A   E\n[A]letra", 2)
        assertTrue(out, out.lines().first().replace(" ", "") == "[B][F#]")
    }

    // "Intro: G D" without brackets: the label and the chords on one chords line (audit 03/11)
    @Test fun `a label in front of the chords stays on the chords line`() {
        val line = chordLine(br.app.stagebook.domain.Chords.bracketChordLine("Intro: G D"), ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text == "Intro:   G   D")
        assertTrue(line.getStringAnnotations(CHORD_TAG, 0, line.length).isEmpty())
    }

    // user, 07/11: Waiting For a Girl Like You showed its intro as lyrics, because of the note after the dash
    @Test fun `chords with a note after a dash are a chord line`() {
        val raw = br.app.stagebook.domain.Chords.bracketChordLine("Am G6 Am G6 - INTRO RIFF x 2")
        val line = chordLine(raw, ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text == "Am   G6   Am   G6   -   INTRO   RIFF   x   2")
        assertTrue(line.getStringAnnotations(CHORD_TAG, 0, line.length).isEmpty())
    }

    // ChordPro annotation (user, 07/11: Flores came with "at[*2x]é" and showed the bracket inside the word): the text
    // sits above the lyrics like a chord, and it isn't one
    @Test fun `an annotation sits above the lyrics`() {
        val line = chordLine("[D5]Olhei at[*2x]é ficar", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, !line.text.contains('['))
        val marks = line.getStringAnnotations(CHORD_TAG, 0, line.length).map { it.item }
        assertTrue(marks.toString(), marks == listOf("D5", "*2x"))
        assertTrue(br.app.stagebook.ui.stage.chordLabel("*2x") == "2x")
        assertTrue(!br.app.stagebook.domain.Chords.isChord("*2x"))
    }

    // an annotation isn't sung on a syllable: inside a word it goes to the word's end, with no hyphen (user, 07/11)
    @Test fun `an annotation inside a word moves to its end`() {
        val line = chordLine("As flor[*2x]es que", ChordLook(true, 1f, Color.Yellow))
        assertTrue(line.text, line.text.startsWith("As flores"))
        assertTrue(line.text, !line.text.contains(br.app.stagebook.domain.WORD_JOIN))
    }
}
