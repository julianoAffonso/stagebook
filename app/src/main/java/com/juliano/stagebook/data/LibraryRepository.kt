package com.juliano.stagebook.data

import android.content.Context
import android.util.Log
import com.juliano.stagebook.data.model.Library
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.SectionLang
import com.juliano.stagebook.data.model.Setlist
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.di.ApplicationScope
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.domain.SectionGuesser
import com.juliano.stagebook.domain.Sections
import com.juliano.stagebook.domain.SongMatch
import com.juliano.stagebook.domain.SongSearch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biblioteca, setlists e aparência num JSON dentro do app (sem conta). Toda mudança vale na hora
 * na memória e é gravada em segundo plano, com troca atômica do arquivo.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "stagebook.json")
    private val backup = File(context.filesDir, "stagebook.json.bak")   // a versão gravada antes da atual
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()
    /** Índice de busca das músicas, refeito só quando as músicas mudam (não com cor, fonte ou setlist). */
    val songSearch: Flow<SongSearch> = library.map { it.songs }
        // a busca e as linhas da lista só usam título, artista e ano (e a ordem): gravar zoom ou letra não
        // refaz as chaves das 200 músicas
        .distinctUntilChanged { a, b ->
            a.size == b.size && a.indices.all { a[it].id == b[it].id && a[it].title == b[it].title && a[it].author == b[it].author && a[it].year == b[it].year }
        }
        .map(SongSearch::of)
        .flowOn(Dispatchers.Default)
        // um índice só para Músicas e Escolher músicas, guardado 5 s depois da última tela sair
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val ready = CompletableDeferred<Unit>()
    private val writeLock = Mutex()
    private val writePending = AtomicBoolean(false)

    init {
        scope.launch(Dispatchers.IO) {
            _library.value = load()
            _loaded.value = true
            ready.complete(Unit)
        }
    }

    private fun load(): Library {
        if (!file.exists()) return readOrNull(backup) ?: Library()
        return readOrNull(file) ?: run {
            // arquivo corrompido: guarda o original para não perder nada e abre a versão anterior
            file.renameTo(File(file.parentFile, "stagebook.corrompido.${System.currentTimeMillis()}.json"))
            readOrNull(backup) ?: Library()
        }
    }

    private fun readOrNull(f: File): Library? =
        if (!f.exists()) null
        else try { f.inputStream().buffered(64 * 1024).use { LibraryCodec.decode(it, ::newId) } } catch (e: Exception) { Log.w(TAG, "não li ${f.name}", e); null }

    /** Muda na memória já (a tela reage na hora) e grava em seguida. Antes de carregar, espera o arquivo. */
    private fun mutate(change: (Library) -> Library) {
        if (!ready.isCompleted) {
            scope.launch { ready.await(); mutate(change) }
            return
        }
        _library.update(change)
        // várias mudanças seguidas (importar, padronizar) viram uma gravação só, com o estado mais novo
        if (writePending.compareAndSet(false, true)) scope.launch { persist() }
    }

    private suspend fun persist() = withContext(Dispatchers.IO + NonCancellable) {
        writeLock.withLock {
            writePending.set(false)
            // disco cheio ou falha de gravação não pode derrubar o app no palco: a mudança fica na
            // memória e a próxima mudança tenta gravar de novo
            try {
                // troca atômica: desligar no meio não corrompe a biblioteca
                val lib = _library.value
                file.replaceAtomically(backup) { LibraryCodec.encodeTo(lib, it) }
            } catch (e: IOException) {
                Log.w(TAG, "falha ao gravar a biblioteca", e)
            }
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---- músicas ----
    fun song(id: String): Song? = _library.value.song(id)

    /** A mesma música já na biblioteca (título + artista; ver SongMatch), ou null. */
    fun findSong(title: String, artist: String): Song? =
        _library.value.songs.firstOrNull { SongMatch.same(it.title, it.author, title, artist) }

    /** Grava a música (id vazio = nova) e devolve o id. */
    fun saveSong(song: Song): String {
        val id = song.id.ifEmpty { newId() }
        val saved = song.copy(id = id)
        mutate { lib ->
            val exists = lib.songs.any { it.id == id }
            lib.copy(songs = if (exists) lib.songs.map { if (it.id == id) saved else it } else lib.songs + saved)
        }
        return id
    }

    fun updateSong(id: String, change: (Song) -> Song) =
        mutate { lib -> lib.copy(songs = lib.songs.map { if (it.id == id) change(it) else it }) }

    /** Apaga a música e tira dos setlists. */
    fun deleteSong(id: String) = mutate { lib ->
        lib.copy(songs = lib.songs.filterNot { it.id == id }, sets = lib.sets.map { s -> s.copy(items = s.items.filterNot { it == id }) })
    }

    // ---- setlists ----
    fun createSetlist(name: String): String {
        val id = newId()
        mutate { it.copy(sets = listOf(Setlist(id = id, name = name)) + it.sets) }
        return id
    }

    private fun updateSet(id: String, change: (Setlist) -> Setlist) =
        mutate { lib -> lib.copy(sets = lib.sets.map { if (it.id == id) change(it) else it }) }

    fun renameSetlist(id: String, name: String) = updateSet(id) { it.copy(name = name) }

    fun deleteSetlist(id: String) = mutate { lib -> lib.copy(sets = lib.sets.filterNot { it.id == id }) }

    fun moveItem(setId: String, from: Int, to: Int) = updateSet(setId) { s ->
        if (from !in s.items.indices || to !in s.items.indices) s
        else s.copy(items = s.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Só tira se ainda for a mesma música no lugar: o 2º toque de um toque duplo não leva a seguinte. */
    fun removeItem(setId: String, index: Int, songId: String) = updateSet(setId) { s ->
        if (s.items.getOrNull(index) != songId) s else s.copy(items = s.items.toMutableList().apply { removeAt(index) })
    }

    /** Entram no fim, na ordem escolhida; a que já está no setlist não entra de novo. */
    fun addToSetlist(setId: String, songIds: List<String>) = updateSet(setId) { s ->
        s.copy(items = s.items + songIds.filterNot { it in s.items }.distinct())
    }

    fun qrPayload(setId: String): ByteArray? = _library.value.setlist(setId)?.let { QrPayload.build(it, _library.value) }

    // ---- aparência ----
    fun updateLook(change: (Look) -> Look) = mutate { it.copy(look = change(it.look)) }

    data class SectionsResult(val changed: Int, val unidentified: Int)

    /**
     * Títulos de seção de todas as músicas: os que existem são reescritos no formato "[Refrão]";
     * letra sem nenhum título tem as seções identificadas pela repetição. Sem repetição, fica como está.
     */
    suspend fun normalizeSections(lang: SectionLang): SectionsResult {
        ready.await()
        // pesado (toda a biblioteca, identificação por repetição): fora da thread principal
        val before = _library.value.songs.associate { it.id to it.text }
        var unidentified = 0
        val updated = withContext(Dispatchers.Default) {
            // títulos que já existem ficam como estão (o palco mostra na língua do app); só a letra sem nenhum
            // título ganha as seções, identificadas pela repetição
            before.mapValues { (_, text) ->
                val hasHeaders = text.lines().any { Sections.detect(it) != null || SECTION_DIRECTIVE.matches(it) }
                if (hasHeaders) text
                else SectionGuesser.guess(text, lang) ?: text.also { if (it.isNotBlank()) unidentified++ }
            }
        }
        // só troca a letra que ainda é a mesma de quando começou: uma edição ou uma sincronia que
        // chegou no meio não é desfeita pelo texto da versão anterior
        var changed = 0
        mutate { lib ->
            changed = 0
            lib.copy(songs = lib.songs.map { s ->
                val new = updated[s.id]
                if (new != null && new != s.text && before[s.id] == s.text) { changed++; s.copy(text = new) } else s
            })
        }
        return SectionsResult(changed, unidentified)
    }

    // ---- arquivo de biblioteca ----
    /** O que o import faria, sem mudar nada (para perguntar antes de trocar letras). */
    suspend fun previewImport(json: String): ImportResult {
        ready.await()
        return _library.value.mergeSongs(LibraryCodec.decodeSongs(json)) { "" }.second
    }

    suspend fun importSongs(json: String): ImportResult {
        ready.await()
        val incoming = LibraryCodec.decodeSongs(json)
        var result = ImportResult(0, 0)
        mutate { lib -> lib.mergeSongs(incoming, ::newId).also { result = it.second }.first }
        return result
    }

    fun exportSongs(): String = LibraryCodec.encodeSongs(_library.value.songs)
}

private const val TAG = "stagebook"

/** Seção do ChordPro (`{start_of_chorus}`, `{soc}`…): a música já tem títulos. */
private val SECTION_DIRECTIVE = Regex("""^\s*\{\s*(start_of_\w+|so[cvb])\s*(:.*)?}\s*$""", RegexOption.IGNORE_CASE)
