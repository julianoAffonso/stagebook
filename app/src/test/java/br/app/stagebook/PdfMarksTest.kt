package br.app.stagebook

import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.data.model.PdfPoint
import br.app.stagebook.domain.PdfMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The highlighter on a PDF page (user, 15/11): strokes kept in the page's own coordinates. */
class PdfMarksTest {
    private fun at(x: Float, y: Float, scale: Float = 1f, ox: Float = 0f, oy: Float = 0f) =
        PdfMarks.onPage(x, y, boxW = 1000f, boxH = 1500f, fitW = 800f, fitH = 1200f, scale = scale, offsetX = ox, offsetY = oy)

    @Test fun `the page's own corners are 0 and 1, wherever it sits in the box`() {
        // 800 x 1200 centred in 1000 x 1500: it starts at 100, 150
        assertEquals(PdfPoint(0f, 0f), at(100f, 150f))
        assertEquals(PdfPoint(1f, 1f), at(900f, 1350f))
        assertEquals(PdfPoint(0.5f, 0.5f), at(500f, 750f))
    }

    @Test fun `a touch beside the page is not on it`() {
        assertNull(at(50f, 750f))
        assertNull(at(500f, 1450f))
    }

    @Test fun `with the page enlarged and moved a touch lands on the same place of the page`() {
        // twice as large and moved 100 left: the page's centre is at 400, 750
        assertEquals(PdfPoint(0.5f, 0.5f), at(400f, 750f, scale = 2f, ox = -100f))
        val p = at(400f - 800f / 2f * 2f / 2f, 750f, scale = 2f, ox = -100f)   // half a page-width to the left of centre = a quarter in
        assertNotNull(p)
        assertEquals(0.25f, p!!.x, 0.0001f)
    }

    @Test fun `a stroke drops the points that did not move and keeps its end`() {
        val points = listOf(PdfPoint(0.1f, 0.1f), PdfPoint(0.1001f, 0.1f), PdfPoint(0.2f, 0.1f), PdfPoint(0.2001f, 0.1001f))
        val thin = PdfMarks.thin(points)
        assertEquals(listOf(PdfPoint(0.1f, 0.1f), PdfPoint(0.2f, 0.1f), PdfPoint(0.2001f, 0.1001f)), thin)
    }

    @Test fun `a very long stroke is cut to the most points`() {
        val points = List(5000) { PdfPoint(if (it % 2 == 0) 0f else 1f, it / 5000f) }   // a zig-zag: every point moved
        val thin = PdfMarks.thin(points)
        assertEquals(PdfMarks.MAX_POINTS, thin.size)
        assertEquals(points.first(), thin.first())
        assertEquals(points.last(), thin.last())
    }

    @Test fun `a stroke is added to the page it was made on, with the highlighter's thickness`() {
        val marks = PdfMarks.add(emptyList(), 2, listOf(PdfPoint(0.1f, 0.1f), PdfPoint(0.5f, 0.1f)))
        assertEquals(1, marks.size)
        assertEquals(2, marks[0].page)
        assertEquals(PdfMarks.WIDTH, marks[0].width, 0f)
        assertEquals(marks, PdfMarks.add(marks, 2, emptyList()))
    }

    @Test fun `a page keeps at most 200 strokes and the oldest go first, other pages are not touched`() {
        var marks = listOf(PdfMark(5, listOf(PdfPoint(0f, 0f))))
        repeat(PdfMarks.MAX_PER_PAGE + 30) { i -> marks = PdfMarks.add(marks, 1, listOf(PdfPoint(0.1f, i / 1000f), PdfPoint(0.9f, i / 1000f))) }
        assertEquals(PdfMarks.MAX_PER_PAGE, marks.count { it.page == 1 })
        assertEquals(1, marks.count { it.page == 5 })
        // the oldest of the page went: the first kept is the 31st stroke made
        assertEquals(30 / 1000f, marks.first { it.page == 1 }.points.first().y, 0.0001f)
    }

    @Test fun `undo takes the last stroke of the page and clear takes them all, other pages stay`() {
        val a = PdfMark(0, listOf(PdfPoint(0f, 0f)))
        val b = PdfMark(1, listOf(PdfPoint(0.1f, 0.1f)))
        val c = PdfMark(1, listOf(PdfPoint(0.2f, 0.2f)))
        val marks = listOf(a, b, c)
        assertEquals(listOf(a, b), PdfMarks.undoLast(marks, 1))
        assertEquals(marks, PdfMarks.undoLast(marks, 5))
        assertEquals(listOf(a), PdfMarks.clearPage(marks, 1))
        assertTrue(PdfMarks.clearPage(listOf(a), 0).isEmpty())
    }
}
