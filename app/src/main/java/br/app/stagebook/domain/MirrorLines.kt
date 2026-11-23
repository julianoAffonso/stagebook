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
        // more pages than lines: the pages share the parts in order, in place of all the extra ones showing the last
        val p = (if (pages > n) page.toLong() * n / pages else page.toLong()).toInt().coerceIn(0, n - 1)
        val reach = count / n / 2
        // where each part starts: near a blank line when there is one, and always after the part before (two cuts on
        // the same blank line left a page with nothing, audit 22/11)
        val starts = IntArray(n + 1)
        starts[n] = count
        for (k in 1 until n) {
            val at = k * count / n
            val near = (0..reach).asSequence().flatMap { d -> sequenceOf(at - d, at + d) }.firstOrNull { it in 1 until count && blank(it) }?.plus(1)
            val least = starts[k - 1] + 1          // the part before keeps a line
            val most = count - (n - k)             // and every part after gets one
            starts[k] = (near?.takeIf { it in least..most } ?: at).coerceIn(least, most)
        }
        return starts[p] until starts[p + 1]
    }
}
