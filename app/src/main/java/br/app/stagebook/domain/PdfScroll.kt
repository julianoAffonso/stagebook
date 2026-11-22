package br.app.stagebook.domain

/**
 * A PDF in the stage's scroll mode (user, 22/11): its pages one under the other, each as wide as the screen. A pedal
 * press moves on by [STEP] of the screen, so the last lines of one stretch are still on top of the next and the eye
 * finds where to go on. [anchor]: where the automatic scroll paused; the steps go on from there.
 */
object PdfScroll {
    const val STEP = 0.9f

    /** Each page's height on a screen [width] wide, from its shape (the part shown, with a trim): [shapes] = width to height. */
    fun heights(shapes: List<Pair<Float, Float>>, width: Int): List<Int> =
        shapes.map { (w, h) -> if (w <= 0f) 0 else (width * h / w).toInt().coerceAtLeast(1) }

    /** Where each page starts, in the column of pages. */
    fun tops(heights: List<Int>): List<Int> = heights.runningFold(0) { acc, h -> acc + h }.dropLast(1)

    /** The scroll positions a pedal press goes through: from the top to the end of the last page. */
    fun steps(total: Int, viewport: Int, anchor: Int = 0): List<Int> {
        val max = (total - viewport).coerceAtLeast(0)
        if (viewport <= 0 || max == 0) return listOf(0)
        val stride = (viewport * STEP).toInt().coerceAtLeast(1)
        val stop = anchor.coerceIn(0, max)
        val out = mutableListOf(0)
        var pos = 0
        var anchored = stop == 0
        while (pos < max) {
            var next = (pos + stride).coerceAtMost(max)
            if (!anchored && next >= stop) { anchored = true; if (next != stop) next = stop }
            out += next
            pos = next
        }
        return out
    }

    /** The pages that touch the screen at [offset] (one more above and below, drawn ahead). */
    fun visible(tops: List<Int>, heights: List<Int>, offset: Int, viewport: Int): IntRange {
        if (tops.isEmpty()) return IntRange.EMPTY
        val first = tops.indices.firstOrNull { tops[it] + heights[it] > offset } ?: tops.lastIndex
        val last = tops.indices.lastOrNull { tops[it] < offset + viewport } ?: first
        return (first - 1).coerceAtLeast(0)..(last + 1).coerceAtMost(tops.lastIndex)
    }

    /** The page at height [y] of the pages one under the other (the external screen shows the one in the middle of the view). */
    fun pageAt(tops: List<Int>, y: Int): Int = tops.indexOfLast { it <= y }.coerceAtLeast(0)
}
