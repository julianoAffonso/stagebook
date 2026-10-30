package br.app.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import br.app.stagebook.domain.HarmonicField
import br.app.stagebook.ui.theme.Sizes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import br.app.stagebook.domain.Chords
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import br.app.stagebook.R
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ChordDiagram
import br.app.stagebook.ui.theme.Spacing

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
    extras: Boolean = true,   // modo essencial (T19): sem o som e sem os Detalhes; o desenho e as variações ficam
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
    // "Detalhes": a segunda tela da janela, com o campo harmônico (T21); o desenho fica na primeira, sem distrair
    var details by rememberSaveable(chord) { mutableStateOf(false) }
    val songKey = remember(songText, instrument) {
        val k = Chords.key(songText).ifEmpty { Chords.detectKey(songText) }
        val kc = if (instrument == Instrument.TECLADO) Chords.capo(songText) else 0
        if (k.isNotEmpty() && kc > 0) Chords.transposeKey(k, kc) else k
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (details) {
                    ChordDetails(chord, songKey, onBack = { details = false })
                    return@Column
                }
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
                    if (extras) Surface(
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
                    if (extras) TextButton(onClick = { details = true }) { Text(stringResource(R.string.chord_details)) }
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

/** Segunda tela da janela do acorde: o campo harmônico do tom da música (com o grau do acorde), o dele e onde mais aparece. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChordDetails(chord: String, songKey: String, onBack: () -> Unit) {
    val triad = remember(chord) { Chords.triad(chord) }
    val own = triad?.takeIf { !it.dim }?.let { Chords.keyName(it.root, it.minor) }
    // com sétima, os campos são de tétrades (G7M, Am7…) e o grau só acende se a sétima bater
    val sevenths = triad != null && triad.seventh != Chords.Seventh.NONE
    val place = remember(chord, songKey) { if (songKey.isEmpty()) null else HarmonicField.placeIn(chord, songKey) }
    val degree = place?.label
    val where = remember(chord) { HarmonicField.appearances(chord) }
    Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, stringResource(R.string.chord_back_to_shape)) }
            Text(stringResource(R.string.chord_details_title, chord), style = MaterialTheme.typography.titleLarge)
        }
        if (songKey.isNotEmpty()) {
            Text(stringResource(R.string.chord_details_song_key, songKey), style = MaterialTheme.typography.titleSmall)
            FieldRow(HarmonicField.field(songKey, sevenths), lit = place?.index)
            Text(
                if (degree != null) stringResource(R.string.chord_details_degree, chord, degree) else stringResource(R.string.chord_details_outside, chord),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // o campo em que o acorde é o I; um acorde que nunca é o I (ex.: D7, dominante) ganha uma frase no lugar
        val ownPlace = own?.let { HarmonicField.placeIn(chord, it) }?.takeIf { it.index == 0 }
        if (own != null && ownPlace != null) {
            Text(stringResource(R.string.chord_details_own, own), style = MaterialTheme.typography.titleSmall)
            FieldRow(HarmonicField.field(own, sevenths), lit = 0)
        } else if (own != null) {
            Text(stringResource(R.string.chord_details_not_tonic, chord), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (where.isNotEmpty()) {
            Text(stringResource(R.string.chord_details_where, chord), style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                where.forEach { a ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(stringResource(R.string.chord_details_in, a.roman, a.key), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Os 7 graus de um campo (grau em cima, acorde embaixo); o da posição [lit] fica aceso. */
@Composable
private fun FieldRow(degrees: List<HarmonicField.Degree>, lit: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        degrees.forEachIndexed { i, d ->
            val on = i == lit
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.roman, style = MaterialTheme.typography.labelSmall)
                    Text(d.chord, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
