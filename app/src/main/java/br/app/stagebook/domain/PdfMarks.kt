package br.app.stagebook.domain

import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.data.model.PdfPoint

/**
 * The highlighter on a PDF page: strokes in coordinates of the page itself (0 to 1 across and down), so they sit in the same
 * place whatever the screen, the zoom or the colours, and travel with the song in a backup.
 */
object PdfMarks {
    /** A stroke keeps at most this many points: a long scribble stays small in the library file. */
    const val MAX_POINTS = 400
    /** A point closer than this (a fraction of the page) to the last one kept adds nothing to the stroke. */
    const val MIN_STEP = 0.004f
    /** The thickness of the highlighter, as a fraction of the page's width. */
    const val WIDTH = 0.025f
    /** A page keeps this many strokes: past it the oldest go, so a page covered in scribbles stays light to draw and to save. */
    const val MAX_PER_PAGE = 200

    /**
     * Where a touch at ([x], [y]) in a box of [boxW] x [boxH] falls on the page: a page of [fitW] x [fitH] at its size,
     * enlarged by [scale], moved by ([offsetX], [offsetY]) from the centre. Null when it falls outside the page.
     */
    fun onPage(x: Float, y: Float, boxW: Float, boxH: Float, fitW: Float, fitH: Float, scale: Float, offsetX: Float, offsetY: Float): PdfPoint? {
        val w = fitW * scale
        val h = fitH * scale
        val left = boxW / 2f + offsetX - w / 2f
        val top = boxH / 2f + offsetY - h / 2f
        val px = (x - left) / w
        val py = (y - top) / h
        return if (px in 0f..1f && py in 0f..1f) PdfPoint(px, py) else null
    }

    /** [points] as they came, thinned: the ones that moved at least [MIN_STEP], the last one always, and no more than [MAX_POINTS]. */
    fun thin(points: List<PdfPoint>): List<PdfPoint> {
        if (points.size <= 1) return points
        val kept = ArrayList<PdfPoint>()
        for (p in points) {
            val last = kept.lastOrNull()
            if (last == null || kotlin.math.abs(p.x - last.x) >= MIN_STEP || kotlin.math.abs(p.y - last.y) >= MIN_STEP) kept += p
        }
        if (kept.last() != points.last()) kept += points.last()
        return if (kept.size <= MAX_POINTS) kept else List(MAX_POINTS) { kept[(it.toLong() * (kept.size - 1) / (MAX_POINTS - 1)).toInt()] }
    }

    fun add(marks: List<PdfMark>, page: Int, points: List<PdfPoint>): List<PdfMark> {
        if (points.isEmpty()) return marks
        val all = marks + PdfMark(page, thin(points), WIDTH)
        val extra = all.count { it.page == page } - MAX_PER_PAGE
        if (extra <= 0) return all
        var drop = extra
        return all.filter { !(it.page == page && drop-- > 0) }
    }

    /** The last stroke made on [page] goes. */
    fun undoLast(marks: List<PdfMark>, page: Int): List<PdfMark> {
        val at = marks.indexOfLast { it.page == page }
        return if (at < 0) marks else marks.filterIndexed { i, _ -> i != at }
    }

    fun clearPage(marks: List<PdfMark>, page: Int): List<PdfMark> = marks.filter { it.page != page }
}
