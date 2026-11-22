package br.app.stagebook.domain

/**
 * Which of the song's lines the stage has on screen, for the external screen to show the same part (user, 22/11). The
 * stage's lines are pieces of the song's lines (a wrapped line, the chord list): [sources] gives, for each, the song line
 * it came from, -1 for lines the stage made.
 */
object MirrorLines {
    /** The song lines behind the stage lines [shown] (one page, or the columns of a sheet); null when none is a song line. */
    fun sourceRange(sources: List<Int>, shown: List<IntRange>): IntRange? {
        var lo = Int.MAX_VALUE
        var hi = -1
        for (range in shown) for (i in range) {
            val s = sources.getOrNull(i) ?: continue
            if (s < 0) continue
            if (s < lo) lo = s
            if (s > hi) hi = s
        }
        return if (hi < 0) null else lo..hi
    }

    /**
     * A PDF song's kept lyrics on the external screen (user, 22/11, as in the benchmark: text in place of the PDF): the
     * [count] lines split in as many parts as the stage has pages ([pages]), each cut moved to the nearest blank line
     * (between verses) when one is close. The part of page [page], as a range of lines; every line is in one part.
     */
    fun pdfPart(count: Int, blank: (Int) -> Boolean, pages: Int, page: Int): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val n = pages.coerceIn(1, count)
        val p = page.coerceIn(0, n - 1)
        val reach = (count / n / 2).coerceAtLeast(0)
        fun cut(k: Int): Int {   // where part k starts
            if (k <= 0) return 0
            if (k >= n) return count
            val at = k * count / n
            val near = (0..reach).asSequence().flatMap { d -> sequenceOf(at - d, at + d) }.firstOrNull { it in 1 until count && blank(it) }
            return near?.plus(1)?.coerceAtMost(count) ?: at   // the part starts after the blank line
        }
        val from = cut(p)
        val to = maxOf(from, cut(p + 1))
        return from until to
    }
}
