package br.app.stagebook.data

import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.SongMatch
import br.app.stagebook.domain.TextKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Reading and writing stagebook.json and the library file ({"songs":[...]}) used for import/export. */
object LibraryCodec {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true    // unknown value (e.g. a new section language) falls back to the default
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = " "
    }

    // the app's own library file: nobody reads it by eye, and the indentation was a good part of a 2-3 MB write on
    // every change (audit 01/11). The exported songs file stays indented
    private val compact = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Serializable
    private data class SongsFile(val version: Int = 1, val songs: List<Song> = emptyList())

    fun decode(text: String, newId: () -> String): Library = withIds(json.decodeFromString<Library>(text), newId)

    private fun withIds(lib: Library, newId: () -> String): Library {
        return lib.copy(
            songs = lib.songs.map { if (it.id.isEmpty()) it.copy(id = newId()) else it },
            sets = lib.sets.map { if (it.id.isEmpty()) it.copy(id = newId()) else it },
        )
    }

    fun encode(library: Library): String = compact.encodeToString(Library.serializer(), library)

    /** Writes straight to the file, without building the ~1 MB text in memory (the Fire has 2 GB). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun encodeTo(library: Library, out: OutputStream) = compact.encodeToStream(Library.serializer(), library, out)

    /** Reads straight from the file (opening the app without holding the whole text before decoding). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun decode(input: InputStream, newId: () -> String): Library = withIds(json.decodeFromStream(Library.serializer(), input), newId)

    /** Songs file version this app understands; a newer file is refused with a notice (no guessing). */
    const val SONGS_FILE_VERSION = 1

    class NewerFileException : IllegalStateException("file from a newer Stagebook version")

    fun decodeSongs(text: String): List<Song> = json.decodeFromString<SongsFile>(text).also {
        if (it.version > SONGS_FILE_VERSION) throw NewerFileException()
    }.songs

    fun encodeSongs(songs: List<Song>): String =
        json.encodeToString(SongsFile.serializer(), SongsFile(songs = songs.sortedBy { TextKey.of(it.title) }))
}

/** [lyricsChanged]: of the updated ones, how many had their lyrics replaced by the file's. */
data class ImportResult(val added: Int, val replaced: Int, val lyricsChanged: Int = 0)

/**
 * Importing a library without duplicating: the same song (title + artist, see SongMatch) is updated, the others come in
 * as new. The stage zoom the song already had is kept.
 */
fun Library.mergeSongs(incoming: List<Song>, newId: () -> String): Pair<Library, ImportResult> {
    val songs = songs.toMutableList()
    var added = 0
    var replaced = 0
    var lyricsChanged = 0
    val index = SongMatch.Index(songs.indices, { songs[it].title }, { songs[it].author })
    for (song in incoming) {
        if (song.title.isBlank()) continue
        val i = index.find(song.title, song.author) ?: -1
        if (i >= 0) {
            if (songs[i].text != song.text) lyricsChanged++
            songs[i] = song.copy(id = songs[i].id, zoom = songs[i].zoom, author = song.author.ifBlank { songs[i].author }, year = song.year.ifBlank { songs[i].year })
            replaced++
        } else {
            songs += song.copy(id = newId())
            index.add(songs.lastIndex)
            added++
        }
    }
    return copy(songs = songs) to ImportResult(added, replaced, lyricsChanged)
}
