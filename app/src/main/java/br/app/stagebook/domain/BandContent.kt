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

    /** The setlist text (QR format): size, plain text and readable as a setlist. */
    fun listOk(text: String): Boolean = text.length <= MAX_LIST && isPlainText(text) && QrPayload.parse(text) != null
}
