package br.app.stagebook.domain

/** The stage opens on the song at [start]; [atBreak]: on the break that was left open after it. */
data class StageStart(val start: Int, val atBreak: Boolean)

/** Where tapping a setlist opens its stage: where today's show stopped. */
object SetlistResume {
    /**
     * The song on stage last today (the first when none, or when the marks are from another day), and the break left
     * open after it, if there is one: the stage comes back on it, with its counter.
     */
    fun at(setId: String, items: List<String>, breaks: List<Int>, marks: SetlistShow.Marks?, timers: Map<String, BreakTimer.Timer>, day: String): StageStart {
        val current = marks?.takeIf { it.day == day }?.current.orEmpty()
        val start = if (current.isEmpty()) 0 else items.indexOf(current).coerceAtLeast(0)
        val atBreak = (start + 1) in BreakTimer.openAt(setId, items, breaks, timers, day)
        return StageStart(start, atBreak)
    }
}
