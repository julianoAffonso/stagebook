package br.app.stagebook.ui.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.data.model.SiteKind

/**
 * The automatic profile shows each song its own way, so the importer can't tell what is coming in: lyrics or a chord
 * chart. Each opens on its own list of sites.
 */
@Composable
fun ImportKindDialog(onPick: (SiteKind) -> Unit, onDismiss: () -> Unit) {
    br.app.stagebook.ui.components.FollowBusyWhile()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_kind_title)) },
        text = {
            Column {
                listOf(SiteKind.LYRICS to R.string.import_kind_lyrics, SiteKind.CHORDS to R.string.import_kind_chords).forEach { (kind, name) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(kind) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(name), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
