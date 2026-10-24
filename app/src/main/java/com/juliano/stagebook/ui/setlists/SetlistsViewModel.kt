package com.juliano.stagebook.ui.setlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.data.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.juliano.stagebook.domain.QrPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Immutable
data class SetlistRowUi(val id: String, val name: String, val count: Int)

@Immutable
data class SetlistsUiState(val loading: Boolean = true, val sets: List<SetlistRowUi> = emptyList())

@HiltViewModel
class SetlistsViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    val uiState: StateFlow<SetlistsUiState> = combine(repository.loaded, repository.library) { loaded, lib ->
        SetlistsUiState(!loaded, lib.sets.map { SetlistRowUi(it.id, it.name, it.items.size) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetlistsUiState())

    /** Nome vazio = dia e hora de agora. Devolve o id para abrir o setlist novo. */
    fun create(name: String): String =
        repository.createSetlist(name.ifBlank { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(Date()) })

    fun delete(id: String) = repository.deleteSetlist(id)

    private val _nameLimit = MutableStateFlow(false)
    /** O nome novo faria o setlist passar do limite do QR (mesmo aviso de dentro do setlist). */
    val nameLimit: StateFlow<Boolean> = _nameLimit.asStateFlow()

    fun dismissNameLimit() { _nameLimit.value = false }

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        val lib = repository.library.value
        val set = lib.setlist(id) ?: return
        // o nome também vai no QR: um nome novo que faria o setlist passar do limite não é gravado
        if (QrPayload.fits(set, lib) && !QrPayload.fits(set, lib, name = name)) { _nameLimit.value = true; return }
        repository.renameSetlist(id, name)
    }
}
