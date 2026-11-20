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

    /** The song played on [day]: one more time, unless it was already counted that day (null: nothing to change). */
    fun record(song: Song, day: String): Song? = if (song.lastPlayed == day) null else song.copy(plays = song.plays + 1, lastPlayed = day)

    private fun daysSince(last: String, today: String): Long? = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(today)) }.getOrNull()

    fun shows(song: Song, show: PlayShow, today: String): Boolean = when (show) {
        PlayShow.ALL -> true
        PlayShow.NEVER -> song.plays == 0
        PlayShow.RECENT -> song.plays > 0 && (daysSince(song.lastPlayed, today) ?: Long.MAX_VALUE) in 0..RECENT_DAYS
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
        val days = daysSince(last, today)?.coerceAtLeast(0) ?: return null
        return when {
            days == 0L -> PlayAgo(PlayAgo.Unit.TODAY, 0)
            days == 1L -> PlayAgo(PlayAgo.Unit.YESTERDAY, 1)
            days < 14 -> PlayAgo(PlayAgo.Unit.DAYS, days.toInt())
            days < 60 -> PlayAgo(PlayAgo.Unit.WEEKS, (days / 7).toInt())
            else -> PlayAgo(PlayAgo.Unit.MONTHS, (days / 30).toInt())
        }
    }
}
