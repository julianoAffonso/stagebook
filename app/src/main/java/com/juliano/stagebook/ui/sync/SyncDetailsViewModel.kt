package com.juliano.stagebook.ui.sync

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.data.LastCheck
import com.juliano.stagebook.data.LibrarySync
import com.juliano.stagebook.data.SyncChange
import com.juliano.stagebook.data.SyncDetails
import com.juliano.stagebook.data.SyncKind
import com.juliano.stagebook.domain.LineDiff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** Grupos da revisão (cada um com "aceitar todas" / "manter todas"). */
enum class SyncGroup(val kinds: Set<SyncKind>) {
    CHANGED(setOf(SyncKind.UPDATED, SyncKind.SECTIONS, SyncKind.META)),
    PROTECTED(setOf(SyncKind.PROTECTED, SyncKind.META_PROTECTED)),
    ADDED(setOf(SyncKind.ADDED)),
}

/** A escolha do usuário para uma música da atualização. */
enum class SyncChoice { PENDING, KEPT_NEW, KEPT_MINE, REMOVED }

@Immutable
data class DiffLineUi(val kind: LineDiff.Kind, val text: String)

/** Um dado (título, artista ou ano) que mudou: antes → depois. */
@Immutable
data class MetaLineUi(val field: MetaField, val from: String, val to: String)

enum class MetaField { TITLE, AUTHOR, YEAR }

/** Um cartão: a música, o que mudou (só as linhas mudadas, com contexto) e a escolha. */
@Immutable
data class SyncCardUi(
    val key: String,   // cartão (a mesma música pode ter um da letra e outro dos dados)
    val songId: String,
    val title: String,
    val kind: SyncKind,
    val choice: SyncChoice,
    val diff: List<DiffLineUi> = emptyList(),
    val moreLines: Int = 0,
    val meta: List<MetaLineUi> = emptyList(),
)

@Immutable
data class SyncDetailsUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val date: String = "",
    val rebuilt: Boolean = false,
    val lastCheck: LastCheckUi? = null,   // a última conferência, mesmo sem mudança
    val updating: Boolean = false,   // há uma atualização nova a caminho: esta lista é a anterior
    val changed: List<SyncCardUi> = emptyList(),
    val protectedSongs: List<SyncCardUi> = emptyList(),   // o usuário tem a versão dele; a atualizada é outra
    val added: List<SyncCardUi> = emptyList(),
)

@Immutable
data class LastCheckUi(val date: String, val result: LastCheck.Result, val count: Int)

/** Manter a versão do usuário não deu: a letra foi editada depois da atualização. */
data object UndoRefused

@HiltViewModel
class SyncDetailsViewModel @Inject constructor(private val sync: LibrarySync) : ViewModel() {
    private val _uiState = MutableStateFlow(SyncDetailsUiState())
    val uiState: StateFlow<SyncDetailsUiState> = _uiState.asStateFlow()
    private val _refused = Channel<UndoRefused>(Channel.CONFLATED)
    val refused = _refused.receiveAsFlow()
    // a comparação de linhas de cada cartão não muda (antes/depois são fixos): calcula uma vez só, não a
    // cada escolha (com ~160 cartões, refazer todas pesava no Fire). Chave inclui a hora da atualização.
    private val diffs = java.util.concurrent.ConcurrentHashMap<String, Pair<List<DiffLineUi>, Int>>()

    init {
        refresh()
        // a atualização a caminho chegou: a lista passa a ser a dela
        viewModelScope.launch {
            var was = sync.updating.value
            sync.updating.collect { now ->
                if (was && !now) refresh()
                was = now
                _uiState.value = _uiState.value.copy(updating = now)
            }
        }
    }

    fun keepNew(songId: String) = act { sync.keep(songId) }
    fun keepMine(songId: String) = act { if (!sync.revert(songId)) _refused.send(UndoRefused) }
    fun removeAdded(songId: String) = act { sync.removeNew(songId) }
    fun acceptAll(group: SyncGroup) = act { sync.decideAll(group.kinds, accept = true) }
    fun keepAll(group: SyncGroup) = act { if (sync.decideAll(group.kinds, accept = false) > 0) _refused.send(UndoRefused) }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch { block(); show(sync.details()) }

    private fun refresh() = viewModelScope.launch { show(sync.details()) }

    private suspend fun show(d: SyncDetails?) { _uiState.value = ui(d).copy(updating = sync.updating.value) }

    // comparar as letras é trabalho de CPU: fora da thread principal
    private fun dateOf(at: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(at))

    private suspend fun ui(d: SyncDetails?): SyncDetailsUiState = withContext(Dispatchers.Default) {
        val check = sync.lastCheck()?.let { LastCheckUi(dateOf(it.at), it.result, it.count) }
        if (d == null || d.changes.isEmpty()) return@withContext SyncDetailsUiState(loading = false, empty = true, lastCheck = check)
        SyncDetailsUiState(
            lastCheck = check,
            loading = false,
            date = dateOf(d.at),
            rebuilt = d.rebuilt,
            changed = d.changes.filter { it.kind in SyncGroup.CHANGED.kinds }.map { card(d.at, it) },
            protectedSongs = d.changes.filter { it.kind in SyncGroup.PROTECTED.kinds }.map { card(d.at, it) },
            added = d.changes.filter { it.kind == SyncKind.ADDED }.map { card(d.at, it) },
        )
    }

    private fun card(at: Long, c: SyncChange): SyncCardUi {
        val choice = when {
            c.reverted -> SyncChoice.KEPT_MINE
            c.removed -> SyncChoice.REMOVED
            c.kept -> SyncChoice.KEPT_NEW
            else -> SyncChoice.PENDING
        }
        if (c.kind == SyncKind.ADDED) return SyncCardUi(c.key, c.songId, c.title, c.kind, choice)
        if (c.kind == SyncKind.META || c.kind == SyncKind.META_PROTECTED) {
            val meta = listOfNotNull(
                MetaLineUi(MetaField.TITLE, c.titleFrom, c.titleTo).takeIf { c.titleFrom != c.titleTo },
                MetaLineUi(MetaField.AUTHOR, c.authorFrom, c.authorTo).takeIf { c.authorFrom != c.authorTo },
                MetaLineUi(MetaField.YEAR, c.yearFrom, c.yearTo).takeIf { c.yearFrom != c.yearTo },
            )
            return SyncCardUi(c.key, c.songId, c.title, c.kind, choice, meta = meta)
        }
        val (lines, more) = diffs.getOrPut("$at:${c.key}") {
            val all = LineDiff.changes(c.before, c.after).map { DiffLineUi(it.kind, it.text) }
            all.take(MAX_LINES) to (all.size - MAX_LINES).coerceAtLeast(0)
        }
        return SyncCardUi(c.key, c.songId, c.title, c.kind, choice, lines, more)
    }

    private companion object { const val MAX_LINES = 14 }
}
