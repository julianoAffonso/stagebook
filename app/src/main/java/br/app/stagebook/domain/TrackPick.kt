package br.app.stagebook.domain

/**
 * Picks, among a music service's search results, the track that is the song (user, 22/11: songs stayed without data
 * because the service writes "All My Loving (Remastered 2009)", "Acima do Sol Ao Vivo", "Morrissey" where the library
 * has "All My Loving", "Acima do Sol", "Morrisey"). The title is compared without the version the service adds; the
 * studio version wins over live, remix, cover and karaoke ones, unless the song itself is one of those; the artist
 * matches one of the names in a duo ("A e B", "A & B", "A feat. B") or with a small typo.
 */
object TrackPick {
    data class Result(val id: Long, val title: String, val artist: String)

    /** The result that is the song, or null when none is (better nothing than another song's data). */
    fun best(results: List<Result>, title: String, artist: String): Long? {
        val want = TextKey.of(baseTitle(title))
        val ownVersion = versionWords(title)
        return results
            .filter { TextKey.of(baseTitle(it.title)) == want && (artist.isBlank() || sameArtist(it.artist, artist)) }
            .minByOrNull { r ->
                val exact = TextKey.of(r.title) == TextKey.of(title)
                val theirs = versionWords(r.title)
                // a version the song itself isn't (live, remix…) weighs most; one the song is and the result isn't, less
                (if (exact) 0 else 1) + (theirs - ownVersion).size * 10 + (ownVersion - theirs).size * 5
            }?.id
    }

    /** The title without the version the service adds: "(Remastered 2009)", " - Live", a trailing "Ao Vivo". */
    fun baseTitle(t: String): String {
        var s = t.replace('’', '\'').replace('‘', '\'')
        s = s.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), " ")
        s = s.replace(Regex("""\s+[-–—]\s+.*$"""), "")
        s = s.replace(Regex("""\s+(ao vivo|live|ac[uú]stico|acoustic)\s*$""", RegexOption.IGNORE_CASE), "")
        return s.trim()
    }

    // the kinds of version that are another recording than the studio one, each with the words that say it
    private val versions = mapOf(
        "live" to listOf("ao vivo", "live"), "remix" to listOf("remix", "mix"), "cover" to listOf("cover"),
        "karaoke" to listOf("karaoke"), "instrumental" to listOf("instrumental"), "acoustic" to listOf("acustico", "acoustic"),
        "demo" to listOf("demo"),
    )

    private fun versionWords(t: String): Set<String> {
        val k = " " + TextKey.of(t) + " "
        return versions.filterValues { words -> words.any { " ${TextKey.of(it)} " in k } }.keys
    }

    /** The same artist: one of the names of a duo, or the same name with a typo of up to two letters. */
    fun sameArtist(found: String, wanted: String): Boolean {
        val a = names(found)
        val b = names(wanted)
        return a.any { x -> b.any { y -> SongMatch.sameArtistKey(x, y) || close(x, y) } }
    }

    private fun names(s: String): List<String> =
        s.split(Regex("""\s+(e|and|&|feat\.?|ft\.?|x|com|with)\s+|,|/""", RegexOption.IGNORE_CASE))
            .map { TextKey.of(it) }.filter { it.isNotBlank() }

    // a typo, not another artist: only in names long enough that two letters are a small part of them
    private fun close(a: String, b: String): Boolean {
        val shorter = minOf(a.length, b.length)
        if (shorter < MIN_TYPO_NAME) return false
        return distance(a, b) <= if (shorter >= LONG_NAME) 2 else 1
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private const val MIN_TYPO_NAME = 6
    private const val LONG_NAME = 10
}
