package com.juliano.stagebook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
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
import com.juliano.stagebook.R
import com.juliano.stagebook.data.Backup
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.PreviewSurface
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Escolher as partes do backup (fazer ou restaurar): todas marcadas por padrão, o usuário tira o que não quer. [available]:
 * as que o arquivo tem (na restauração); [warning]: o aviso de que substitui o que está no aparelho.
 */
@Composable
fun BackupPartsDialog(
    title: String,
    confirmLabel: String,
    available: Set<Backup.Part>,
    onConfirm: (Set<Backup.Part>) -> Unit,
    onDismiss: () -> Unit,
    warning: String? = null,
) {
    var chosen by rememberSaveable { mutableStateOf(available.map { it.name }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Backup.Part.entries.filter { it in available }.forEach { part ->
                    val on = part.name in chosen
                    Row(
                        Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { chosen = if (it) chosen + part.name else chosen - part.name },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Column {
                            Text(stringResource(part.label()), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(part.detail()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                warning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Backup.Part.entries.filter { it.name in chosen }.toSet()) }, enabled = chosen.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
    BackupPartsDialog("Fazer backup", "Salvar", Backup.Part.entries.toSet(), {}, {})
}

@DarkPreview
@Composable
private fun BackupPartsDarkPreview() = PreviewSurface(dark = true) {
    BackupPartsDialog("Restaurar backup", "Restaurar", Backup.Part.entries.toSet(), {}, {}, warning = "Substitui o que está neste aparelho.")
}
