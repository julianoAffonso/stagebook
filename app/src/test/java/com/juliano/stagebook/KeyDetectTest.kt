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
}
