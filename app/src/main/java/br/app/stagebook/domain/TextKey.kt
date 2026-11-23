package br.app.stagebook.domain

import java.text.Normalizer
import java.util.Locale

/** Title comparison key: no accents, lowercase, only letters and digits (it's the key between tablets). */
object TextKey {
    private val marks = Regex("\\p{M}")
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val apostrophes = Regex("['’´`]")   // "it's" = "its", "don´t" = "dont"

    /** A to Z by [name], ignoring case and accents: the one way the app sorts by name. */
    // each item's key made once: sortedBy asks for it at every comparison, and the key costs a normalisation
    // (2,000 songs = some 40,000 of them per sort; audit 22/11)
    fun <T> sorted(items: List<T>, name: (T) -> String): List<T> = items.map { of(name(it).trim()) to it }.sortedBy { it.first }.map { it.second }

    fun of(s: String?): String =
        Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(apostrophes, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlnum, " ")
            .trim()
}
