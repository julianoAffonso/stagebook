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

    /** The start a running counter would have to show [timer]'s time: what the band's strip counts from. */
    fun since(timer: Timer, now: Long): Long = now - timer.elapsed(now)
}
