package br.app.stagebook.domain

import java.text.Normalizer
import java.util.Locale

/** Chave de comparação de título: sem acento, minúscula, só letras e números (é a chave entre os tablets). */
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
