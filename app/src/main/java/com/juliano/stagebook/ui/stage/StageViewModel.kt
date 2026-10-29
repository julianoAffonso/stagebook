package com.juliano.stagebook.ui.stage

import com.juliano.stagebook.player.PlayerUi
import com.juliano.stagebook.player.StagePlayer
import com.juliano.stagebook.data.TrackStore
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
data class StageSong(val title: String, val meta: String, val text: String, val missing: Boolean, val id: String = "", val zoom: Float = 1f, val track: String = "")

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
    private val tracks: TrackStore,
    private val player: StagePlayer,
    private val conduct: com.juliano.stagebook.data.BandConduct,
) : ViewModel() {
    /** Música na tela (posição no setlist): conduzindo este setlist, a banda abre a mesma. */
    fun songShown(index: Int) { route.setId?.let { conduct.open(it, index) } }

    /** Tocando (ou esperando a saída USB para tocar) e o aviso do player. */
    val playerUi: StateFlow<PlayerUi> = player.ui

    fun togglePlay() = player.toggle()
    fun needsBatteryAsk() = player.needsBatteryAsk()
    fun batteryAsked() = player.batteryAsked()

    /** Música na tela: o player anota o backing track dela (e para o da anterior). */
    fun showSong(track: String) = player.open(track.takeIf { it.isNotEmpty() }?.let(tracks::file))

    /** Liga a faixa escolhida à música (copiada para o app); a anterior é apagada. Falso se o arquivo não serve. */
    suspend fun attachTrack(songId: String, uri: android.net.Uri): Boolean {
        val name = tracks.copy(songId, uri) ?: return false
        val old = repository.song(songId)?.track.orEmpty()
        repository.updateSong(songId) { it.copy(track = name) }
        if (old != name) tracks.remove(old)
        return true
    }

    fun removeTrack(songId: String) {
        val old = repository.song(songId)?.track.orEmpty()
        repository.updateSong(songId) { it.copy(track = "") }
        player.open(null)
        tracks.remove(old)
    }

    init { stageGate.enter() }   // segura a sincronia com o Drive enquanto o palco está aberto
    override fun onCleared() {
        player.pause()   // sair do palco para o backing track
        stageGate.leave()
        // sair do palco NÃO encerra o "Quem conduz" (pedido do usuário): no show a pessoa sai para conferir algo e a banda
        // continua junto; só o "Encerrar" solta todos
    }
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
                StageSong(s?.title.orEmpty(), s?.subtitle().orEmpty(), s?.text.orEmpty(), s == null, id, s?.zoom ?: 1f, s?.track.orEmpty())
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
    /** Velocidade da rolagem automática (−/+ no palco), gravada nos ajustes. */
    fun setAutoScroll(level: Int) = repository.updateLook { it.copy(autoScroll = level.coerceIn(1, com.juliano.stagebook.data.model.Look.MAX_AUTO_SCROLL)) }

    fun setZoom(songId: String, zoom: Float) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(zoom = zoom) }
    }
}
