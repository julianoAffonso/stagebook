package br.app.stagebook

import br.app.stagebook.domain.Chords
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyDetectTest {
    @Test fun `progression in G major`() =
        assertEquals("G", Chords.detectKey("[G]Saio [D]cedo [Em]pela [C]rua\n[G]fim"))

    @Test fun `minor by the tonic at the start and end`() =
        assertEquals("Am", Chords.detectKey("[Am]um [Dm]dois [E7]três [Am]quatro"))

    @Test fun `flat key comes out with flats`() =
        assertEquals("F", Chords.detectKey("[F]um [Bb]dois [C7]três [F]quatro"))

    @Test fun `chords above the lyrics too`() =
        assertEquals("D", Chords.detectKey("D        A\nLinha um\nBm       G\nLinha dois\nD"))

    @Test fun `without chords it does not make up a key`() = assertEquals("", Chords.detectKey("só letra\nsem acorde"))

    @Test fun `key already written wins`() {
        val text = "{key: A}\n[G]um [D]dois"
        assertEquals(text, Chords.withDetectedKey(text))
        assertEquals("G", Chords.key(Chords.withDetectedKey("[G]um [C]dois [D]três [G]quatro")))
    }

    @Test fun `editor - key and capo leave the lyrics field and come back on save`() {
        val text = "{key: G}\n{capo: 2}\n[Verso]\n[G]linha"
        assertEquals("[Verso]\n[G]linha", Chords.hideMeta(text))
        assertEquals(text, Chords.withMeta("[Verso]\n[G]linha editada".replace(" editada", ""), text))
        // sem tom nem capo: nada é acrescentado
        assertEquals("[G]linha", Chords.withMeta("[G]linha", "[G]outra"))
    }

    @Test fun `song BPM - read from tempo, out of the lyrics field and back on save`() {
        val text = "{tempo: 120}\n{key: G}\n[G]linha"
        assertEquals(120, Chords.tempo(text))
        assertEquals(0, Chords.tempo("{tempo: 900}\nx"))   // fora do razoável: sem BPM
        assertEquals("[G]linha", Chords.hideMeta(text))
        assertEquals(120, Chords.tempo(Chords.withMeta("[G]linha", text)))
    }

    @Test fun `import - BPM from the page or written in the text`() {
        assertEquals(128, br.app.stagebook.domain.ChordImport.bpmOf("128", ""))
        assertEquals(96, br.app.stagebook.domain.ChordImport.bpmOf("", "Intro\nBPM: 96\n[G]linha"))
        assertEquals(110, br.app.stagebook.domain.ChordImport.bpmOf("", "110 bpm\nletra"))
        assertEquals(72, br.app.stagebook.domain.ChordImport.bpmOf("", "{tempo: 72}\nletra"))
        assertEquals(0, br.app.stagebook.domain.ChordImport.bpmOf("", "a letra fala de 120 caminhos"))   // número solto não é BPM
        assertEquals(0, br.app.stagebook.domain.ChordImport.bpmOf("999", ""))
    }

    @Test fun `key moves a half step at a time with the usual name`() {
        val up = generateSequence("C") { Chords.transposeKey(it, 1) }.take(13).toList()
        assertEquals(listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B", "C"), up)
        val minor = generateSequence("Am") { Chords.transposeKey(it, 1) }.take(4).toList()
        assertEquals(listOf("Am", "Bbm", "Bm", "Cm"), minor)
        assertEquals("E", Chords.transposeKey("F", -1))
        // os acordes seguem a armadura do tom novo
        assertEquals("{key: Eb}\n[Eb]um [Ab]dois [Bb]três", Chords.transpose("{key: D}\n[D]um [G]dois [A]três", 1))
        assertEquals("{key: F#}\n[F#]um [B]dois [C#]três", Chords.transpose("{key: F}\n[F]um [Bb]dois [C]três", 1))
    }
}
