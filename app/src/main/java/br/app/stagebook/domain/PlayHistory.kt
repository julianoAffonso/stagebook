package br.app.stagebook.domain

import br.app.stagebook.data.model.PlayEntry
import java.time.LocalDate

/**
 * The history of what was played (user, 22/11): kept one line per song per show day, and exported as a spreadsheet
 * (CSV) for a period, e.g. to report the songs played for royalties.
 */
object PlayHistory {
    /** At most this many lines: the oldest go first (years of shows; a line is a few dozen bytes). */
    const val MAX = 20_000

    fun add(history: List<PlayEntry>, entry: PlayEntry): List<PlayEntry> =
        (history + entry).let { if (it.size > MAX) it.takeLast(MAX) else it }

    /** The periods offered for the export. */
    enum class Period { MONTH, YEAR, ALL }

    /** The lines of [period] counted back from [today], oldest first. */
    fun of(history: List<PlayEntry>, period: Period, today: LocalDate): List<PlayEntry> {
        val from = when (period) {
            Period.MONTH -> today.withDayOfMonth(1).toString()
            Period.YEAR -> today.minusMonths(12).toString()
            Period.ALL -> ""
        }
        return history.filter { it.day >= from }.sortedBy { it.day }
    }

    /** [lines] as the report shows them: each show day with its songs in the order played, the latest day first. */
    fun byDay(lines: List<PlayEntry>): List<Pair<String, List<PlayEntry>>> =
        lines.groupBy { it.day }.toList().sortedByDescending { it.first }

    /** How many songs were played and on how many show days. */
    fun summary(history: List<PlayEntry>): Pair<Int, Int> = history.size to history.map { it.day }.distinct().size

    /** How the report is read: every song of every show day, or each song once with how many times it was played. */
    enum class View { DAYS, SONGS }

    /** A song of the period and how many times it was played (user, 23/11: what a royalties report asks for). */
    data class SongCount(val title: String, val author: String, val times: Int)

    /**
     * Each song of [lines] once, the most played first and then by name. The same song written with other capitals or
     * accents on another day is one song; the name shown is the latest one.
     */
    fun bySong(lines: List<PlayEntry>): List<SongCount> =
        lines.groupBy { TextKey.of(it.title) to TextKey.of(it.author) }.entries
            .map { (key, plays) -> key.first to SongCount(plays.last().title, plays.last().author, plays.size) }
            .sortedWith(compareByDescending<Pair<String, SongCount>> { it.second.times }.thenBy { it.first })
            .map { it.second }

    /** The names of the spreadsheet's columns, in the app's language. */
    data class Columns(val date: String, val song: String, val artist: String, val setlist: String, val times: String)

    /** The spreadsheet of [period] as [view] shows it, and how many rows it has. */
    fun sheet(history: List<PlayEntry>, period: Period, view: View, today: LocalDate, columns: Columns, separator: Char): Pair<String, Int> {
        val lines = of(history, period, today)
        return when (view) {
            View.DAYS -> csv(lines, listOf(columns.date, columns.song, columns.artist, columns.setlist), separator) to lines.size
            View.SONGS -> bySong(lines).let { counts ->
                table(listOf(listOf(columns.song, columns.artist, columns.times)) + counts.map { listOf(it.title, it.author, it.times.toString()) }, separator) to counts.size
            }
        }
    }

    /**
     * The spreadsheet: a header in the app's language, then date, song, artist and setlist. [separator] ';' where a
     * comma is the decimal mark (a Brazilian spreadsheet opens it in columns); a BOM so accents read right.
     */
    fun csv(lines: List<PlayEntry>, header: List<String>, separator: Char): String =
        table(listOf(header) + lines.map { listOf(it.day, it.title, it.author, it.setlist) }, separator)

    private fun table(rows: List<List<String>>, separator: Char): String {
        fun cell(v: String) = if (v.any { it == separator || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        return "\uFEFF" + rows.joinToString("\r\n") { r -> r.joinToString(separator.toString(), transform = ::cell) } + "\r\n"
    }
}
