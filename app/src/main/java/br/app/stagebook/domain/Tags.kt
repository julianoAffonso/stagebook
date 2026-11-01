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

    /** The chosen filter tags still in use ([all]): a tag no song has any more (taken off everywhere) leaves the filter. */
    fun stillUsed(chosen: Set<String>, all: List<String>): Set<String> = chosen.filter { c -> all.any { it.equals(c, true) } }.toSet()

    fun has(song: Song, tag: String): Boolean = song.tags.any { it.equals(tag, ignoreCase = true) }

    fun add(tags: List<String>, tag: String): List<String> = if (tags.any { it.equals(tag, true) }) tags else tags + tag

    fun remove(tags: List<String>, tag: String): List<String> = tags.filterNot { it.equals(tag, true) }

    /** The tags window's answer applied to one song's tags: [remove] taken off, then [add] (cleaned) put on. */
    fun apply(tags: List<String>, add: List<String>, remove: List<String>): List<String> {
        var t = tags
        remove.forEach { t = remove(t, it) }
        add.forEach { t = add(t, clean(it)) }
        return t
    }

    /** Songs with every chosen tag; none chosen = all. */
    fun <T> filter(items: List<T>, chosen: Set<String>, tagsOf: (T) -> List<String>): List<T> {
        if (chosen.isEmpty()) return items
        val keys = chosen.map { it.lowercase() }.toSet()
        return items.filter { item -> tagsOf(item).map { it.lowercase() }.toSet().containsAll(keys) }
    }
}

/**
 * The language of a song's lyrics, by the common words of each known language (chords and section titles out). Null
 * when there are too few words to tell (no lyrics yet) or no known language wins. "National" is the app's language
 * (user, 01/11: the app isn't only for Brazil): a new translation brings its own word list here.
 */
object LyricsLanguage {
    private val words = mapOf(
        "pt" to setOf(
            "que", "não", "nao", "você", "voce", "eu", "meu", "minha", "do", "da", "é", "os", "um", "uma", "pra", "para",
            "com", "sem", "mais", "quando", "como", "tudo", "nada", "amor", "coração", "vida", "seu", "sua", "tão", "já",
            "só", "vou", "quero", "nós", "agora", "sempre", "nunca", "dia", "noite", "também", "aqui", "assim", "ela", "ele",
        ),
        "en" to setOf(
            "the", "you", "and", "my", "love", "it", "is", "in", "of", "your", "that", "be", "we", "don't", "i'm", "baby",
            "all", "for", "on", "with", "what", "can", "just", "know", "want", "never", "this", "are", "when", "now",
            "got", "like", "there", "it's", "can't", "she", "he", "they", "night", "time", "way", "feel", "heart", "i",
        ),
    )

    /** Languages it can tell (ISO 639-1). */
    val known: Set<String> get() = words.keys

    fun detect(text: String): String? {
        val lyrics = Chords.stripChords(text).lines().filter { Sections.detect(it) == null && !it.trimStart().startsWith("{") }
        val counts = words.keys.associateWith { 0 }.toMutableMap()
        lyrics.forEach { line ->
            // the typographic apostrophe of most sites ("don’t", "I’m") counts as the plain one, as in LyricsCleaner
            // (audit 01/11)
            TextPatterns.words(line).forEach { w ->
                words.forEach { (lang, set) -> if (w in set) counts[lang] = counts.getValue(lang) + 1 }
            }
        }
        if (counts.values.sum() < MIN_WORDS) return null
        val best = counts.maxByOrNull { it.value } ?: return null
        // a tie isn't an answer
        return best.key.takeIf { counts.values.count { it == best.value } == 1 }
    }

    private const val MIN_WORDS = 5
}
