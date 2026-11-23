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
        val exactTitle = TextKey.of(title)
        val ownVersion = versionWords(title)
        return results
            .filter { r ->
                // with no artist to tell recordings apart, only the very same title counts
                if (artist.isBlank()) TextKey.of(r.title) == exactTitle
                else TextKey.of(baseTitle(r.title)) == want && sameArtist(r.artist, artist)
            }
            .minByOrNull { r ->
                val exact = TextKey.of(r.title) == exactTitle
                val theirs = versionWords(r.title)
                // a version the song itself isn't (live, remix…) weighs most; one the song is and the result isn't, less
                (if (exact) 0 else 1) + (theirs - ownVersion).size * 10 + (ownVersion - theirs).size * 5
            }?.id
    }

    private val bracketed = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val afterDash = Regex("""\s+[-–—]\s+.*$""")
    private val trailingVersion = Regex("""\s+(ao vivo|live|ac[uú]stico|acoustic)\s*$""", RegexOption.IGNORE_CASE)
    // "(Part 2)", "(Pts. 1-5)", "(Parte 1)": another piece of music, not another version of the same one
    private val part = Regex("""^\s*(part|parte|pt|pts)\b\.?\s*[\divx]""", RegexOption.IGNORE_CASE)

    /** The title without the version the service adds: "(Remastered 2009)", " - Live", a trailing "Ao Vivo". A part number stays. */
    fun baseTitle(t: String): String {
        var s = t.replace('’', '\'').replace('‘', '\'')
        s = bracketed.replace(s) { m -> if (part.containsMatchIn(m.groupValues[1])) m.value else " " }
        s = afterDash.replace(s, "")
        s = trailingVersion.replace(s, "")
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

    /**
     * The same artist (audit 22/11: comparing pieces by "contains" took "Henrique & Juliano" for "Henrique & Diego" and
     * "Sia" for "Asia"). Whole names first, equal or with a small typo, without a leading article. Only then as duos:
     * every name of the smaller side has to be one of the other side's ("Vitor Kley" in "Vitor Kley e Samuel Rosa").
     */
    fun sameArtist(found: String, wanted: String): Boolean {
        val a = whole(found)
        val b = whole(wanted)
        if (a.isEmpty() || b.isEmpty()) return false
        if (same(a, b)) return true
        val x = names(found)
        val y = names(wanted)
        if (x.size < 2 && y.size < 2) return false
        val (few, many) = if (x.size <= y.size) x to y else y to x
        return few.isNotEmpty() && few.all { n -> many.any { same(n, it) } }
    }

    private fun same(a: String, b: String) = a == b || close(a, b)

    private val article = Regex("""^(the|os|as|o|a|los|las)\s+""")
    private fun whole(s: String): String = TextKey.of(s).replace(article, "")

    private val joiner = Regex("""\s+(e|and|&|feat\.?|ft\.?|x|com|with)\s+|,|/""", RegexOption.IGNORE_CASE)

    // the names of a duo; a "name" of one or two letters ("Tones and I", "AC/DC") means it wasn't a list of names
    private fun names(s: String): List<String> {
        val parts = s.split(joiner).map { whole(it) }.filter { it.isNotEmpty() }
        return if (parts.any { it.length < MIN_NAME }) listOf(whole(s)) else parts
    }

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

    private const val MIN_NAME = 3
    private const val MIN_TYPO_NAME = 6
    private const val LONG_NAME = 10
}
