package br.app.stagebook.ui.editor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OptionDropdown(
                    stringResource(R.string.song_midi_kind), MidiKind.entries, m.kind, { onChange(m.copy(kind = it)) },
                    { stringResource(kindName(it)) }, Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(AppIcons.Delete, stringResource(R.string.song_midi_remove), tint = MaterialTheme.colorScheme.error) }
            }
            OptionDropdown(
                stringResource(R.string.song_midi_channel), (1..MidiBytes.CHANNELS).toList(), m.channel.coerceIn(1, MidiBytes.CHANNELS),
                { onChange(m.copy(channel = it)) }, { it.toString() }, Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                when (m.kind) {
                    MidiKind.PROGRAM -> NumberField(R.string.song_midi_program, m.number, 1, MidiBytes.PROGRAMS, Modifier.weight(1f)) { onChange(m.copy(number = it)) }
                    MidiKind.CONTROL -> {
                        NumberField(R.string.song_midi_controller, m.number, 0, MidiBytes.MAX_DATA, Modifier.weight(1f)) { onChange(m.copy(number = it)) }
                        NumberField(R.string.song_midi_value, m.value, 0, MidiBytes.MAX_DATA, Modifier.weight(1f)) { onChange(m.copy(value = it)) }
                    }
                    MidiKind.NOTE -> {
                        NumberField(R.string.song_midi_note, m.number, 0, MidiBytes.MAX_DATA, Modifier.weight(1f)) { onChange(m.copy(number = it)) }
                        NumberField(R.string.song_midi_velocity, m.value, 1, MidiBytes.MAX_DATA, Modifier.weight(1f)) { onChange(m.copy(value = it)) }
                    }
                    MidiKind.BANK_PROGRAM -> {
                        NumberField(R.string.song_midi_bank, m.bank, 0, MidiBytes.MAX_BANK, Modifier.weight(1f)) { onChange(m.copy(bank = it)) }
                        NumberField(R.string.song_midi_program, m.number, 1, MidiBytes.PROGRAMS, Modifier.weight(1f)) { onChange(m.copy(number = it)) }
                    }
                }
            }
        }
    }
}

private fun kindName(k: MidiKind) = when (k) {
    MidiKind.PROGRAM -> R.string.song_midi_kind_program
    MidiKind.CONTROL -> R.string.song_midi_kind_control
    MidiKind.NOTE -> R.string.song_midi_kind_note
    MidiKind.BANK_PROGRAM -> R.string.song_midi_kind_bank
}

/** A number held to [min]..[max] as it's typed; the label says the range. */
@Composable
private fun NumberField(label: Int, value: Int, min: Int, max: Int, modifier: Modifier = Modifier, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.coerceIn(min, max).toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(max.toString().length)
            text = digits
            digits.toIntOrNull()?.let { onValue(it.coerceIn(min, max)); if (it > max) text = max.toString() }
        },
        // the range in the label: a supporting line under each field looped the dialog's layout on a phone
        label = { Text(stringResource(R.string.song_midi_range, stringResource(label), min, max), maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

private val sample = SongMidi(enter = listOf(MidiMessage(MidiKind.CONTROL, channel = 2, number = 7, value = 100)))

@LightPreview
@Composable
private fun MidiDialogLightPreview() = StagebookTheme(darkTheme = false) { MidiDialog(sample, {}, {}) }

@DarkPreview
@Composable
private fun MidiDialogDarkPreview() = StagebookTheme(darkTheme = true) { MidiDialog(sample, {}, {}) }
