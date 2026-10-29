package com.juliano.stagebook

import com.juliano.stagebook.domain.Chords
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyDetectTest {
    @Test fun `progressão em Sol maior`() =
        assertEquals("G", Chords.detectKey("[G]Saio [D]cedo [Em]pela [C]rua\n[G]fim"))

    @Test fun `menor pela tônica no começo e no fim`() =
        assertEquals("Am", Chords.detectKey("[Am]um [Dm]dois [E7]três [Am]quatro"))

    @Test fun `tom de bemol sai com bemol`() =
        assertEquals("F", Chords.detectKey("[F]um [Bb]dois [C7]três [F]quatro"))

    @Test fun `cifra em cima da letra também`() =
        assertEquals("D", Chords.detectKey("D        A\nLinha um\nBm       G\nLinha dois\nD"))

    @Test fun `sem acordes não inventa tom`() = assertEquals("", Chords.detectKey("só letra\nsem acorde"))

    @Test fun `tom já escrito prevalece`() {
        val text = "{key: A}\n[G]um [D]dois"
        assertEquals(text, Chords.withDetectedKey(text))
        assertEquals("G", Chords.key(Chords.withDetectedKey("[G]um [C]dois [D]três [G]quatro")))
    }

    @Test fun `editor - tom e capo saem do campo da letra e voltam ao salvar`() {
        val text = "{key: G}\n{capo: 2}\n[Verso]\n[G]linha"
        assertEquals("[Verso]\n[G]linha", Chords.hideMeta(text))
        assertEquals(text, Chords.withMeta("[Verso]\n[G]linha editada".replace(" editada", ""), text))
        // sem tom nem capo: nada é acrescentado
        assertEquals("[G]linha", Chords.withMeta("[G]linha", "[G]outra"))
    }

    @Test fun `BPM da música - lido do tempo, fora do campo da letra e de volta ao salvar`() {
        val text = "{tempo: 120}\n{key: G}\n[G]linha"
        assertEquals(120, Chords.tempo(text))
        assertEquals(0, Chords.tempo("{tempo: 900}\nx"))   // fora do razoável: sem BPM
        assertEquals("[G]linha", Chords.hideMeta(text))
        assertEquals(120, Chords.tempo(Chords.withMeta("[G]linha", text)))
    }
}
