package br.app.stagebook.domain

/**
 * Splits the lyrics into stage pages (the pedal turns the whole page, no scrolling). Breaks between verses; a section
 * title goes with its verse. Only a verse longer than the page is cut, by line.
 */
object Pagination {
    /** A lyrics line already measured at the screen width (the height counts automatic wrapping). */
    data class Line(val height: Int, val blank: Boolean)

    /** Line ranges for each page (no blank lines at the edges). */
    fun paginate(lines: List<Line>, pageHeight: Int): List<IntRange> {
        val blocks = blocks(lines)
        if (blocks.isEmpty()) return listOf(IntRange.EMPTY)
        val gap = lines.firstOrNull { it.blank }?.height ?: 0
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
                    // verse longer than the page: cut by line
                    flush()
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

    /** Verses: consecutive non-empty lines. */
    private fun blocks(lines: List<Line>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].blank) { i++; continue }
            val s = i
            while (i < lines.size && !lines[i].blank) i++
            out += s until i
        }
        return out
    }
}
