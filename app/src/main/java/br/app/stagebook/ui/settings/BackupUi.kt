package br.app.stagebook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import br.app.stagebook.R
import br.app.stagebook.data.Backup
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.theme.Spacing

/**
 * Choosing the backup parts (save or restore): all checked by default, the user unchecks what they don't want.
 * [available]: the ones the file has (on restore); [warning]: the notice that it replaces what's on the device.
 */
@Composable
fun BackupPartsDialog(
    title: String,
    confirmLabel: String,
    available: Set<Backup.Part>,
    onConfirm: (parts: Set<Backup.Part>, replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
    warning: String? = null,
) {
    var chosen by rememberSaveable { mutableStateOf(available.map { it.name }.toSet()) }
    // songs in the file: add to the ones here (default, safe) or replace
    var replace by rememberSaveable { mutableStateOf(false) }
    br.app.stagebook.ui.components.FollowBusyWhile()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                BackupPartsChecks(available, Backup.Part.entries.filter { it.name in chosen }.toSet()) { part, on ->
                    chosen = if (on) chosen + part.name else chosen - part.name
                }
                if (Backup.Part.LIBRARY.name in chosen) {
                    Text(stringResource(R.string.backup_songs_how), style = MaterialTheme.typography.bodyLarge)
                    val options = listOf(false to R.string.backup_merge, true to R.string.backup_replace)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (r, label) ->
                            SegmentedButton(
                                selected = replace == r,
                                onClick = { replace = r },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = CircleShape),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Text(
                        stringResource(if (replace) R.string.backup_replace_help else R.string.backup_merge_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (replace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (Backup.Part.SETTINGS.name in chosen) warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Backup.Part.entries.filter { it.name in chosen }.toSet(), replace) }, enabled = chosen.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The checkboxes for each backup part (on the backup card and in the restore window). */
@Composable
fun BackupPartsChecks(available: Set<Backup.Part>, chosen: Set<Backup.Part>, onChange: (Backup.Part, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Backup.Part.entries.filter { it in available }.forEach { part ->
            val on = part in chosen
            Row(
                Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { onChange(part, it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Column(Modifier.padding(start = Spacing.s)) {
                    Text(stringResource(part.label()), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(part.detail()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun Backup.Part.label() = when (this) {
    Backup.Part.LIBRARY -> R.string.backup_part_library
    Backup.Part.TRACKS -> R.string.backup_part_tracks
    Backup.Part.SETTINGS -> R.string.backup_part_settings
}

private fun Backup.Part.detail() = when (this) {
    Backup.Part.LIBRARY -> R.string.backup_part_library_detail
    Backup.Part.TRACKS -> R.string.backup_part_tracks_detail
    Backup.Part.SETTINGS -> R.string.backup_part_settings_detail
}

@LightPreview
@Composable
private fun BackupPartsLightPreview() = PreviewSurface(dark = false) {
    BackupPartsDialog("Abrir arquivo", "Abrir", Backup.Part.entries.toSet(), { _, _ -> }, {})
}

@DarkPreview
@Composable
private fun BackupPartsDarkPreview() = PreviewSurface(dark = true) {
    BackupPartsDialog("Abrir arquivo", "Abrir", Backup.Part.entries.toSet(), { _, _ -> }, {}, warning = "Os ajustes do arquivo substituem os daqui.")
}
