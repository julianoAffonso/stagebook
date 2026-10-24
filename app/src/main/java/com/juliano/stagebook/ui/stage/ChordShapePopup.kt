package com.juliano.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.juliano.stagebook.R
import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.domain.Voicings
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ChordDiagram
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Como montar o acorde tocado no palco (como no Cifra Club): o desenho no instrumento dos ajustes, as variações
 * com ‹ ›, e OK guarda a variação na música ({define} do ChordPro). Abre na variação já guardada, se houver.
 */
@Composable
fun ChordShapePopup(chord: String, instrument: Instrument, songText: String, onSave: (define: String) -> Unit, onDismiss: () -> Unit) {
    val keyboard = instrument == Instrument.TECLADO
    val shapes = remember(chord, instrument, songText) {
        val generated = Voicings.shapes(chord, instrument)
        val saved = Voicings.savedShape(songText, instrument, chord)
        if (saved != null && saved !in generated) listOf(saved) + generated else generated
    }
    val keySets = remember(chord, songText) {
        val generated = Voicings.keys(chord)
        val saved = Voicings.savedKeys(songText, chord)
        if (saved != null && saved !in generated) listOf(saved) + generated else generated
    }
    val count = if (keyboard) keySets.size else shapes.size
    val start = remember(shapes, keySets) {
        if (keyboard) Voicings.savedKeys(songText, chord)?.let(keySets::indexOf) ?: 0
        else Voicings.savedShape(songText, instrument, chord)?.let(shapes::indexOf) ?: 0
    }
    var i by rememberSaveable(chord, instrument) { mutableIntStateOf(start.coerceAtLeast(0)) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (count == 0) {
                    Text(stringResource(R.string.chord_shape_none, chord), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                    return@Column
                }
                ChordDiagram(
                    chord,
                    instrument,
                    shapes.getOrNull(i),
                    keySets.getOrNull(i),
                    MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { i = (i - 1 + count) % count }, enabled = count > 1) {
                        Icon(AppIcons.ChevronLeft, stringResource(R.string.chord_shape_previous))
                    }
                    Text("${i + 1}/$count", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { i = (i + 1) % count }, enabled = count > 1) {
                        Icon(AppIcons.ChevronRight, stringResource(R.string.chord_shape_next))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                    TextButton(onClick = {
                        onSave(if (keyboard) Voicings.defineKeys(chord, keySets[i]) else Voicings.define(instrument, chord, shapes[i]))
                        onDismiss()
                    }) { Text(stringResource(R.string.chord_shape_keep)) }
                }
            }
        }
    }
}
