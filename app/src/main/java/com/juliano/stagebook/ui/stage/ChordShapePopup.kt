package com.juliano.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import com.juliano.stagebook.ui.theme.Sizes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import com.juliano.stagebook.domain.Chords
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
fun ChordShapePopup(
    chord: String,
    instrument: Instrument,
    songText: String,
    onSave: (define: String) -> Unit,
    onDismiss: () -> Unit,
    onPlay: (notes: List<Int>) -> Unit = {},
) {
    // com capo, o desenho é o da cifra mas a nota que soa sobe (T18/T20): o som toca a nota real
    val capo = remember(songText, instrument) { if (instrument == Instrument.TECLADO) 0 else Chords.capo(songText) }   // teclado não usa capo
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
                // o play fica em cima do desenho, meio translúcido (pedido do usuário, 29/10): tocar o acorde sem procurar botão
                Box(contentAlignment = Alignment.Center) {
                    ChordDiagram(
                        chord,
                        instrument,
                        shapes.getOrNull(i),
                        keySets.getOrNull(i),
                        MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        onClick = { onPlay(if (keyboard) keySets[i].map { it + capo } else Voicings.midi(shapes[i], instrument, capo)) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = PLAY_ALPHA),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(Sizes.touch + Spacing.m),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.PlayRounded, stringResource(R.string.chord_play), Modifier.size(Sizes.touch * 0.7f))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { i = (i - 1 + count) % count }, enabled = count > 1) {
                        Icon(AppIcons.ChevronLeft, stringResource(R.string.chord_shape_previous))
                    }
                    Text("${i + 1}/$count", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { i = (i + 1) % count }, enabled = count > 1) {
                        Icon(AppIcons.ChevronRight, stringResource(R.string.chord_shape_next))
                    }
                }
                // teclado: o acorde na tela já está no tom que soa; a linha mostra o da cifra (capo na Nª casa)
                val cifraCapo = remember(songText, instrument) { if (instrument == Instrument.TECLADO) Chords.capo(songText) else 0 }
                if (cifraCapo > 0) {
                    Text(
                        stringResource(R.string.chord_keyboard_capo, Chords.transposeChord(chord, -cifraCapo), cifraCapo, chord),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (capo > 0) {
                    Text(
                        stringResource(R.string.chord_capo_note, chord, capo, Chords.transposeChord(chord, capo)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
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

private const val PLAY_ALPHA = 0.35f   // o desenho continua aparecendo por baixo do play
