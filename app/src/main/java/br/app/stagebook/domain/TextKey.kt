package br.app.stagebook.domain

import java.text.Normalizer
import java.util.Locale

/** Title comparison key: no accents, lowercase, only letters and digits (it's the key between tablets). */
object TextKey {
    private val marks = Regex("\\p{M}")
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val apostrophes = Regex("['’´`]")   // "it's" = "its", "don´t" = "dont"

    fun of(s: String?): String =
        Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(apostrophes, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlnum, " ")
            .trim()
}
