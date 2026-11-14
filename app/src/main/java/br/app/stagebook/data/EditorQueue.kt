package br.app.stagebook.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The songs the editor steps through with "previous" and "next": the ids in the order of the place the editor was opened
 * from (a setlist, or the Songs list with the search and filters that were on), and where the open song is. Kept in memory
 * outside the route (the route only carries the song's id) and with a size limit; whoever opens the editor fills it, and
 * the editor empties it when it closes. Nothing here when the editor is opened from elsewhere (import, new song).
 */
@Singleton
class EditorQueue @Inject constructor() {
    @Immutable
    data class State(val ids: List<String>, val index: Int)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    // the order the Songs list showed when a song was opened from it (the stage that opens the editor reads it)
    @Volatile private var offered: List<String> = emptyList()

    /** The Songs list, as shown (search and filters applied), when a song is opened from it. */
    fun offerList(ids: List<String>) { offered = ids }

    /** Opens the editor over the list [offerList] gave, on [songId]; no queue when the song isn't in it. */
    fun openFromList(songId: String) = open(offered, offered.indexOf(songId))

    /** The setlist's (or list's) order, with the open song at [index] (out of range or blank ids: no queue). */
    fun open(ids: List<String>, index: Int) {
        if (index !in ids.indices || ids.size < 2) { clear(); return }
        // a very long list keeps a window around the open song
        val from = (index - MAX / 2).coerceAtLeast(0)
        val window = ids.subList(from, minOf(ids.size, from + MAX))
        _state.value = State(window, index - from)
    }

    /**
     * The stage's pencil and long press on a line: the queue for the editor of the song at [open] among the stage's [songs]
     * (id and whether it is missing). In a setlist: the setlist's order without the missing ones; opened from the Songs list
     * ([fromList]): that list as it was shown; otherwise no queue. The same for every way the stage opens the editor.
     */
    fun openFromStage(songs: List<Pair<String, Boolean>>, open: Int, inSetlist: Boolean, fromList: Boolean, songId: String) {
        if (inSetlist) {
            val valid = songs.filter { !it.second && it.first.isNotEmpty() }
            open(valid.map { it.first }, songs.take(open).count { !it.second && it.first.isNotEmpty() })
        } else if (fromList) openFromList(songId) else clear()
    }

    /** The editor moved to the song at [index] of the same queue. */
    fun moveTo(index: Int) {
        val s = _state.value ?: return
        if (index in s.ids.indices) _state.value = s.copy(index = index)
    }

    fun clear() { _state.value = null }

    companion object {
        const val MAX = 2000
    }
}

/** The app's queue for the screens that open the editor (the stage, the Songs list); a loose one in tests and previews. */
val LocalEditorQueue = androidx.compose.runtime.staticCompositionLocalOf { EditorQueue() }
