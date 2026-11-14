package br.app.stagebook.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The counter of a setlist's break, by the wall clock: when it started running plus what it had counted before it was
 * paused. Nothing ticks while the screen is away; the time shown is worked out from the clock when it is drawn, so
 * leaving the stage and coming back loses nothing.
 */
object BreakTimer {
    /**
     * One break's counter on [day]: [startedAt] (0 = paused) and [accumulated] before that. [open]: the break is on
     * screen (set when it opens, cleared when it is left), so a stage that opens again in the middle of it shows it.
     */
    @Immutable
    @Serializable
    data class Timer(val day: String = "", val startedAt: Long = 0L, val accumulated: Long = 0L, val open: Boolean = false) {
        val running: Boolean get() = startedAt > 0L

        fun elapsed(now: Long): Long = accumulated + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
    }

    /** A break key: the setlist and the song that comes after it (positions move, the song stays). */
    fun key(setId: String, songId: String) = "$setId|$songId"

    fun started(day: String, now: Long) = Timer(day, now, 0L, open = true)

    fun pause(timer: Timer, now: Long): Timer = if (timer.running) timer.copy(startedAt = 0L, accumulated = timer.elapsed(now)) else timer

    fun resume(timer: Timer, now: Long): Timer = if (timer.running) timer else timer.copy(startedAt = now)

    /** Back to 00:00, still running if it was running, still paused if it was paused. */
    fun reset(timer: Timer, now: Long): Timer = if (timer.running) timer.copy(startedAt = now, accumulated = 0L) else timer.copy(accumulated = 0L)

    /**
     * The setlist's breaks that were on screen when its stage was left and still are today, by position (the songs
     * before it): what the setlist's rows show, with the counter running or paused.
     */
    fun openAt(setId: String, items: List<String>, breaks: List<Int>, all: Map<String, Timer>, day: String): Map<Int, Timer> =
        breaks.mapNotNull { p -> items.getOrNull(p)?.let { all[key(setId, it)] }?.takeIf { it.open && it.day == day }?.let { p to it } }.toMap()

    /** The leader's counter as a follower draws it: standing still at [frozen] when paused, else running since [since]. */
    fun mirrored(since: Long, frozen: Long?): Timer = if (frozen != null) Timer(accumulated = frozen) else Timer(startedAt = since)

    /**
     * The leader's break on this device's copy of the setlist, by position (the songs before it): the break after the
     * song the leader is on ([msg]'s current one), or failing that the one before the song that follows it. What the
     * setlist's rows show while following, so the time is the leader's ([timer]) and ends when the leader's does. [items]
     * are this setlist's songs (title and artist, null = not on this device).
     */
    fun followedAt(items: List<Pair<String, String>?>, breaks: List<Int>, msg: Conduct.Msg, timer: Timer): Map<Int, Timer> {
        fun at(item: Conduct.Item?, shift: Int): Int? = item?.let { it ->
            breaks.firstOrNull { p -> p >= 1 && p < items.size && items[p + shift]?.let { (title, artist) -> Conduct.isFor(it, title, artist) } == true }
        }
        val p = at(msg.songs.getOrNull(msg.index), -1) ?: at(msg.songs.getOrNull(msg.index + 1), 0) ?: return emptyMap()
        return mapOf(p to timer)
    }

    /** The start a running counter would have to show [timer]'s time: what the band's strip counts from. */
    fun since(timer: Timer, now: Long): Long = now - timer.elapsed(now)
}
