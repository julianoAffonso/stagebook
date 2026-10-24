package com.juliano.stagebook.data

import androidx.core.net.toUri
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.juliano.stagebook.data.model.ImportSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Importar em lote as músicas de um setlist que faltam na biblioteca (vindas do QR): uma fila guiada. O importador
 * abre já buscando a música da vez no 1º site da lista do perfil (Ajustes → Importação); não achou, "Tentar em" passa
 * para o próximo site da lista (failover, na ordem do usuário). O usuário confere a página e importa como sempre: o app
 * não baixa nada sozinho. Salva, a música entra no setlist no lugar que tinha no QR.
 */
@Singleton
class ImportQueue @Inject constructor(private val repository: LibraryRepository) {
    @Immutable
    data class Item(val title: String, val artist: String, val slot: Int)

    /** [slots]: o setlist na ordem do QR (id ou null = falta); [site]: em qual site da lista a busca está. */
    @Immutable
    data class State(val setId: String, val setName: String, val slots: List<String?>, val pending: List<Item>, val total: Int, val site: Int = 0) {
        val current: Item? get() = pending.firstOrNull()
        val position: Int get() = total - pending.size + 1
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun start(setId: String, setName: String, slots: List<String?>, missing: List<Item>) {
        _state.value = if (missing.isEmpty()) null else State(setId, setName, slots, missing, missing.size)
    }

    /** Os sites do perfil atual (cifra ou só letra), na ordem do usuário. */
    fun sites(): List<ImportSite> = repository.library.value.look.let { if (it.chords) it.chordSites else it.lyricSites }

    /** Busca da música da vez no site da vez (Google restrito ao site: vale para qualquer site da lista). */
    fun searchUrl(): String? {
        val s = _state.value ?: return null
        val item = s.current ?: return null
        return searchUrl(item, sites().getOrNull(s.site))
    }

    /** O próximo site da lista, para o botão "Tentar em"; null se este já é o último. */
    fun nextSite(): ImportSite? = _state.value?.let { sites().getOrNull(it.site + 1) }

    fun tryNextSite() = _state.update { s -> if (s == null || s.site + 1 >= sites().size) s else s.copy(site = s.site + 1) }

    /** A música da vez foi salva: entra no setlist no lugar dela e a fila anda. */
    fun saved(songId: String) {
        val s = _state.value ?: return
        val item = s.current ?: return
        val slots = s.slots.toMutableList().also { it[item.slot] = songId }
        repository.setSetlistItems(s.setId, slots.filterNotNull())
        advance(s.copy(slots = slots))
    }

    /** Pula a música da vez (fica de fora do setlist). */
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
