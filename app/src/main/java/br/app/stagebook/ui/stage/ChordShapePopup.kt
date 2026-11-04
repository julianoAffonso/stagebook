package br.app.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import br.app.stagebook.domain.HarmonicField
import br.app.stagebook.ui.theme.Sizes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import br.app.stagebook.domain.Chords
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.app.stagebook.R
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ChordDiagram
import br.app.stagebook.ui.components.HelpIcon
import br.app.stagebook.ui.components.wideLists
import br.app.stagebook.ui.theme.Spacing

/**
 * How to play the chord tapped on stage (like Cifra Club): the diagram for the instrument in settings, the shapes with
 * ‹ ›, and OK saves the shape in the song (ChordPro {define}). Opens on the saved shape, if there is one.
 */
@Composable
fun ChordShapePopup(
    chord: String,
    instrument: Instrument,
    songText: String,
    onSave: (define: String) -> Unit,
    onDismiss: () -> Unit,
    onPlay: (notes: List<Int>) -> Unit = {},
    extras: Boolean = true,   // essential mode (T19): no sound and no Details; the diagram and shapes stay
) {
    // with a capo, the diagram is the chart's but the sounding note goes up (T18/T20): the sound plays the real note
    val capo = remember(songText, instrument) { if (instrument == Instrument.KEYBOARD) 0 else Chords.capo(songText) }   // keyboard doesn't use capo
    val keyboard = instrument == Instrument.KEYBOARD
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
    // "Details": the window's second screen, with the harmonic field (T21); the diagram stays on the first one, without
    // distractions
    var details by rememberSaveable(chord) { mutableStateOf(false) }
    // the chord with the capo, spelled in the key that sounds: the same name here, in Details and on the stage
    val soundingKey = remember(songText) { Chords.soundingKey(Chords.key(songText), Chords.capo(songText)) }
    // Details on an upright phone take the whole width: the field fits in fewer rows
    val window = LocalWindowInfo.current.containerSize
    val fullWidth = details && !wideLists() && window.height > window.width
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !fullWidth)) {
        Surface(Modifier.testTag("chord_window").then(if (fullWidth) Modifier.fillMaxWidth() else Modifier), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            // a phone on its side is shorter than Details: it scrolls
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (details) {
                    // the harmonic field is of what sounds, not of the chart's shapes (on the keyboard the chord already
                    // sounds); only here: most taps never open Details, and a song with no key has to be read for one
                    val songKey = remember(songText) { soundingKey.ifEmpty { Chords.soundingKey(Chords.detectKey(songText), Chords.capo(songText)) } }
                    val sounding = if (capo > 0) Chords.transposeChord(chord, capo, key = songKey) else chord
                    val capoNote = if (capo > 0) stringResource(R.string.chord_capo_note, chord, capo, sounding) else null
                    ChordDetails(sounding, songKey, capoNote, fullWidth, onBack = { details = false })
                    return@Column
                }
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
                // play on the variations line, left of the arrows (user, 01/11: on top of the diagram it covered the
                // frets); still a big, full-colour button, close to the thumb
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (extras) FilledIconButton(
                        onClick = { onPlay(if (keyboard) keySets[i].map { it + capo } else Voicings.midi(shapes[i], instrument, capo)) },
                        modifier = Modifier.size(Sizes.touch),
                    ) {
                        Icon(AppIcons.PlayRounded, stringResource(R.string.chord_play))
                    }
                    IconButton(onClick = { i = (i - 1 + count) % count }, enabled = count > 1) {
                        Icon(AppIcons.ChevronLeft, stringResource(R.string.chord_shape_previous))
                    }
                    Text("${i + 1}/$count", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { i = (i + 1) % count }, enabled = count > 1) {
                        Icon(AppIcons.ChevronRight, stringResource(R.string.chord_shape_next))
                    }
                }
                // keyboard: the chord on screen is already in the sounding key; the line shows the chart's (capo on
                // fret N)
                val chartCapo = remember(songText, instrument) { if (instrument == Instrument.KEYBOARD) Chords.capo(songText) else 0 }
                if (chartCapo > 0) {
                    Text(
                        stringResource(R.string.chord_keyboard_capo, Chords.transposeChord(chord, -chartCapo), chartCapo, chord),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (capo > 0) {
                    Text(
                        stringResource(R.string.chord_capo_note, chord, capo, Chords.transposeChord(chord, capo, key = soundingKey)),
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


/**
 * Second screen of the chord window: the harmonic field of the song's key (with the chord's degree), the chord's own
 * field and where else it shows up.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChordDetails(chord: String, songKey: String, capoNote: String?, fullWidth: Boolean, onBack: () -> Unit) {
    val triad = remember(chord) { Chords.triad(chord) }
    val own = triad?.takeIf { !it.dim }?.let { Chords.keyName(it.root, it.minor) }
    // with a seventh, the fields use seventh chords (Gmaj7, Am7…) and the degree only lights if the seventh matches
    val sevenths = triad != null && triad.seventh != Chords.Seventh.NONE
    val place = remember(chord, songKey) { if (songKey.isEmpty()) null else HarmonicField.placeIn(chord, songKey) }
    val degree = place?.label
    val where = remember(chord) { HarmonicField.appearances(chord) }
    Column(if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // the note belongs to the title: right under it, not one section away
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, stringResource(R.string.chord_back_to_shape)) }
                Text(stringResource(R.string.chord_details_title, chord), style = MaterialTheme.typography.titleLarge)
            }
            if (capoNote != null) CapoNote(capoNote)
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
        // the field where the chord is the I; a chord that is never the I (like D7, a dominant) gets a sentence instead
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

/** Why Details name another chord than the one tapped: the capo. First on the screen, so the name isn't taken for a mistake. */
@Composable
private fun CapoNote(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(start = Spacing.s, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Capo, null, tint = MaterialTheme.colorScheme.primary)
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            HelpIcon(stringResource(R.string.chord_capo_help))
        }
    }
}

/** The 7 degrees of a field (degree on top, chord below); the one at position [lit] is lit. Long chords go to a second row, centered so a lone one doesn't look lost. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FieldRow(degrees: List<HarmonicField.Degree>, lit: Int?) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        degrees.forEachIndexed { i, d ->
            val on = i == lit
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.roman, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    Text(d.chord, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
