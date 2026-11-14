package br.app.stagebook.domain

import java.text.Normalizer
import java.util.Locale

/** The A to Z groups of the song list: a letter header before each group and an index of the letters at the edge. */
object SongGroups {
    /** Titles that don't start with a letter (digits, symbols, no title). It comes before the letters. */
    const val OTHER = "#"

    /** A short list is read at a glance: under these it stays a plain list, with no headers and no index. */
    const val MIN_SONGS = 20
    const val MIN_GROUPS = 3

    /** A group's letter and where its first row is in the list. */
    data class Group(val letter: String, val first: Int)

    /** The rows in the order they are shown and their groups (none: a plain list). */
    data class Grouped<T>(val rows: List<T>, val groups: List<Group>)

    /** The group of a title: its first letter, upper case and without the accent ("Élan" is in E). */
    fun letter(title: String): String {
        val first = title.trim().firstOrNull() ?: return OTHER
        val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first()
        if (!plain.isLetter()) return OTHER
        return plain.toString().uppercase(Locale.ROOT).take(1)
    }

    /**
     * Groups [rows], already in title order, by [letter]. Rows of a group stay together and in the order they came,
     * with [OTHER] first: a title that starts with a symbol doesn't sort where its group is.
     */
    fun <T> group(rows: List<T>, letter: (T) -> String): Grouped<T> {
        if (rows.size < MIN_SONGS) return Grouped(rows, emptyList())
        val letters = rows.map(letter)
        if (letters.distinct().size < MIN_GROUPS) return Grouped(rows, emptyList())
        val order = rows.indices.sortedWith(compareBy({ letters[it] != OTHER }, { letters[it] }))
        val groups = ArrayList<Group>()
        order.forEachIndexed { at, i -> if (at == 0 || letters[i] != letters[order[at - 1]]) groups += Group(letters[i], at) }
        return Grouped(order.map(rows::get), groups)
    }
}
