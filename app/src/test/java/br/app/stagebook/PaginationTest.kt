package br.app.stagebook

import br.app.stagebook.domain.Pagination
import br.app.stagebook.domain.Pagination.Line
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTest {
    // texto: estrofes de linhas com altura 10, separadas por uma linha em branco
    private fun lines(vararg stanzaSizes: Int): List<Line> =
        stanzaSizes.toList().flatMapIndexed { i, n -> (if (i > 0) listOf(Line(10, true)) else emptyList()) + List(n) { Line(10, false) } }

    @Test
    fun `everything fits on one page`() {
        assertEquals(listOf(0..8), Pagination.paginate(lines(4, 4), 100))
    }

    @Test
    fun `breaks between verses, never in the middle of one that fits whole`() {
        // 4 + branco + 4 = 90; a terceira estrofe (4) não cabe mais
        assertEquals(listOf(0..8, 10..13), Pagination.paginate(lines(4, 4, 4), 90))
    }

    @Test
    fun `verse longer than the page is cut by line`() {
        assertEquals(listOf(0..1, 3..7, 8..9), Pagination.paginate(lines(2, 7), 50))
    }

    @Test
    fun `blank space at the edges does not become a page`() {
        val l = listOf(Line(10, true), Line(10, false), Line(10, true), Line(10, true), Line(10, false), Line(10, true))
        assertEquals(listOf(1..4), Pagination.paginate(l, 100))
        assertEquals(listOf(IntRange.EMPTY), Pagination.paginate(emptyList(), 100))
    }

    @Test
    fun `two columns fill the left then the right, the pedal turns the sheet`() {
        // estrofes de 4 linhas, coluna com 90 de altura (2 estrofes por coluna)
        val sheets = Pagination.sheets(lines(4, 4, 4, 4, 4), 90, 2)
        assertEquals(listOf(listOf(0..8, 10..18), listOf(20..23)), sheets)
        assertEquals(3, Pagination.sheets(lines(4, 4, 4, 4, 4), 90, 1).size)
    }
}
