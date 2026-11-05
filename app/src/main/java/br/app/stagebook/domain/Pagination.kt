package br.app.stagebook.domain

/**
 * Splits the lyrics into stage pages (the pedal turns the whole page, no scrolling). Breaks between verses and before a
 * section title, which goes with its verse. Only a verse longer than the page is cut, by line, right after what came before.
 */
object Pagination {
    private const val THIN_PAGE = 3   // a page under a third full counts as thin
    private const val OPENING_LINES = 2   // a title alone at the end of a page says nothing

    /** A lyrics line already measured at the screen width (the height counts automatic wrapping). */
    data class Line(val height: Int, val blank: Boolean, val title: Boolean = false)

    /** Line ranges for each page (no blank lines at the edges). */
    fun paginate(lines: List<Line>, pageHeight: Int): List<IntRange> {
        val blocks = blocks(lines)
        if (blocks.isEmpty()) return listOf(IntRange.EMPTY)
        val blank = lines.firstOrNull { it.blank }?.height ?: 0
        val pages = ArrayList<IntRange>()
        var start = -1
        var end = -1
        var used = 0

        fun flush() {
            if (start >= 0) pages += start..end
            start = -1; end = -1; used = 0
        }

        for (block in blocks) {
            val h = block.sumOf { lines[it].height }
            // a section right under the last line has no blank line between them
            val gap = if (lines.getOrNull(block.first - 1)?.blank == true) blank else 0
            val need = if (start < 0) h else used + gap + h
            when {
                need <= pageHeight -> {
                    if (start < 0) start = block.first
                    end = block.last
                    used = need
                }
                h <= pageHeight -> {
                    flush()
                    start = block.first; end = block.last; used = h
                }
                else -> {
                    // verse longer than the page: cut by line. It gets cut anyway, so it goes on from here when its
                    // opening fits; a new page left the short verse before it alone (an intro, in a low column)
                    val opening = block.take(OPENING_LINES).sumOf { lines[it].height }
                    if (start >= 0 && used + gap + opening <= pageHeight) used += gap else flush()
                    for (i in block) {
                        val lh = lines[i].height
                        if (start >= 0 && used + lh > pageHeight) flush()
                        if (start < 0) start = i
                        end = i
                        used += lh
                    }
                }
            }
        }
        flush()
        return pages
    }

    /**
     * Stage sheets with `columns` columns: the columns fill in order (left, then right), each as tall as the page; the
     * pedal turns the whole sheet.
     */
    fun sheets(lines: List<Line>, columnHeight: Int, columns: Int): List<List<IntRange>> =
        paginate(lines, columnHeight).chunked(columns.coerceAtLeast(1))

    /** Whether some verse is taller than the page and has to be cut by line. */
    fun cutsVerse(lines: List<Line>, pageHeight: Int): Boolean = blocks(lines).any { b -> b.sumOf { lines[it].height } > pageHeight }

    /** Whether the first of several [pages] has only a sliver of text, like an intro left alone before a long verse. */
    fun thinFirstPage(lines: List<Line>, pages: List<IntRange>, pageHeight: Int): Boolean =
        pages.size > 1 && pages[0].sumOf { lines[it].height } * THIN_PAGE < pageHeight

    /** Verses: consecutive non-empty lines, and a section title starts a new one even with no blank line before it. */
    private fun blocks(lines: List<Line>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].blank) { i++; continue }
            val s = i++
            while (i < lines.size && !lines[i].blank && !lines[i].title) i++
            out += s until i
        }
        // a section title alone (a blank line after it) goes with the next verse: it was left at the bottom of a page,
        // with its verse on the next one (device test B-13)
        val merged = ArrayList<IntRange>(out.size)
        var k = 0
        while (k < out.size) {
            val b = out[k]
            if (b.first == b.last && lines[b.first].title && k + 1 < out.size) { merged += b.first..out[k + 1].last; k += 2 }
            else { merged += b; k++ }
        }
        return merged
    }
}
