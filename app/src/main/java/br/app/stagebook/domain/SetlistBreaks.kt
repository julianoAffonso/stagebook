package br.app.stagebook.domain

/**
 * Breaks inside a setlist. A break is a position `p` with `1 <= p <= size - 1`: "a break before the song at p". The
 * songs stay a plain list (an older app version reads it and ignores the breaks); the list of breaks is sorted, with
 * no repeats, and never at the start or the end of the setlist.
 *
 * The screen shows songs and breaks as one list of rows: a song is its position in the setlist, a break is [BREAK].
 */
object SetlistBreaks {
    const val BREAK = -1

    /** A setlist's songs after a row moved ([order]: the old position of each song, in the new order) and its breaks. */
    data class Moved(val order: List<Int>, val breaks: List<Int>)

    /** The valid breaks for a setlist of [size] songs: in range, sorted, one per place. */
    fun normalise(breaks: List<Int>, size: Int): List<Int> {
        if (breaks.isEmpty()) return breaks
        val valid = breaks.filter { it in 1 until size }.distinct().sorted()
        return if (valid == breaks) breaks else valid
    }

    /** Songs and breaks in the order they show: each song's position, with [BREAK] where a break is. */
    fun rows(size: Int, breaks: List<Int>): List<Int> {
        val at = normalise(breaks, size).toSet()
        return buildList(size + at.size) {
            for (i in 0 until size) {
                if (i in at) add(BREAK)
                add(i)
            }
        }
    }

    /**
     * Rows back to songs and breaks. A break left at the very start goes after the first song and one at the very end
     * before the last; two together become one.
     */
    fun split(rows: List<Int>): Moved {
        val order = rows.filter { it != BREAK }
        val breaks = ArrayList<Int>()
        var songs = 0
        for (row in rows) if (row == BREAK) breaks += songs.coerceIn(1, (order.size - 1).coerceAtLeast(1)) else songs++
        return Moved(order, normalise(breaks, order.size))
    }

    /** The row at [from] (a song or a break) dropped at [to]; out of range changes nothing. */
    fun moveRow(size: Int, breaks: List<Int>, from: Int, to: Int): Moved {
        val rows = rows(size, breaks)
        if (from !in rows.indices || to !in rows.indices) return Moved(List(size) { it }, normalise(breaks, size))
        return split(rows.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** The song at [from] takes the place of the song at [to]; the breaks stay between the songs they were between. */
    fun moveSong(size: Int, breaks: List<Int>, from: Int, to: Int): Moved {
        val rows = rows(size, breaks)
        if (from !in 0 until size || to !in 0 until size) return Moved(List(size) { it }, normalise(breaks, size))
        return moveRow(size, breaks, rows.indexOf(from), rows.indexOf(to))
    }

    /** A new break, before the last song (the person drags it into place); none in a setlist with fewer than 2 songs. */
    fun add(breaks: List<Int>, size: Int): List<Int> {
        val now = normalise(breaks, size)
        // that place taken: the nearest free one before it
        val free = (size - 1 downTo 1).firstOrNull { it !in now } ?: return now
        return (now + free).sorted()
    }

    fun canAdd(breaks: List<Int>, size: Int): Boolean = add(breaks, size).size > normalise(breaks, size).size

    /** Without the break before the song at [position]. */
    fun remove(breaks: List<Int>, position: Int): List<Int> = breaks.filter { it != position }

    /**
     * The breaks after the songs at [removed] left a setlist of [size] songs: each stays before the same song, or before
     * the next one that stayed. One left at the start or the end goes away.
     */
    fun afterRemoving(breaks: List<Int>, size: Int, removed: Set<Int>): List<Int> {
        if (breaks.isEmpty()) return breaks
        val left = size - removed.count { it in 0 until size }
        return normalise(breaks.filter { it in 1 until size }.map { p -> p - removed.count { it in 0 until p } }, left)
    }

    /**
     * The breaks after the setlist's songs were replaced ([old] → [new], song ids): each stays before the same song
     * when it's still there, or before the next one that is.
     */
    fun afterReplacing(breaks: List<Int>, old: List<String>, new: List<String>): List<Int> {
        if (breaks.isEmpty()) return breaks
        val place = HashMap<String, Int>()
        new.forEachIndexed { i, id -> place.putIfAbsent(id, i) }
        val moved = normalise(breaks, old.size).mapNotNull { p -> (p until old.size).firstNotNullOfOrNull { place[old[it]] } }
        return normalise(moved, new.size)
    }

    /** The break's counter on stage, minutes and seconds since it started: "00:07", "12:30", "75:03". */
    fun clock(elapsedMs: Long): String {
        val seconds = elapsedMs.coerceAtLeast(0L) / 1_000L
        return (seconds / 60).toString().padStart(2, '0') + ":" + (seconds % 60).toString().padStart(2, '0')
    }

    /** How many songs each block has (the songs between two breaks): one block when there's no break. */
    fun blocks(size: Int, breaks: List<Int>): List<Int> {
        val edges = listOf(0) + normalise(breaks, size) + size
        return edges.zipWithNext { a, b -> b - a }
    }
}
