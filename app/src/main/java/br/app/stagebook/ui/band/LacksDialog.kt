package br.app.stagebook.ui.band

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface

/**
 * The leader's "What's missing": each following device and the songs it lacks, with the action that sends them to it
 * ([onSend] with its id) or to every device listed (null). A device leaves the list when it says it has the songs.
 */
@Composable
fun LacksDialog(lackers: List<ConductUi.Lacker>, sending: Boolean, onSend: (String?) -> Unit, onDismiss: () -> Unit) {
    br.app.stagebook.ui.components.FollowBusyWhile()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conduct_lacks_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (lackers.isEmpty()) Text(stringResource(R.string.conduct_lacks_none), style = MaterialTheme.typography.bodyLarge)
                lackers.forEach { lacker ->
                    Text(lacker.who, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    LackRows(lacker.songs)
                    TextButton({ onSend(lacker.id) }, enabled = !sending) {
                        // a single song has its own sentence, with no number
                        val count = lacker.titles.size
                        Text(if (count == 1) stringResource(R.string.conduct_send_one) else pluralStringResource(R.plurals.conduct_send_lacks, count, count))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = if (lackers.isEmpty()) null else ({ TextButton({ onSend(null) }, enabled = !sending) { Text(stringResource(R.string.conduct_send_all)) } }),
    )
}

/** The artist of each missing title, from the leader's setlist, which is what both ends know about a song that isn't here. */
internal fun lackSongs(titles: List<String>, items: List<br.app.stagebook.domain.Conduct.Item>) =
    titles.map { t -> ConductUi.LackSong(t, items.firstOrNull { it.title == t }?.artist.orEmpty()) }

/**
 * Missing songs as the setlist's rows, with no number and none of the facts: the song isn't here, so the key, the tempo
 * and the capo aren't known (the leader's message carries only the title and the artist).
 */
@Composable
internal fun LackRows(songs: List<ConductUi.LackSong>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(br.app.stagebook.ui.theme.Spacing.xs)) {
        songs.forEach { s ->
            br.app.stagebook.ui.components.SongListItem(
                title = s.title, subtitle = s.artist, onClick = null,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                gap = br.app.stagebook.ui.theme.Spacing.s, titleLines = 1, dense = true,
                titleStyle = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@LightPreview
@Composable
private fun LacksDialogLightPreview() = PreviewSurface(dark = false) { LacksDialog(sampleLackers, false, {}, {}) }

@DarkPreview
@Composable
private fun LacksDialogDarkPreview() = PreviewSurface(dark = true) { LacksDialog(sampleLackers, false, {}, {}) }
