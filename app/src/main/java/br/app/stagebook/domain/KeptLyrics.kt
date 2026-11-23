package br.app.stagebook.domain

/**
 * A PDF song keeps the lyrics and chords it had (they come back when the PDF goes). The editor offers to show them on
 * the stage in place of the PDF only when there is something to show (user, 22/11): a line that isn't a directive nor a
 * section title, words or chords.
 */
object KeptLyrics {
    fun has(text: String): Boolean = text.lineSequence().map { it.trim() }.any { l ->
        l.isNotEmpty() && !(l.startsWith("{") && l.endsWith("}")) && Sections.detect(l) == null
    }
}
