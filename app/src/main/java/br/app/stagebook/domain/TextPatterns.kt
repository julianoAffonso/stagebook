package br.app.stagebook.domain

/** Patterns and word splitting shared by the importers and the language checks (they were repeated; audit 01/11). */
internal object TextPatterns {
    val TRAILING_SPACE = Regex("\\s+$")
    val MANY_BLANK = Regex("\n{3,}")
    private val WORD_SPLIT = Regex("[^\\p{L}']+")

    /**
     * A line's words in lowercase, for telling the language: the typographic apostrophe of most sites ("don’t",
     * "I’m") counts as the plain one, and quotes around a word don't count.
     */
    fun words(line: String): Sequence<String> =
        line.lowercase().replace('\u2019', '\'').splitToSequence(WORD_SPLIT).map { it.trim('\'') }.filter { it.isNotEmpty() }
}
