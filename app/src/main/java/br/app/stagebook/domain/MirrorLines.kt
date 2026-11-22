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
}
