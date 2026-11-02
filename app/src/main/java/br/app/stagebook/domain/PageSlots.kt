package br.app.stagebook.domain

/**
 * Which page dots the stage footer shows, like Google's pagination: when they fit, all; when not, the first, the last,
 * the current with its neighbors and "…" (null) in place of the ones left out (user's request, 23/10: with many pages
 * the phone hid the clock and cut the last ones; the stage uses the same number on phones and tablets).
 */
object PageSlots {
    /** [current] and the result are 0-based indexes; [capacity] = how many dots (or "…") fit. */
    fun of(current: Int, count: Int, capacity: Int): List<Int?> {
        if (count <= capacity) return (0 until count).toList()
        // room for first, "…", neighbors, "…", last: with less than that, only the current one
        if (capacity < 5) return listOf(current)
        val window = capacity - 4
        return when {
            // near the start: a solid block from the 1st, "…" and the last
            current <= capacity - 4 -> (0..capacity - 3).toList() + null + (count - 1)
            // near the end: the 1st, "…" and a solid block to the last
            current >= count - (capacity - 3) -> listOf(0, null) + (count - (capacity - 2) until count)
            else -> {
                val start = current - (window - 1) / 2
                listOf(0, null) + (start until start + window) + null + (count - 1)
            }
        }
    }
}
