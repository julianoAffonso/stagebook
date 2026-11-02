package br.app.stagebook

import br.app.stagebook.domain.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun `black and white is the most, same color is the least`() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.01)
        assertEquals(1.0, Contrast.ratio(black, black), 0.01)
    }

    @Test fun `same or too similar warns, the rest passes`() {
        assertFalse(Contrast.legible(black, black))                       // mesma cor
        assertFalse(Contrast.legible(0xFF333333.toInt(), black))          // dark gray on black
        assertFalse(Contrast.legible(0xFFFFEB3B.toInt(), white))          // yellow on white
        assertTrue(Contrast.legible(0xFFFFC107.toInt(), black))           // amber chord on black (default)
        assertTrue(Contrast.legible(0xFF4AF6CD.toInt(), black))           // green section on black (default)
        assertTrue(Contrast.legible(black, 0xFFFFF8E1.toInt()))           // black lyrics on cream
    }
    @Test fun `the marker is judged over the background, as the screen shows it (user, 01-11)`() {
        // 40% yellow over black = dark yellow; over white = light yellow
        assertEquals(0xFF665E17.toInt(), br.app.stagebook.domain.Contrast.over(0x66FFEB3B, 0xFF000000.toInt()))
        assertEquals(0xFFFFF7B0.toInt(), br.app.stagebook.domain.Contrast.over(0x66FFEB3B, 0xFFFFFFFF.toInt()))
        // every marker option keeps white lyrics readable on black (the default look)
        br.app.stagebook.ui.theme.StagePalette.highlight.forEach { m ->
            assertTrue(Integer.toHexString(m), br.app.stagebook.domain.Contrast.legible(0xFFFFFFFF.toInt(), br.app.stagebook.domain.Contrast.over(m, 0xFF000000.toInt())))
        }
    }
}
