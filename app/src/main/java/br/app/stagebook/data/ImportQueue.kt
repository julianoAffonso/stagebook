package br.app.stagebook.data

import androidx.core.net.toUri
import android.net.Uri
import androidx.compose.runtime.Immutable
import br.app.stagebook.data.model.ImportSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch import of a setlist's songs missing from the library (coming from the QR): a guided queue. The importer opens
 * already searching for the current song on the first site in the profile's list (Settings → Import); if not found,
 * "Try on" moves to the next site in the list (failover, in the user's order). The user checks the page and imports as
 * usual: the app downloads nothing on its own. Once saved, the song goes into the setlist at the place it had in the
 * QR.
 */
@Singleton
class ImportQueue @Inject constructor(private val repository: LibraryRepository) {
    @Immutable
    data class Item(val title: String, val artist: String, val slot: Int)

    /**
     * [slots]: the setlist in QR order (id or null = missing); [site]: which site in the list the search is on;
     * [breaks]: the breaks the sender put between those songs (null = it said nothing: the QR, an older app).
     */
    @Immutable
    data class State(val setId: String, val setName: String, val slots: List<String?>, val pending: List<Item>, val total: Int, val site: Int = 0, val breaks: List<Int>? = null) {
        val current: Item? get() = pending.firstOrNull()
        val position: Int get() = total - pending.size + 1
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun start(setId: String, setName: String, slots: List<String?>, missing: List<Item>, breaks: List<Int>? = null) {
        _state.value = if (missing.isEmpty()) null else State(setId, setName, slots, missing, missing.size, breaks = breaks)
    }

    /** The current profile's sites (chords or lyrics only), in the user's order. */
    fun sites(): List<ImportSite> = repository.library.value.look.let { if (it.chords) it.chordSites else it.lyricSites }

    /** Search for the current song on the current site (Google limited to the site: works for any site in the list). */
    fun searchUrl(): String? {
        val s = _state.value ?: return null
        val item = s.current ?: return null
        return searchUrl(item, sites().getOrNull(s.site))
    }

    /** The next site in the list, for the "Try on" button; null if this is the last one. */
    fun nextSite(): ImportSite? = _state.value?.let { sites().getOrNull(it.site + 1) }

    fun tryNextSite() = _state.update { s -> if (s == null || s.site + 1 >= sites().size) s else s.copy(site = s.site + 1) }

    /** The current song was saved: it takes its place in the setlist and the queue moves on. */
    fun saved(songId: String) {
        val s = _state.value ?: return
        val item = s.current ?: return
        val slots = s.slots.toMutableList().also { it[item.slot] = songId }
        // the sender's breaks are placed again: one that waited before the next song goes back before this one
        if (s.breaks != null) repository.setReceivedSetlist(s.setId, slots, s.breaks) else repository.setSetlistItems(s.setId, slots.filterNotNull())
        advance(s.copy(slots = slots))
    }

    /** Skips the current song (it's left out of the setlist). */
    fun skip() { _state.value?.let(::advance) }

    fun stop() { _state.value = null }

    private fun advance(s: State) {
        val rest = s.pending.drop(1)
        _state.value = if (rest.isEmpty()) null else s.copy(pending = rest, site = 0)
    }

    companion object {
        fun searchUrl(item: Item, site: ImportSite?): String {
            val host = site?.url?.let { it.toUri().host }.orEmpty()
            val q = listOf(if (host.isEmpty()) "" else "site:$host", item.title, item.artist).filter { it.isNotBlank() }.joinToString(" ")
            return "https://www.google.com/search?q=" + Uri.encode(q)
        }
    }
}
