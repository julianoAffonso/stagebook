package br.app.stagebook.domain

import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfPoint

/** The trims of a PDF's pages: which one a page has, how one is kept valid and how it is set or taken off. */
object PdfCrops {
    /** The smallest side a trim may have (a fraction of the page): a sliver is no trim. */
    const val MIN_SIDE = 0.1f

    /** The trim for [page]: its own, else the one for all pages, else none. */
    fun forPage(crops: List<PdfCrop>, page: Int): PdfCrop? =
        (crops.firstOrNull { it.page == page } ?: crops.firstOrNull { it.page == PdfCrop.ALL })?.takeUnless { it.isWhole }

    /** [crop] inside the page and at least [MIN_SIDE] on each side. */
    fun valid(crop: PdfCrop): PdfCrop {
        val left = crop.left.coerceIn(0f, 1f - MIN_SIDE)
        val top = crop.top.coerceIn(0f, 1f - MIN_SIDE)
        return crop.copy(left = left, top = top, right = crop.right.coerceIn(left + MIN_SIDE, 1f), bottom = crop.bottom.coerceIn(top + MIN_SIDE, 1f))
    }

    /** [crops] with [crop] set for its page (or for all pages): a whole page is no trim, so it takes the one there was off. */
    fun set(crops: List<PdfCrop>, crop: PdfCrop): List<PdfCrop> {
        val rest = crops.filter { it.page != crop.page }
        val v = valid(crop)
        return if (v.isWhole) rest else rest + v
    }

    /** [crops] without the trim for [page] ([PdfCrop.ALL] for the one for all pages). */
    fun clear(crops: List<PdfCrop>, page: Int): List<PdfCrop> = crops.filter { it.page != page }

    /**
     * The width to draw a whole page of [size] (points) so that its trim [crop] (or all of it) fills a box of [boxW] x [boxH]
     * as far as it fits: the part shown takes the box, the whole page is that much wider.
     */
    fun wholeWidth(size: Pair<Int, Int>, crop: PdfCrop?, boxW: Int, boxH: Int): Int {
        val cw = crop?.let { it.right - it.left } ?: 1f
        val ch = crop?.let { it.bottom - it.top } ?: 1f
        val factor = minOf(boxW / (size.first * cw), boxH / (size.second * ch))
        return PdfZoom.capWidth(((size.first * cw * factor) / cw).toInt().coerceAtLeast(1), size.first, size.second)
    }

    /** A point of the shown part ([x], [y] across and down it, 0 to 1) as a point of the whole page. */
    fun toPage(crop: PdfCrop?, p: PdfPoint): PdfPoint =
        if (crop == null) p else PdfPoint(crop.left + p.x * (crop.right - crop.left), crop.top + p.y * (crop.bottom - crop.top))
}
