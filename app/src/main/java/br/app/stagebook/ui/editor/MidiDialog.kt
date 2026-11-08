package br.app.stagebook.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.domain.MidiBytes
import br.app.stagebook.domain.MidiKind
import br.app.stagebook.domain.MidiMessage
import br.app.stagebook.domain.SongMidi
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.Choice
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.OptionDropdown
import br.app.stagebook.ui.components.SegmentedChoice
import br.app.stagebook.ui.theme.Spacing
import br.app.stagebook.ui.theme.StagebookTheme

/**
 * The song's MIDI (user, 07/11): what it sends when it shows on stage and when the stage leaves it. Type and channel
 * from lists and numbers held to MIDI's ranges, so nothing outside the standard can be written.
 */
@Composable
fun MidiDialog(midi: SongMidi, onDone: (SongMidi) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) { MidiPanel(midi, onDone, onDismiss) }
}

/** The window's content, apart from the window (the screenshots draw it on its own). */
@Composable
fun MidiPanel(midi: SongMidi, onDone: (SongMidi) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(midi) }
    var leaving by remember { mutableStateOf(false) }
    val list = if (leaving) draft.exit else draft.enter
    fun set(l: List<MidiMessage>) { draft = if (leaving) draft.copy(exit = l) else draft.copy(enter = l) }
    run {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.song_midi_title), style = MaterialTheme.typography.headlineSmall)
                SegmentedChoice(listOf(Choice(false, R.string.song_midi_enter), Choice(true, R.string.song_midi_exit)), leaving, { leaving = it })
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    if (list.isEmpty()) item { Text(stringResource(R.string.song_midi_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(list.size) { i ->
                        MidiMessageCard(list[i], { set(list.toMutableList().also { l -> l[i] = it }) }, { set(list.filterIndexed { j, _ -> j != i }) })
                    }
                    item { ActionButton(stringResource(R.string.song_midi_add), { set(list + MidiMessage()) }, icon = AppIcons.Add, tone = ButtonTone.Tonal) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onDone(draft) }) { Text(stringResource(R.string.action_ok)) }
                }
            }
        }
    }
}

@Composable
private fun MidiMessageCard(m: MidiMessage, onChange: (MidiMessage) -> Unit, onRemove: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // the type gets the whole width and wraps: "Bank and program (CC0/CC32 + PC)" lost its end on a phone
            OptionDropdown(
                stringResource(R.string.song_midi_kind), MidiKind.entries, m.kind, { onChange(m.copy(kind = it)) },
                { stringResource(kindName(it)) }, Modifier.fillMaxWidth(), singleLine = false,
            )
            // Remove sits by the channel, a short number, so it takes no width from the type
            Row(verticalAlignment = Alignment.CenterVertically) {
                OptionDropdown(
                    stringResource(R.string.song_midi_channel), (1..MidiBytes.CHANNELS).toList(), m.channel.coerceIn(1, MidiBytes.CHANNELS),
                    { onChange(m.copy(channel = it)) }, { it.toString() }, Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(AppIcons.Delete, stringResource(R.string.song_midi_remove), tint = MaterialTheme.colorScheme.error) }
            }
            NumberFields(
                when (m.kind) {
                    MidiKind.PROGRAM -> listOf(NumberSpec(R.string.song_midi_program, m.number, 1, MidiBytes.PROGRAMS) { onChange(m.copy(number = it)) })
                    MidiKind.CONTROL -> listOf(
                        NumberSpec(R.string.song_midi_controller, m.number, 0, MidiBytes.MAX_DATA) { onChange(m.copy(number = it)) },
                        NumberSpec(R.string.song_midi_value, m.value, 0, MidiBytes.MAX_DATA) { onChange(m.copy(value = it)) },
                    )
                    MidiKind.NOTE -> listOf(
                        NumberSpec(R.string.song_midi_note, m.number, 0, MidiBytes.MAX_DATA) { onChange(m.copy(number = it)) },
                        NumberSpec(R.string.song_midi_velocity, m.value, 1, MidiBytes.MAX_DATA) { onChange(m.copy(value = it)) },
                    )
                    MidiKind.BANK_PROGRAM -> listOf(
                        NumberSpec(R.string.song_midi_bank, m.bank, 0, MidiBytes.MAX_BANK) { onChange(m.copy(bank = it)) },
                        NumberSpec(R.string.song_midi_program, m.number, 1, MidiBytes.PROGRAMS) { onChange(m.copy(number = it)) },
                    )
                },
            )
        }
    }
}

private class NumberSpec(val label: Int, val value: Int, val min: Int, val max: Int, val onValue: (Int) -> Unit)

/**
 * The message's numbers side by side when every label fits whole, one under the other when not: on a phone "Bank,
 * 0–16383" and "Program, 1–128" side by side were cut to "Bank," and "Program," (S26, 07/11).
 */
@Composable
private fun NumberFields(specs: List<NumberSpec>) {
    val labels = specs.map { stringResource(R.string.song_midi_range, stringResource(it.label), it.min, it.max) }
    val measurer = rememberTextMeasurer()
    // measured at the size the label has inside an empty field, the larger of its two sizes
    val style = MaterialTheme.typography.bodyLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val need = labels.sumOf { measurer.measure(it, style, maxLines = 1).size.width } +
            with(density) { (FieldChrome * specs.size + Spacing.s * (specs.size - 1)).roundToPx() }
        if (need <= constraints.maxWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                specs.forEachIndexed { i, f -> NumberField(labels[i], f, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                specs.forEachIndexed { i, f -> NumberField(labels[i], f, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** An outlined field's room around its label: 16 dp on each side plus the 4 dp gaps of the outline around it. */
private val FieldChrome = 40.dp

private fun kindName(k: MidiKind) = when (k) {
    MidiKind.PROGRAM -> R.string.song_midi_kind_program
    MidiKind.CONTROL -> R.string.song_midi_kind_control
    MidiKind.NOTE -> R.string.song_midi_kind_note
    MidiKind.BANK_PROGRAM -> R.string.song_midi_kind_bank
}

/** A number held to its range as it's typed; the label says the range. */
@Composable
private fun NumberField(label: String, f: NumberSpec, modifier: Modifier = Modifier) {
    val min = f.min
    val max = f.max
    var text by remember(f.value) { mutableStateOf(f.value.coerceIn(min, max).toString()) }
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(max.toString().length)
            text = digits
            digits.toIntOrNull()?.let { f.onValue(it.coerceIn(min, max)); if (it > max) text = max.toString() }
        },
        // the range in the label: a supporting line under each field looped the dialog's layout on a phone
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
        // leaving the field, it shows what is kept: "0" in a field that starts at 1 saved 1 but kept showing 0 (audit 07/11)
        modifier = modifier.onFocusChanged { if (focused && !it.isFocused) text = f.value.coerceIn(min, max).toString(); focused = it.isFocused },
    )
}

private val sample = SongMidi(enter = listOf(MidiMessage(MidiKind.CONTROL, channel = 2, number = 7, value = 100)))

@LightPreview
@Composable
private fun MidiDialogLightPreview() = StagebookTheme(darkTheme = false) { MidiDialog(sample, {}, {}) }

@DarkPreview
@Composable
private fun MidiDialogDarkPreview() = StagebookTheme(darkTheme = true) { MidiDialog(sample, {}, {}) }
