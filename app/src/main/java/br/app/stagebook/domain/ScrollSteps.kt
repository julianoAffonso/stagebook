package br.app.stagebook.domain

/**
 * Stage scroll mode: each pedal press scrolls to the first line that didn't fit whole (no line is cut between one step
 * and the next). [Step.markY]/[Step.markHeight]: where, on screen, the line where the new part starts sits, to light it
 * for a moment so the eye finds where to continue. The last step stops at the end of the song.
 *
 * [anchor]: where auto-scroll paused. The following steps start from there (no drift: the next pedal press goes to the
 * first line that didn't fit from the real position, not from a step computed from the start).
 */
object ScrollSteps {
    data class Step(val offset: Int, val markY: Int, val markHeight: Int)

    fun steps(lines: List<Pagination.Line>, viewport: Int, anchor: Int = 0): List<Step> {
        val first = Step(0, 0, 0)
        if (lines.isEmpty() || viewport <= 0) return listOf(first)
        val tops = IntArray(lines.size)
        var y = 0
        lines.forEachIndexed { i, l -> tops[i] = y; y += l.height }
        val max = (y - viewport).coerceAtLeast(0)
        val stop = anchor.coerceIn(0, max)
        val out = mutableListOf(first)
        var pos = 0
        var anchored = stop == 0
        while (pos < max) {
            var i = lines.indices.firstOrNull { tops[it] + lines[it].height > pos + viewport } ?: break
            // the highlight goes on the first line with text (skips the blank lines between verses)
            while (i < lines.lastIndex && lines[i].blank) i++
            var next = tops[i]
            if (next <= pos) next = pos + viewport   // a line taller than the screen: scrolls the whole screen
            next = next.coerceAtMost(max)
            if (next <= pos) break
            // past the pause point: it comes in as a step (no highlight: the lyrics don't move) and the count goes on
            // from there
            if (!anchored && next >= stop) {
                anchored = true
                if (next != stop) { out += Step(stop, 0, 0); pos = stop; continue }
            }
            out += Step(next, tops[i] - next, lines[i].height)
            pos = next
        }
        return out
    }
}
