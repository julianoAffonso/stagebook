package br.app.stagebook.domain

import java.time.Instant
import java.time.ZoneId

/**
 * The day a show's marks and break counters belong to. A show night turns into a new day at 5 in the morning, not at
 * midnight (user, 14/11): a show from 22:00 to 02:00 is one day, so playing past midnight loses nothing.
 */
object ShowDay {
    const val ROLLOVER_HOUR = 5L

    /** The show day of [instant] on the wall clock of [zone] (passed in, so a time zone or DST change is a test). */
    fun of(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).toLocalDateTime().minusHours(ROLLOVER_HOUR).toLocalDate().toString()
}
