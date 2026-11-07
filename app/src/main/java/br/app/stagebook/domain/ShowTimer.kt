package br.app.stagebook.domain

import br.app.stagebook.data.model.StageClockMode

/**
 * The stage clock's other faces (user, 07/11): how long the show has run and how much of it is left, in hours and
 * minutes, so the musician knows when to stop. The show's length goes in half hours.
 */
object ShowTimer {
    const val STEP_MINUTES = 30
    const val MIN_MINUTES = 30
    const val MAX_MINUTES = 6 * 60
    const val DEFAULT_MINUTES = 60   // starts at an hour (user, 07/11)
    const val WARN_MINUTES = 10   // the last minutes change color
    // a start older than this is yesterday's show: switching to the counter starts a new one
    private const val STALE_MS = 12 * 60 * 60 * 1000L

    /** Tapping the clock: clock → show time → time left → clock. */
    fun next(mode: StageClockMode): StageClockMode = StageClockMode.entries[(mode.ordinal + 1) % StageClockMode.entries.size]

    /** The show's start when the counter shows up: the one running, or now for a new show. */
    fun startFor(start: Long, now: Long): Long = if (start <= 0L || start > now || now - start > STALE_MS) now else start

    /** The show's length one step up or down, within its limits. */
    fun step(minutes: Int, up: Boolean): Int = (minutes + if (up) STEP_MINUTES else -STEP_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun elapsedMinutes(start: Long, now: Long): Long = ((now - start).coerceAtLeast(0L)) / 60_000L

    /** Minutes left; below zero once the show ran past its length. */
    fun leftMinutes(start: Long, now: Long, length: Int): Long = length - elapsedMinutes(start, now)

    /** "1:05"; past the end, "−0:05". */
    fun format(minutes: Long): String {
        val m = kotlin.math.abs(minutes)
        return (if (minutes < 0) "−" else "") + "${m / 60}:" + (m % 60).toString().padStart(2, '0')
    }

    /** The length as the window shows it: "2h", "2h30". */
    fun lengthText(minutes: Int): String = "${minutes / 60}h" + if (minutes % 60 != 0) (minutes % 60).toString().padStart(2, '0') else ""

    fun warn(start: Long, now: Long, length: Int): Boolean = leftMinutes(start, now, length) < WARN_MINUTES

    /** Milliseconds to the next minute of the counter (its minutes count from the start, not from the hour). */
    fun untilNextMinute(start: Long, now: Long): Long = 60_000L - ((now - start).coerceAtLeast(0L) % 60_000L)
}
