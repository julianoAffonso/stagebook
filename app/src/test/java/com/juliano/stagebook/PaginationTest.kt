package com.juliano.stagebook

import com.juliano.stagebook.domain.Pagination
import com.juliano.stagebook.domain.Pagination.Line
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTest {
    // texto: estrofes de linhas com altura 10, separadas por uma linha em branco
    private fun lines(vararg stanzaSizes: Int): List<Line> =
        stanzaSizes.toList().flatMapIndexed { i, n -> (if (i > 0) listOf(Line(10, true)) else emptyList()) + List(n) { Line(10, false) } }

    @Test
    fun `tudo cabe numa página`() {
        assertEquals(listOf(0..8), Pagination.paginate(lines(4, 4), 100))
    }

    @Test
    fun `quebra entre estrofes, nunca no meio de uma que cabe inteira`() {
        // 4 + branco + 4 = 90; a terceira estrofe (4) não cabe mais
        assertEquals(listOf(0..8, 10..13), Pagination.paginate(lines(4, 4, 4), 90))
    }

    @Test
    fun `estrofe maior que a página é cortada por linha`() {
        assertEquals(listOf(0..1, 3..7, 8..9), Pagination.paginate(lines(2, 7), 50))
    }

    @Test
    fun `brancos sobrando nas bordas não viram página`() {
        val l = listOf(Line(10, true), Line(10, false), Line(10, true), Line(10, true), Line(10, false), Line(10, true))
        assertEquals(listOf(1..4), Pagination.paginate(l, 100))
        assertEquals(listOf(IntRange.EMPTY), Pagination.paginate(emptyList(), 100))
    }

    @Test
    fun `duas colunas enchem a esquerda e depois a direita, o pedal vira a folha`() {
        // estrofes de 4 linhas, coluna com 90 de altura (2 estrofes por coluna)
        val sheets = Pagination.sheets(lines(4, 4, 4, 4, 4), 90, 2)
        assertEquals(listOf(listOf(0..8, 10..18), listOf(20..23)), sheets)
        assertEquals(3, Pagination.sheets(lines(4, 4, 4, 4, 4), 90, 1).size)
    }
}
