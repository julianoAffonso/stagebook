package br.app.stagebook.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * For a read-only text field that acts like a button (track, tags, the song summary): the field takes no click, so the
 * tap arrives through its interaction source.
 */
@Composable
fun rememberTapSource(onTap: () -> Unit): MutableInteractionSource {
    val taps = remember { MutableInteractionSource() }
    val tap by rememberUpdatedState(onTap)
    LaunchedEffect(taps) { taps.interactions.collect { if (it is PressInteraction.Release) tap() } }
    return taps
}
