package br.app.stagebook.domain

/**
 * What can pass between devices (band, 3 words): only setlists and songs in plain text (lyrics, chords, tablature).
 * Everything that arrives passes here before entering the inbox; what doesn't look like lyrics is silently discarded:
 * text too big, control characters, or long blocks with no spaces that look like an encoded file (base64 and the like:
 * the way to hide a photo or a program inside "text"). Tablature lines (`e|---0---3---|`) pass.
 */
object BandContent {
    const val MAX_TITLE = 200
    const val MAX_YEAR = 10
    const val MAX_TEXT = 60_000      // a long chord chart has about 10 thousand characters
    const val MAX_SONGS = 200
    const val MAX_LIST = 20_000      // the setlist text (name + titles)
    // each way of sending has its own limit. Internet: the free server (ntfy.sh) takes about 60
    // requests in a row, and each song and each list is one (plus the sender's card). Band on the network: one package
    // per setlist straight to the device, limited by its Received, which keeps 30. QR: one setlist (it isn't offered for
    // several).
    const val MAX_INTERNET_MESSAGES = 60
    const val MAX_BAND_SETS = 30

    /** Does a send by code fit? [lists]: setlists (loose songs are one list); [songs]: songs carried along. */
    fun internetFits(lists: Int, songs: Int): Boolean = 1 + lists + songs <= MAX_INTERNET_MESSAGES && songs <= MAX_SONGS

    /** Does a send to the band on the network fit? */
    fun bandFits(sets: Int): Boolean = sets <= MAX_BAND_SETS
    private const val LONG_TOKEN = 200
    private const val ENCODED_SHARE = 0.9

    /** Plain text: no control characters (except line break and tab) and no encoded block. */
    fun isPlainText(s: String): Boolean =
        s.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } && s.splitToSequence(' ', '\n', '\t', '\r').none(::looksEncoded)

    // long block of almost only letters/digits/+/=: encoded data. Tablature is almost only '-', and doesn't fall here
    private fun looksEncoded(token: String): Boolean {
        if (token.length < LONG_TOKEN) return false
        val encoded = token.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '_' }
        return encoded >= token.length * ENCODED_SHARE
    }

    fun songOk(title: String, author: String, year: String, text: String): Boolean =
        title.isNotBlank() && title.length <= MAX_TITLE && author.length <= MAX_TITLE && year.length <= MAX_YEAR &&
            text.length <= MAX_TEXT && isPlainText(title) && isPlainText(author) && isPlainText(year) && isPlainText(text)

    /**
     * Looks like lyrics or a chord chart (imports, user's request of 30/10: nothing unrelated comes in): at least 3
     * lines, and most of them short, as verses are (a page of prose or a document has long paragraphs).
     */
    fun looksLikeLyrics(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("{") }
        if (lines.size < 3) return false
        val short = lines.count { it.length <= SHORT_LINE }
        return short >= lines.size * SHORT_SHARE
    }

    /**
     * What import takes as a song (ChordPro or lyrics, [text] with chords): short lines like verses, at least
     * [MIN_VERSES] of them with [MIN_WORDS] words, and not mostly lines with numbers (a price list). A chart with
     * [MIN_CHORDS] chords or more is a chart (also instrumental, or short verses): the verse rule is for plain text.
     * The tablature doesn't count. The search
     * page's footer ("Ajuda", "Feedback"…) and a shopping list came in as songs (device test B-05).
     */
    fun looksLikeSong(text: String): Boolean {
        val noTab = Tablature.strip(text)
        if (Chords.bracket.findAll(noTab).count { Chords.isChord(it.groupValues[1]) } >= MIN_CHORDS) return true
        val plain = Chords.stripChords(noTab)
        if (!looksLikeLyrics(plain)) return false
        val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("{") && !it.startsWith("[") }
        val verses = lines.count { l -> l.split(' ').count { w -> w.any(Char::isLetter) } >= MIN_WORDS }
        val numbered = lines.count { l -> l.any(Char::isDigit) }
        return verses >= MIN_VERSES && numbered * 2 < lines.size
    }

    private const val MIN_CHORDS = 2
    private const val MIN_VERSES = 3
    private const val MIN_WORDS = 3

    private const val SHORT_LINE = 90
    private const val SHORT_SHARE = 0.7

    /** The setlist text (QR format): size, plain text and readable as a setlist. */
    fun listOk(text: String): Boolean = text.length <= MAX_LIST && isPlainText(text) && QrPayload.parse(text) != null
}
