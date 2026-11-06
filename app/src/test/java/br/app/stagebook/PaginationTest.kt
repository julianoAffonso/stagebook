package br.app.stagebook

import br.app.stagebook.domain.Pagination
import br.app.stagebook.domain.Pagination.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals(listOf(0..4, 5..9), Pagination.paginate(lines(2, 7), 50))
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

    // À Francesa on the S26 on its side (05/11): the column is low, the verse gets cut anyway and the intro was left alone
    @Test fun `a verse that gets cut anyway starts right after the short one before it`() {
        val title = Pagination.Line(100, false, title = true)
        val text = Pagination.Line(100, false)
        val blank = Pagination.Line(100, true)
        val lines = listOf(title, text, blank, title) + List(8) { text }
        assertEquals(listOf(0..5, 6..11), Pagination.paginate(lines, 600))
    }

    // a verse taller than a column has to split, but the two columns of one sheet are read together: not across the pedal
    @Test fun `a verse too long for a column starts a sheet when it wouldn't fit the rest of this one`() {
        assertEquals(listOf(listOf(0..7), listOf(9..18, 19..23)), Pagination.sheets(lines(8, 15), 100, 2))
    }

    @Test fun `a verse too long for a column stays on this sheet when it fits`() {
        assertEquals(1, Pagination.sheets(lines(3, 15), 100, 2).size)
    }

    @Test fun `a verse never leaves an intro alone on its sheet`() {
        assertEquals(2, Pagination.sheets(lines(1, 18), 100, 2).first().size)
    }

    @Test fun `a verse goes on from a column nearly empty to the next one`() {
        val sheets = Pagination.sheets(lines(2, 9), 100, 2)
        assertEquals(1, sheets.size)
        assertEquals(listOf(0..9, 10..11), sheets.single())
    }

    @Test fun `a verse doesn't split when the column before it is well used`() {
        assertEquals(listOf(0..4, 6..14), Pagination.sheets(lines(5, 9), 100, 2).single())
    }

    @Test fun `a verse too long for a sheet goes on from where it is`() {
        assertEquals(listOf(0..9, 11..20, 21..30, 31..40), Pagination.sheets(lines(10, 30), 100, 2).flatten())
    }

    @Test fun `a verse over the page turn is told apart`() {
        val long = lines(10, 30)
        assertEquals(true, Pagination.crossesSheet(long, Pagination.sheets(long, 100, 2)))
        val short = lines(8, 15)
        assertFalse(Pagination.crossesSheet(short, Pagination.sheets(short, 100, 2)))
    }

    @Test fun `a verse doesn't start on the last line of a page`() {
        val text = Pagination.Line(100, false)
        val title = Pagination.Line(100, false, title = true)
        val blank = Pagination.Line(100, true)
        val lines = List(4) { text } + blank + title + List(8) { text }
        assertEquals("só o título caberia", 5, Pagination.paginate(lines, 600)[1].first)
    }

    // a tab's strings are read together: E and B in one column and the rest in the next said nothing
    @Test fun `a tab cut from a long verse stays whole`() {
        val text = Line(10, false)
        val tab = Line(10, false, tab = true)
        val lines = List(4) { text } + List(6) { tab } + List(4) { text }
        val pages = Pagination.paginate(lines, 70)
        assertTrue(pages.toString(), pages.any { 4 in it && 9 in it })
    }

    @Test fun `a tab taller than the page is still cut`() {
        val tab = Line(10, false, tab = true)
        assertEquals(listOf(0..4, 5..9), Pagination.paginate(List(10) { tab }, 50))
    }
}
