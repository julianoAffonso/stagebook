package br.app.stagebook.domain

import br.app.stagebook.data.model.Song
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the Songs list shows by how often a song was played (user, 19/11). */
enum class PlayShow { ALL, RECENT, NEVER }

/** The Songs list's order: A to Z (with the letters), or by how often a song was played. */
enum class SongOrder { AZ, MOST, LEAST }

/** How long ago a song was last played, for its row: [unit] and how many of it. */
data class PlayAgo(val unit: Unit, val count: Int) {
    enum class Unit { TODAY, YESTERDAY, DAYS, WEEKS, MONTHS }
}

/**
 * The count of times each song was played: one a show day (the night turns at 5 am, [ShowDay]) when it stays a minute
 * on stage, from a setlist or alone. It lives on the song itself, so a deleted setlist or cleared marks keep it.
 */
object PlayStats {
    /** "Played in the last 30 days" counts a song last played up to this many days before today. */
    const val RECENT_DAYS = 30L

    /**
     * The orders that make sense with [show]: never played songs all have zero plays, so only A to Z sorts them
     * (user, 19/11: "Never played" with "Most played" was a choice that meant nothing).
     */
    fun ordersFor(show: PlayShow): List<SongOrder> = if (show == PlayShow.NEVER) listOf(SongOrder.AZ) else SongOrder.entries

    /** [order] kept when it fits [show], else A to Z. */
    fun orderFor(show: PlayShow, order: SongOrder): SongOrder = if (order in ordersFor(show)) order else SongOrder.AZ

    /** The song played on [day]: one more time, unless it was already counted that day (null: nothing to change). */
    fun record(song: Song, day: String): Song? = if (song.lastPlayed == day) null else song.copy(plays = song.plays + 1, lastPlayed = day)

    /**
     * Whole days from [last] to [today] (null: a date that can't be read). A last play after today (the clock moved back,
     * or another time zone) counts as today, everywhere: the list, its filter and the editor on the computer agree.
     */
    fun daysAgo(last: String, today: String): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(today)).coerceAtLeast(0) }.getOrNull()

    fun shows(song: Song, show: PlayShow, today: String): Boolean = when (show) {
        PlayShow.ALL -> true
        PlayShow.NEVER -> song.plays == 0
        PlayShow.RECENT -> song.plays > 0 && (daysAgo(song.lastPlayed, today) ?: Long.MAX_VALUE) <= RECENT_DAYS
    }

    /**
     * [songs] (already in title order) in [order]: most played first, or least (never played first); a tie goes to the
     * one played last (most) or longest ago (least), then stays in title order.
     */
    fun <T> sorted(songs: List<T>, order: SongOrder, song: (T) -> Song): List<T> = when (order) {
        SongOrder.AZ -> songs
        SongOrder.MOST -> songs.sortedWith(compareByDescending<T> { song(it).plays }.thenByDescending { song(it).lastPlayed })
        SongOrder.LEAST -> songs.sortedWith(compareBy<T> { song(it).plays }.thenBy { song(it).lastPlayed })
    }

    /** How long ago [last] was, from [today] (null: never played, or a date that can't be read). */
    fun ago(last: String, today: String): PlayAgo? {
        if (last.isEmpty()) return null
        val days = daysAgo(last, today) ?: return null
        return when {
            days == 0L -> PlayAgo(PlayAgo.Unit.TODAY, 0)
            days == 1L -> PlayAgo(PlayAgo.Unit.YESTERDAY, 1)
            days < 14 -> PlayAgo(PlayAgo.Unit.DAYS, days.toInt())
            days < 60 -> PlayAgo(PlayAgo.Unit.WEEKS, (days / 7).toInt())
            else -> PlayAgo(PlayAgo.Unit.MONTHS, (days / 30).toInt())
        }
    }
}
