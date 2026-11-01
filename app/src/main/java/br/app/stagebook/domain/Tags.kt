package br.app.stagebook.domain

import br.app.stagebook.data.model.Song

/**
 * Song tags (vocalist, 01/11: separate Brazilian songs from international ones). Free names; the same tag in another
 * case is the same tag. Filtering by several tags shows the songs that have all of them (user, 01/11: "or" would
 * confuse).
 */
object Tags {
    fun clean(tag: String): String = tag.trim().replace(Regex("""\s+"""), " ")

    /** Every tag in use, each once, in alphabetical order. */
    fun all(songs: List<Song>): List<String> =
        songs.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { TextKey.of(it) }

    fun has(song: Song, tag: String): Boolean = song.tags.any { it.equals(tag, ignoreCase = true) }

    fun add(tags: List<String>, tag: String): List<String> = if (tags.any { it.equals(tag, true) }) tags else tags + tag

    fun remove(tags: List<String>, tag: String): List<String> = tags.filterNot { it.equals(tag, true) }

    /** Songs with every chosen tag; none chosen = all. */
    fun <T> filter(items: List<T>, chosen: Set<String>, tagsOf: (T) -> List<String>): List<T> {
        if (chosen.isEmpty()) return items
        val keys = chosen.map { it.lowercase() }.toSet()
        return items.filter { item -> tagsOf(item).map { it.lowercase() }.toSet().containsAll(keys) }
    }
}

/**
 * Is the song in Portuguese? Counts common words of each language in the lyrics (chords and section titles out).
 * Null when there are too few words to tell (no lyrics yet). Good enough to start; the tags can be fixed by hand.
 */
object LyricsLanguage {
    private val pt = setOf(
        "que", "não", "nao", "você", "voce", "eu", "meu", "minha", "do", "da", "é", "os", "um", "uma", "pra", "para",
        "com", "sem", "mais", "quando", "como", "tudo", "nada", "amor", "coração", "vida", "seu", "sua", "tão", "já",
        "só", "vou", "quero", "nós", "agora", "sempre", "nunca", "dia", "noite", "também", "aqui", "assim", "ela", "ele",
    )
    private val en = setOf(
        "the", "you", "and", "my", "love", "it", "is", "in", "of", "your", "that", "be", "we", "don't", "i'm", "baby",
        "all", "for", "on", "with", "what", "can", "just", "know", "want", "never", "this", "are", "when", "now",
        "got", "like", "there", "it's", "can't", "she", "he", "they", "night", "time", "way", "feel", "heart",
    )
    private val word = Regex("""[\p{L}']+""")

    fun isPortuguese(text: String): Boolean? {
        val lyrics = Chords.stripChords(text).lines().filter { Sections.detect(it) == null && !it.trimStart().startsWith("{") }
        var p = 0
        var e = 0
        lyrics.forEach { line ->
            word.findAll(line.lowercase()).forEach { m ->
                val w = m.value.trim('\'')
                if (w in pt) p++
                if (w in en || w == "i") e++
            }
        }
        if (p + e < MIN_WORDS) return null
        return p > e
    }

    private const val MIN_WORDS = 5
}
