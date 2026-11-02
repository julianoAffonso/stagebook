package br.app.stagebook.data

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the band sent and hasn't been reviewed yet (setlist or loose songs). Kept until reviewed or discarded, even if
 * the app closes. The tabs show a notice (like new email); on stage nothing shows.
 */
@Singleton
class BandInbox @Inject constructor(@ApplicationContext context: Context) {
    /** [text]: the list in QR format (no name = loose songs); [seen]: the notice was already dismissed ("Not now"). */
    @Immutable
    @Serializable
    data class Item(
        val id: String,
        val fromId: String,
        val fromName: String,
        val text: String,
        val songs: List<SetlistCloud.SharedSong> = emptyList(),
        val at: Long = System.currentTimeMillis(),
        val seen: Boolean = false,
        val updated: Boolean = false,   // the same person sent again with changes: "updated at 21:04"
    ) {
        /** Setlist name; empty = loose songs. */
        val setName: String get() = text.split('\n').getOrNull(2).orEmpty()
        val songCount: Int get() = text.split('\n').getOrNull(1)?.trim()?.toIntOrNull() ?: songs.size
        /** Came from a newer Stagebook: it can only be reviewed after updating. */
        val newer: Boolean get() = text == SetlistCloud.NEWER
    }

    // the items carry every song's text: a file of their own, replaced whole (prefs keep everything in memory and
    // rewrite the whole XML; audit 01/11)
    private val file = File(context.filesDir, FILE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items

    /**
     * Keeps what arrived. The same send arriving twice (over Wi-Fi and the internet) doesn't repeat; the same setlist
     * from the same person replaces the old one (they fixed it and sent again: no point reviewing the old version).
     */
    fun add(fromId: String, fromName: String, text: String, songs: List<SetlistCloud.SharedSong>) {
        // everything from the band (local network or internet) passes here: only setlists and songs in plain text
        if (!br.app.stagebook.domain.BandContent.listOk(text)) return
        val item = Item(UUID.randomUUID().toString(), fromId, fromName, text, songs.filter { it.allowed() }.take(br.app.stagebook.domain.BandContent.MAX_SONGS))
        _items.update { list ->
            if (list.any { it.fromId == fromId && it.text == text && it.songs == item.songs }) return@update list
            val old = if (item.setName.isEmpty()) emptyList() else list.filter { it.fromId == fromId && it.setName == item.setName }
            // the same person's new version replaces the old one and is marked updated (and unseen again)
            ((list - old.toSet()) + item.copy(updated = old.isNotEmpty())).takeLast(MAX)
        }
        save()
    }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    // from the screen: written off the main thread (the list carries every song's text)
    fun markSeen(id: String) { _items.update { l -> l.map { if (it.id == id) it.copy(seen = true) else it } }; scope.launch { save() } }

    fun remove(id: String) { _items.update { l -> l.filterNot { it.id == id } }; scope.launch { save() } }

    private val scope = backgroundScope(kotlinx.coroutines.Dispatchers.IO)

    private fun load(): List<Item> = runCatching { json.decodeFromString<List<Item>>(file.readText()) }.getOrDefault(emptyList())

    // one at a time, each with the list as it is now: two threads saving could write an older list last and lose an
    // item that arrived (audit 01/11). A full disk keeps the list in memory and logs (never a crash)
    @Synchronized
    private fun save() {
        runCatching { file.replaceAtomically(json.encodeToString(_items.value).toByteArray()) }
            .onFailure { android.util.Log.w("BandInbox", "couldn't save the inbox", it) }
    }

    private companion object {
        const val FILE = "inbox.json"
        const val MAX = 30   // the inbox doesn't grow without limit
    }
}

/**
 * Grouped received items (T11): the same setlist (by name) sent by more than one member stays together, newest first,
 * so the person can pick which to use or compare; different setlists each stay in their own group, without one deleting
 * the other. Loose songs stay alone. Groups ordered by most recent.
 */
fun groupInbox(items: List<BandInbox.Item>): List<List<BandInbox.Item>> =
    items.groupBy { if (it.setName.isEmpty()) "#" + it.id else it.setName.trim().lowercase() }
        .values.map { g -> g.sortedByDescending { it.at } }
        .sortedByDescending { g -> g.first().at }

