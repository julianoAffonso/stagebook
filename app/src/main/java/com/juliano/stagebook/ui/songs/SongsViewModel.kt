package com.juliano.stagebook.ui.songs

import com.juliano.stagebook.data.Connectivity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

@Immutable
data class SongRowUi(val id: String, val title: String, val subtitle: String)

@Immutable
data class SongsUiState(
    val loading: Boolean = true,
    val songs: List<SongRowUi> = emptyList(),
    val total: Int = 0,
)

/** Espera a pessoa parar de digitar antes de filtrar; limpar a busca é na hora. */
fun typingDelay(q: String): Long = if (q.isEmpty()) 0L else 150L

fun Song.subtitle(): String = listOf(author, year).filter { it.isNotBlank() }.joinToString(" · ") { it.trim() }

@HiltViewModel
class SongsViewModel @Inject constructor(private val repository: LibraryRepository, connectivity: Connectivity) : ViewModel() {
    /** Importar precisa de internet: o botão fica indisponível sem ela (só observa com a tela aberta). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val query = MutableStateFlow("")
    private val search = repository.songSearch

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<SongsUiState> = combine(repository.loaded, search, query.debounce(::typingDelay)) { loaded, s, q ->
        val songs = s.find(q)
        SongsUiState(
            loading = !loaded,
            songs = songs.map { SongRowUi(it.id, it.title, it.subtitle()) },
            total = repository.library.value.songs.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    fun onQueryChange(q: String) { query.value = q }

    fun delete(id: String) = repository.deleteSong(id)
}
