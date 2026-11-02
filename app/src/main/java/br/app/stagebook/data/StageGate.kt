package br.app.stagebook.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Stage open now? With it open (show) there's no sync: no download and no lyrics swap. */
@Singleton
class StageGate @Inject constructor() {
    // counter: going from one stage to another, the new one enters before the old one leaves
    private val open = MutableStateFlow(0)
    private val _onStage = MutableStateFlow(false)
    val onStage: StateFlow<Boolean> = _onStage.asStateFlow()
    // counts the stage exits: entering and leaving quickly isn't lost (onStage would come back the same)
    private val _exits = MutableStateFlow(0)
    val exits: StateFlow<Int> = _exits.asStateFlow()

    fun enter() { open.update { it + 1 }; _onStage.value = open.value > 0 }
    fun leave() {
        open.update { (it - 1).coerceAtLeast(0) }
        _onStage.value = open.value > 0
        if (!_onStage.value) _exits.update { it + 1 }
    }
}
