package com.juliano.stagebook.data

import com.juliano.stagebook.data.model.Library
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.domain.SongMatch
import com.juliano.stagebook.domain.TextKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Leitura e gravação do letras.json e do arquivo de biblioteca ({"songs":[...]}) usado para importar/exportar. */
object LibraryCodec {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true    // valor desconhecido (ex.: idioma de seção novo) cai no padrão
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = " "
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

    fun encode(library: Library): String = json.encodeToString(Library.serializer(), library)

    /** Grava direto no arquivo, sem montar o texto de ~1 MB na memória (o Fire tem 2 GB). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun encodeTo(library: Library, out: OutputStream) = json.encodeToStream(Library.serializer(), library, out)

    /** Lê direto do arquivo (abrir o app sem segurar o texto inteiro antes de decodificar). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun decode(input: InputStream, newId: () -> String): Library = withIds(json.decodeFromStream(Library.serializer(), input), newId)

    fun decodeSongs(text: String): List<Song> = json.decodeFromString<SongsFile>(text).songs

    fun encodeSongs(songs: List<Song>): String =
        json.encodeToString(SongsFile.serializer(), SongsFile(songs = songs.sortedBy { TextKey.of(it.title) }))
}

/** [lyricsChanged]: das atualizadas, quantas tiveram a letra trocada pela do arquivo. */
data class ImportResult(val added: Int, val replaced: Int, val lyricsChanged: Int = 0)

/**
 * Importar uma biblioteca sem duplicar: a mesma música (título + artista, ver SongMatch) é atualizada,
 * as outras entram novas. O zoom do palco que a música já tinha é mantido.
 */
fun Library.mergeSongs(incoming: List<Song>, newId: () -> String): Pair<Library, ImportResult> {
    val songs = songs.toMutableList()
    var added = 0
    var replaced = 0
    var lyricsChanged = 0
    for (song in incoming) {
        if (song.title.isBlank()) continue
        val i = songs.indexOfFirst { SongMatch.same(it.title, it.author, song.title, song.author) }
        if (i >= 0) {
            if (songs[i].text != song.text) lyricsChanged++
            songs[i] = song.copy(id = songs[i].id, zoom = songs[i].zoom, author = song.author.ifBlank { songs[i].author }, year = song.year.ifBlank { songs[i].year })
            replaced++
        } else {
            songs += song.copy(id = newId())
            added++
        }
    }
    return copy(songs = songs) to ImportResult(added, replaced, lyricsChanged)
}
