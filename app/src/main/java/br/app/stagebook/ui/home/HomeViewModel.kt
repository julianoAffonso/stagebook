package br.app.stagebook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.anyOff
import br.app.stagebook.data.model.essential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** O que o Início precisa além da caixa da banda: o aviso do modo essencial (T19). */
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    val essential: StateFlow<Boolean> = repository.library.map { it.look.essential }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.essential)

    /** Aviso ao abrir com alguma parte desligada (o que está desligado vem no Look; null = sem aviso): uma vez por abertura, até "Não mostrar de novo". */
    val alert: StateFlow<Look?> = combine(repository.library, alerted) { lib, seen -> lib.look.takeIf { it.anyOff && !it.essentialAlertOff && !seen } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun alertSeen(neverAgain: Boolean) {
        alerted.value = true
        if (neverAgain) repository.updateLook { it.copy(essentialAlertOff = true) }
    }

    private companion object {
        val alerted = MutableStateFlow(false)   // por abertura do app (processo): voltar ao Início não repete
    }

    /** "Sincronizar com a banda" usa a rede local e a internet: some com as duas desligadas. */
    val canSync: StateFlow<Boolean> = repository.library.map { it.look.bandOn || it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
