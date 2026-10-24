package com.juliano.stagebook

import com.juliano.stagebook.domain.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTest {
    @Test fun `reconhece acordes e não confunde com seção ou palavra`() {
        listOf("G", "Am", "F#m7/E", "Cm7(b5)", "G7M", "Bb", "Asus4", "C/G", "E7(9)", "D°", "Bbmaj7", "A4", "C#m").forEach {
            assertTrue(it, Chords.isChord(it))
        }
        listOf("Refrão", "Chorus", "Bad", "Dim", "Eu", "Intro", "Am7x", "H").forEach { assertFalse(it, Chords.isChord(it)) }
    }

    @Test fun `acordes na linha de cima viram ChordPro na coluna certa`() {
        // cada acorde em cima da letra em que começa: G em "Hoje", D em "vou", Em em "Pra", C em "ouvir"
        val cifra = listOf(
            "G           D",
            "Hoje eu     vou cantar",
            "Em       C",
            "Pra você ouvir",
        ).joinToString("\n")
        assertEquals("[G]Hoje eu     [D]vou cantar\n[Em]Pra você [C]ouvir", Chords.fromChordsOverLyrics(cifra))
    }

    @Test fun `linha só de acordes, rótulo e seção`() {
        val cifra = "[Intro] G  D  Em  C\n\n[Refrão]\nG     D\nOh oh oh\nIntro: G D (2x)"
        val out = Chords.fromChordsOverLyrics(cifra).split('\n')
        // "[Intro] G D…" vira o título e, embaixo, os acordes
        assertEquals("[Intro]", out[0])
        assertEquals("[G] [D] [Em] [C]", out[1])
        assertEquals("[Refrão]", out[3])
        assertEquals("[G]Oh oh [D]oh", out[4])
        assertEquals("Intro: [G] [D] (2x)", out[5])
    }

    @Test fun `acorde depois do fim da letra fica no fim`() {
        // colunas 3 e 5, depois do fim de "Oi": ficam no fim, com o espaço entre eles
        assertEquals("Oi [G]  [D]", Chords.fromChordsOverLyrics("   G D\nOi"))
    }

    @Test fun `ChordPro já pronto passa igual`() {
        val cp = "{key: G}\n[G]Hoje eu [D]vou\n[Refrão]\n[Em]Oh"
        assertEquals(cp, Chords.fromChordsOverLyrics(cp))
    }

    @Test fun `transpor acordes, baixo e tom, com bemóis nos tons de bemol`() {
        assertEquals("A", Chords.transposeChord("G", 2))
        assertEquals("F#m7/E", Chords.transposeChord("Em7/D", 2))
        assertEquals("Bb", Chords.transposeChord("A", 1, useFlats = true))
        assertEquals("G", Chords.transposeChord("A", -2))
        val song = "{key: C}\n[C]Hoje [G/B]eu [Am7]vou [F]lá"
        val up = Chords.transpose(song, 3)   // C -> Eb: tom de bemol
        assertEquals("Eb", Chords.key(up))
        assertTrue(up, up.contains("[Eb]Hoje [Bb/D]eu [Cm7]vou [Ab]lá"))
        assertEquals(song, Chords.transpose(Chords.transpose(song, 5), -5))
    }

    @Test fun `transpor não mexe em título de seção`() {
        assertEquals("[Refrão]\n[A]oi", Chords.transpose("[Refrão]\n[G]oi", 2))
    }

    @Test fun `capo muda o tom que soa e não os acordes`() {
        val song = "{key: G}\n{capo: 2}\n[G]oi"
        assertEquals(2, Chords.capo(song))
        assertEquals("A", Chords.soundingKey(Chords.key(song), Chords.capo(song)))
        assertEquals("G", Chords.soundingKey("G", 0))
    }

    @Test fun `diretiva com hífen no nome (define-guitar) é diretiva`() {
        assertTrue(Chords.isDirective("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}"))
        assertEquals("", Chords.stripChords("{define-guitar: C base-fret 1 frets x 3 2 0 1 0}"))
    }

    @Test fun `acordes escondidos da lista vão e voltam pela diretiva x_hide_chords`() {
        var t = "[G]a [D]b [C]c"
        t = Chords.setHiddenChords(t, setOf("G", "D"))
        assertEquals(setOf("G", "D"), Chords.hiddenChords(t))
        assertEquals(listOf("C"), com.juliano.stagebook.ui.stage.stripChords(t))
        assertEquals("[G]a [D]b [C]c", Chords.setHiddenChords(t, emptySet()))
        assertTrue(Chords.isDirective(t.lines().first()))   // o palco não mostra a linha
    }

    @Test fun `diretivas gravam, trocam e saem`() {
        var t = "[G]oi"
        t = Chords.setDirective(t, "capo", "3")
        assertEquals("{capo: 3}\n[G]oi", t)
        t = Chords.setDirective(t, "capo", "5")
        assertEquals(5, Chords.capo(t))
        assertEquals("[G]oi", Chords.setDirective(t, "capo", ""))
    }

    @Test fun `só letra tira acordes, linhas de acorde e diretivas e mantém seções`() {
        val song = "{key: G}\n[Refrão]\n[G]Hoje eu [D] vou\n[Em] [C]\nfim"
        assertEquals("[Refrão]\nHoje eu vou\nfim", Chords.stripChords(song))
        assertTrue(Chords.hasChords(song))
        assertFalse(Chords.hasChords("[Refrão]\nsó letra"))
    }
}
