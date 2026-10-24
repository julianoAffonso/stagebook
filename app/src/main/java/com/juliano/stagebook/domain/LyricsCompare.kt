package com.juliano.stagebook.domain

import com.juliano.stagebook.data.model.SectionLang

/**
 * Compara duas versões da mesma letra pelo conteúdo, para a sincronia decidir sem depender de marca:
 * formatação (espaços, linhas vazias, "[Chorus]" x "[Refrão]") não conta como diferença; as seções
 * e as palavras são comparadas separadas.
 */
object LyricsCompare {
    enum class Diff { SAME, FORMAT, SECTIONS, WORDS }

    /** Letra com seções em "[Refrão]", linhas aparadas e linhas vazias seguidas juntadas. */
    fun canonical(text: String): String =
        Sections.normalize(text.replace("\r", ""), SectionLang.PT).lines()
            .map { it.trim() }
            .fold(mutableListOf<String>()) { out, l -> if (l.isNotEmpty() || out.lastOrNull()?.isNotEmpty() == true) out += l; out }
            .joinToString("\n").trim()

    /** Só as linhas cantadas (sem títulos de seção nem linhas vazias). */
    fun words(text: String): List<String> =
        Sections.parse(text.replace("\r", "")).filter { it.header == null }.map { it.text.trim() }.filter { it.isNotEmpty() }

    fun hasSections(text: String): Boolean = Sections.parse(text).any { it.header != null }

    fun diff(a: String, b: String): Diff = when {
        a == b -> Diff.SAME
        canonical(a) == canonical(b) -> Diff.FORMAT
        words(a) == words(b) -> Diff.SECTIONS
        else -> Diff.WORDS
    }
}
