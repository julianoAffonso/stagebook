package br.app.stagebook.data

import android.content.Context
import android.util.Log
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SectionLang
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.di.ApplicationScope
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SectionGuesser
import br.app.stagebook.domain.Sections
import br.app.stagebook.domain.SongMatch
import br.app.stagebook.domain.SongSearch
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
 * Library, setlists and look in a JSON inside the app (no account). Every change applies right away in memory and is
 * saved in the background, with an atomic file swap.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "stagebook.json")
    private val backup = File(context.filesDir, "stagebook.json.bak")   // the version saved before the current one
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()
    /** Song search index, rebuilt only when the songs change (not with color, font or setlist). */
    val songSearch: Flow<SongSearch> = library.map { it.songs }
        // search and list rows only use title, artist and year (and the order): saving zoom or lyrics doesn't rebuild
        // the keys of 200 songs
        .distinctUntilChanged { a, b ->
            a.size == b.size && a.indices.all { a[it].id == b[it].id && a[it].title == b[it].title && a[it].author == b[it].author && a[it].year == b[it].year }
        }
        .map(SongSearch::of)
        .flowOn(Dispatchers.Default)
        // a single index for Songs and Pick songs, kept 5 s after the last screen leaves
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
            // corrupted file: keeps the original so nothing is lost and opens the previous version
            file.renameTo(File(file.parentFile, "stagebook.corrompido.${System.currentTimeMillis()}.json"))
            readOrNull(backup) ?: Library()
        }
    }

    private fun readOrNull(f: File): Library? =
        if (!f.exists()) null
        else try { f.inputStream().buffered(64 * 1024).use { LibraryCodec.decode(it, ::newId) } } catch (e: Exception) { Log.w(TAG, "não li ${f.name}", e); null }

    /** Changes memory right away (the screen reacts at once) and saves next. Before loading, waits for the file. */
    private fun mutate(change: (Library) -> Library) {
        if (!ready.isCompleted) {
            scope.launch { ready.await(); mutate(change) }
            return
        }
        _library.update(change)
        // several changes in a row (import, standardize) become a single save, with the newest state
        if (writePending.compareAndSet(false, true)) scope.launch { persist() }
    }

    private suspend fun persist() = withContext(Dispatchers.IO + NonCancellable) {
        writeLock.withLock {
            writePending.set(false)
            // a full disk or a write failure can't crash the app on stage: the change stays in memory and the next
            // change tries to save again
            try {
                // atomic swap: powering off midway doesn't corrupt the library
                val lib = _library.value
                file.replaceAtomically(backup) { LibraryCodec.encodeTo(lib, it) }
            } catch (e: IOException) {
                Log.w(TAG, "falha ao gravar a biblioteca", e)
            }
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---- songs ----
    fun song(id: String): Song? = _library.value.song(id)

    /** The same song already in the library (title + artist; see SongMatch), or null. */
    fun findSong(title: String, artist: String): Song? =
        _library.value.songs.firstOrNull { SongMatch.same(it.title, it.author, title, artist) }

    /** Saves the song (empty id = new) and returns the id. */
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

    /** Deletes the song and removes it from the setlists. */
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

    /**
     * Only removes it if it's still the same song in that place: the 2nd tap of a double tap doesn't take the next one.
     */
    fun removeItem(setId: String, index: Int, songId: String) = updateSet(setId) { s ->
        if (s.items.getOrNull(index) != songId) s else s.copy(items = s.items.toMutableList().apply { removeAt(index) })
    }

    /** They go at the end, in the chosen order; one already in the setlist doesn't go in again. */
    fun addToSetlist(setId: String, songIds: List<String>) = updateSet(setId) { s ->
        s.copy(items = s.items + songIds.filterNot { it in s.items }.distinct())
    }

    /** Replaces the setlist's songs (the QR scanned over a setlist with the same name). */
    fun setSetlistItems(setId: String, songIds: List<String>) = updateSet(setId) { it.copy(items = songIds.distinct()) }

    fun qrPayload(setId: String): ByteArray? = _library.value.setlist(setId)?.let { QrPayload.build(it, _library.value) }

    // ---- look ----
    fun updateLook(change: (Look) -> Look) = mutate { it.copy(look = change(it.look)) }

    data class SectionsResult(val changed: Int, val unidentified: Int)

    /**
     * Section titles of every song: existing ones are rewritten in the "[Chorus]" format; lyrics with no title at all
     * get their sections found by repetition. Without repetition, they stay as they are.
     */
    suspend fun normalizeSections(lang: SectionLang): SectionsResult {
        ready.await()
        // heavy (the whole library, finding repetition): off the main thread
        val before = _library.value.songs.associate { it.id to it.text }
        var unidentified = 0
        val updated = withContext(Dispatchers.Default) {
            // existing titles stay as they are (the stage shows them in the app's language); only lyrics with no title
            // at all get sections, found by repetition
            before.mapValues { (_, text) ->
                val hasHeaders = text.lines().any { Sections.detect(it) != null || SECTION_DIRECTIVE.matches(it) }
                if (hasHeaders) text
                else SectionGuesser.guess(text, lang) ?: text.also { if (it.isNotBlank()) unidentified++ }
            }
        }
        // only replaces lyrics that are still the same as when it started: an edit or a sync that arrived midway isn't
        // undone by the previous version's text
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

    // ---- library file ----
    /** What the import would do, without changing anything (to ask before replacing lyrics). */
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

    /**
     * Backup added to what's here: only missing songs come in (the same song, by title and artist, stays as it is here)
     * and setlists with a new name, pointing to the songs here. Returns how many songs and setlists came in.
     */
    suspend fun mergeBackup(songs: List<Song>, sets: List<Setlist>): Pair<Int, Int> {
        ready.await()
        var added = 0
        var addedSets = 0
        mutate { lib ->
            val all = lib.songs.toMutableList()
            val idOf = HashMap<String, String>()   // id in the backup → id here
            for (s in songs) {
                if (s.title.isBlank()) continue
                val here = all.firstOrNull { SongMatch.same(it.title, it.author, s.title, s.author) }
                if (here != null) idOf[s.id] = here.id
                else { val n = s.copy(id = newId()); all += n; idOf[s.id] = n.id; added++ }
            }
            val names = lib.sets.map { it.name.trim().lowercase() }.toSet()
            val newSets = sets.filter { it.name.trim().lowercase() !in names }
                .map { it.copy(id = newId(), items = it.items.mapNotNull(idOf::get)) }
            addedSets = newSets.size
            lib.copy(songs = all, sets = lib.sets + newSets)
        }
        return added to addedSets
    }

    /** Backup restored: its songs and setlists replace the ones here (the look stays). */
    suspend fun restore(songs: List<Song>, sets: List<Setlist>) {
        ready.await()
        mutate { lib -> lib.copy(songs = songs, sets = sets) }
    }

}

private const val TAG = "stagebook"

/** ChordPro section (`{start_of_chorus}`, `{soc}`…): the song already has titles. */
private val SECTION_DIRECTIVE = Regex("""^\s*\{\s*(start_of_\w+|so[cvb])\s*(:.*)?}\s*$""", RegexOption.IGNORE_CASE)
