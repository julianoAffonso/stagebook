package br.app.stagebook

import br.app.stagebook.domain.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun `preto e branco é o máximo, cor igual é o mínimo`() {
        assertEquals(21.0, Contrast.ratio(black, white), 0.01)
        assertEquals(1.0, Contrast.ratio(black, black), 0.01)
    }

    @Test fun `igual ou parecido demais avisa, o resto passa`() {
        assertFalse(Contrast.legible(black, black))                       // mesma cor
        assertFalse(Contrast.legible(0xFF333333.toInt(), black))          // cinza-escuro no preto
        assertFalse(Contrast.legible(0xFFFFEB3B.toInt(), white))          // amarelo no branco
        assertTrue(Contrast.legible(0xFFFFC107.toInt(), black))           // acorde âmbar no preto (padrão)
        assertTrue(Contrast.legible(0xFF4AF6CD.toInt(), black))           // seção verde no preto (padrão)
        assertTrue(Contrast.legible(black, 0xFFFFF8E1.toInt()))           // letra preta no creme
    }
}
