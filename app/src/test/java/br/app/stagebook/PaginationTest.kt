package br.app.stagebook

import br.app.stagebook.domain.Pagination
import br.app.stagebook.domain.Pagination.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PaginationTest {
    // text: verses of lines 10 tall, separated by a blank line
    private fun lines(vararg stanzaSizes: Int): List<Line> =
        stanzaSizes.toList().flatMapIndexed { i, n -> (if (i > 0) listOf(Line(10, true)) else emptyList()) + List(n) { Line(10, false) } }

    @Test
    fun `everything fits on one page`() {
        assertEquals(listOf(0..8), Pagination.paginate(lines(4, 4), 100))
    }

    @Test
    fun `breaks between verses, never in the middle of one that fits whole`() {
        // 4 + blank + 4 = 90; the third verse (4) no longer fits
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
        // 4-line verses, a column 90 tall (2 verses per column)
        val sheets = Pagination.sheets(lines(4, 4, 4, 4, 4), 90, 2)
        assertEquals(listOf(listOf(0..8, 10..18), listOf(20..23)), sheets)
        assertEquals(3, Pagination.sheets(lines(4, 4, 4, 4, 4), 90, 1).size)
    }
    @Test fun `a section title followed by a blank line goes to the next page with its verse (device test B-13)`() {
        val L = { h: Int -> Pagination.Line(h, false) }
        val blank = Pagination.Line(10, true)
        val title = Pagination.Line(20, false, title = true)
        // page of 100: verse (60) + blank + title (20) would fit, but the title's verse (50) goes to the next page
        val lines = listOf(L(30), L(30), blank, title, blank, L(25), L(25))
        val pages = Pagination.paginate(lines, 100)
        assertEquals(listOf(0..1, 3..6), pages)
    }

    // À Francesa on the S26 (05/11): verse and chorus with no blank line between them were one verse too tall for the
    // page, so it was cut and the intro stayed alone
    @Test fun `a section starts a new verse even without a blank line`() {
        val title = Pagination.Line(100, false, title = true)
        val text = Pagination.Line(100, false)
        val lines = listOf(title, text, text, title, text, text)
        assertEquals(listOf(0..2, 3..5), Pagination.paginate(lines, 400))
        assertFalse(Pagination.cutsVerse(lines, 400))
    }

    @Test fun `sections without a blank line between them share the page`() {
        val title = Pagination.Line(100, false, title = true)
        val text = Pagination.Line(100, false)
        assertEquals(listOf(0..3), Pagination.paginate(listOf(title, text, title, text), 400))
    }
}
