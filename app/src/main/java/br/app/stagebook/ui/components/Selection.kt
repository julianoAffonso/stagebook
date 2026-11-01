package br.app.stagebook.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * A list's selection mode (Google's pattern: holding an item starts it, back or ✕ ends it), the same on the songs, the
 * setlists and a setlist's songs. [state] holds the chosen ids, in the order they were chosen.
 */
@Stable
class Selection(val state: MutableState<List<String>>) {
    val active: Boolean get() = state.value.isNotEmpty()

    fun toggle(id: String) { state.value = state.value.let { if (id in it) it - id else it + id } }
}

/** The selection, kept across rotation; back ends it while it's on. */
@Composable
fun rememberSelection(initial: List<String> = emptyList()): Selection {
    val selection = Selection(rememberSaveable { mutableStateOf(initial) })
    BackHandler(enabled = selection.active) { selection.state.value = emptyList() }
    return selection
}
