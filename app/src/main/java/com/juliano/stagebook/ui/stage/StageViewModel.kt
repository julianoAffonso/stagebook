package com.juliano.stagebook.ui.stage

import com.juliano.stagebook.domain.Chords
import com.juliano.stagebook.domain.Voicings
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.StageGate
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.navigation.Stage
import com.juliano.stagebook.ui.songs.subtitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class StageSong(val title: String, val meta: String, val text: String, val missing: Boolean, val id: String = "", val zoom: Float = 1f)

@Immutable
data class StageUiState(
    val loading: Boolean = true,
    val songs: List<StageSong> = emptyList(),
    val look: Look = Look(),
)

@HiltViewModel
class StageViewModel @Inject constructor(
    private val repository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
    private val stageGate: StageGate,
) : ViewModel() {
    init { stageGate.enter() }   // segura a sincronia com o Drive enquanto o palco está aberto
    override fun onCleared() = stageGate.leave()
    private val route = savedStateHandle.toRoute<Stage>()
    /** Música em que o palco abre (posição no setlist). */
    val start: Int = route.start

    val uiState: StateFlow<StageUiState> = combine(repository.loaded, repository.library) { loaded, lib ->
        val set = route.setId?.let(lib::setlist)
        val ids = set?.items ?: listOfNotNull(route.songId)
        StageUiState(
            loading = !loaded,
            songs = ids.map { id ->
                val s = lib.song(id)
                StageSong(s?.title.orEmpty(), s?.subtitle().orEmpty(), s?.text.orEmpty(), s == null, id, s?.zoom ?: 1f)
            },
            look = lib.look,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StageUiState())

    /** Variação do acorde escolhida no palco: vai na música como {define} do ChordPro (troca a anterior). */
    fun saveShape(songId: String, chord: String, define: String) {
        if (songId.isEmpty()) return
        val instrument = repository.library.value.look.instrument
        repository.updateSong(songId) { it.copy(text = Voicings.saveDefine(it.text, instrument, chord, define)) }
    }

    /** Acordes tirados da lista de acordes desta música (o × no palco); vazio devolve todos. */
    fun setHiddenChords(songId: String, hidden: Set<String>) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = Chords.setHiddenChords(it.text, hidden)) }
    }

    /** Zoom da pinça, gravado na música (vale em qualquer setlist, como no another app). */
    fun setZoom(songId: String, zoom: Float) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(zoom = zoom) }
    }
}
