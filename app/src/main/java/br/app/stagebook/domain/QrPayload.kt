package br.app.stagebook.domain

import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Setlist
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Setlist QR content: the bytes `Q1` + deflate (no zlib header) of the UTF-8 text:
 * ```
 * <format header>
 * <number of songs>
 * <setlist name>
 * <title>[\t<artist>]
 * …
 * ```
 * Only name, titles and, when the title repeats in the library, the artist: each device has its own songs; the key is
 * the title, and the artist only breaks ties between equal titles ("It's My Life" by Bon Jovi and by Talk Talk).
 * Measured on 194 songs: 28 bytes per song with the artist always, 16 without the unneeded ones, and deflate removes
 * another ~30%. The count lets the reader check it got everything; the reader must flag, before creating the setlist,
 * every title it didn't find.
 */
object QrPayload {
    private const val NAME = "STAGEBOOK"
    private const val VERSION = 1
    const val HEADER = "$NAME$VERSION"   // format header; a higher number = a newer app (it asks to update)
    // "Q" + the version: a newer QR still unpacks here, so the reader can say "update the app" instead of "not a setlist"
    private val PACKED_MARK = byteArrayOf('Q'.code.toByte(), ('0'.code + VERSION).toByte())

    /**
     * QR ceiling, in bytes (already compressed). The QR holds up to 2,331 bytes at this correction level (M), but that
     * dense the TCL camera (analysis at 640x480) doesn't read it comfortably; ~1,000 bytes (version 25, 117 modules)
     * give ~80 songs with average titles. Raise it only after testing the scan on the TCL.
     */
    const val MAX_BYTES = 1000

    data class Entry(val title: String, val artist: String)

    data class Parsed(val name: String, val songs: List<Entry>)

    private fun clean(s: String) = s.replace('\n', ' ').replace('\t', ' ').trim()

    fun entries(set: Setlist, library: Library): List<Entry> =
        set.items.mapNotNull { id -> library.song(id)?.let { Entry(clean(it.title), clean(it.author)) } }

    /** Titles (by key) that appear more than once in the library: only those carry the artist. */
    private fun repeatedTitles(library: Library): Set<String> = TitleKeys.of(library.songs).repeated   // worked out once per library

    /** The QR text (before compressing). */
    fun text(set: Setlist, library: Library, name: String = set.name, extra: List<Entry> = emptyList()): String {
        val repeated = repeatedTitles(library)
        val songs = entries(set, library) + extra
        val lines = songs.map { if (it.artist.isEmpty() || TextKey.of(it.title) !in repeated) it.title else it.title + "\t" + it.artist }
        return (listOf(HEADER, songs.size.toString(), clean(name)) + lines).joinToString("\n")
    }

    /** The bytes that go in the QR: `Q1` + deflate of the text. */
    fun pack(text: String): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            deflater.setInput(text.toByteArray(Charsets.UTF_8))
            deflater.finish()
            val buf = ByteArray(text.length * 3 + 64)
            val n = deflater.deflate(buf)
            PACKED_MARK + buf.copyOf(n)
        } finally {
            deflater.end()
        }
    }

    fun build(set: Setlist, library: Library): ByteArray = pack(text(set, library))

    /** Whether the setlist as it is, with another name and/or more songs (library ids), still fits in the QR. */
    fun fits(set: Setlist, library: Library, name: String = set.name, extraIds: List<String> = emptyList()): Boolean {
        val extra = extraIds.mapNotNull { id -> library.song(id)?.let { Entry(clean(it.title), clean(it.author)) } }
        return pack(text(set, library, name, extra)).size <= MAX_BYTES
    }

    private const val MAX_TEXT = 64 * 1024   // a setlist never gets close; guards against odd data

    /** Compressed QR text (`Q1` + deflate), or null if the bytes aren't in that format. */
    fun unpack(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size <= PACKED_MARK.size || bytes[0] != PACKED_MARK[0] || bytes[1] !in '1'.code.toByte()..'9'.code.toByte()) return null
        val inflater = Inflater(true)
        return try {
            inflater.setInput(bytes, PACKED_MARK.size, bytes.size - PACKED_MARK.size)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null   // cortado
                out.write(buf, 0, n)
                if (out.size() > MAX_TEXT) return null
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Setlist QR in a format newer than this app (a higher header number): asks to update. */
    fun isNewer(text: String): Boolean =
        Regex("^$NAME(\\d+)\n").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { it > VERSION } == true

    /**
     * Reading the already decompressed text (reference for the reader): null if it isn't a setlist QR or a title is
     * missing.
     */
    fun parse(payload: String): Parsed? {
        val lines = payload.replace("\r", "").split('\n')
        if (lines.size < 3 || lines[0] != HEADER) return null
        val count = lines[1].trim().toIntOrNull() ?: return null
        val songs = lines.drop(3).filter { it.isNotBlank() }.map { l ->
            val parts = l.split('\t', limit = 2)
            Entry(parts[0], parts.getOrElse(1) { "" })
        }
        if (songs.size != count) return null
        return Parsed(lines[2], songs)
    }
}
