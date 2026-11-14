package br.app.stagebook.domain

import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * A setlist's breaks, as they travel with it to the band and by the 3 words: a short tail after the setlist's package
 * (`Q1` + deflate, see [QrPayload]), the text `B1:` and the positions, as in `B1:3,7` (`B1:` alone = no breaks).
 *
 * An app that doesn't know the tail still reads the package: it unpacks up to the end of the compressed text and never
 * looks at what comes after, so it gets the setlist with no breaks instead of refusing it. That is why the breaks
 * aren't lines of the setlist's text (the count of songs wouldn't match there) and why no version number goes up. The
 * QR on screen never carries the tail: its format doesn't change.
 */
object SharedBreaks {
    private const val MARK = "B1:"
    private const val MAX_TAIL = 2_000     // 200 positions of up to 3 digits and their commas fit in half of this
    private const val MAX_TEXT = 64 * 1024   // the same ceiling as the setlist's text

    /** The setlist's package with its breaks after it. */
    fun pack(list: ByteArray, breaks: List<Int>): ByteArray = list + (MARK + breaks.joinToString(",")).toByteArray(Charsets.US_ASCII)

    /**
     * The breaks that came after the package whose text is [text], already valid for its songs (see
     * [SetlistBreaks.normalise]). Null when the sender said nothing about breaks (the QR, an older app) or the tail
     * can't be read; an empty list when it said the setlist has none.
     */
    fun read(packed: ByteArray, text: String): List<Int>? {
        val songs = QrPayload.parse(text)?.songs?.size ?: return null
        val tail = tail(packed)?.takeIf { it.size in MARK.length..MAX_TAIL }?.toString(Charsets.US_ASCII) ?: return null
        if (!tail.startsWith(MARK)) return null
        val body = tail.substring(MARK.length)
        if (body.isEmpty()) return emptyList()
        if (body.any { it != ',' && it !in '0'..'9' }) return null
        // a number too long to be a position isn't one: it is left out, like any other out of range
        return SetlistBreaks.normalise(body.split(',').mapNotNull { it.toIntOrNull() }, songs)
    }

    // the bytes after the end of the compressed text; null if it isn't a package
    private fun tail(packed: ByteArray): ByteArray? {
        if (packed.size <= 2) return null
        val inflater = Inflater(true)
        return try {
            inflater.setInput(packed, 2, packed.size - 2)
            val buf = ByteArray(4096)
            var total = 0
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                total += n
                if (total > MAX_TEXT) return null
            }
            packed.copyOfRange(packed.size - inflater.remaining, packed.size)
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }
}
