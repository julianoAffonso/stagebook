package br.app.stagebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Spacing

/** The song's key in a small badge, at the end of its row. */
@Composable
fun SongKeyBadge(key: String, modifier: Modifier = Modifier) {
    Text(
        key,
        modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(horizontal = Spacing.s),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
    )
}

/**
 * What the musician checks before the song starts, at the end of its row (the song list and a setlist): the key, and
 * under it the tempo, the capo and a play mark with a backing track. Only what the song has; nothing at all with none.
 */
@Composable
fun SongFacts(songKey: String, bpm: Int, capo: Int, hasTrack: Boolean, modifier: Modifier = Modifier) {
    if (songKey.isEmpty() && bpm <= 0 && capo <= 0 && !hasTrack) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (songKey.isNotEmpty()) SongKeyBadge(songKey)
        if (bpm > 0 || capo > 0 || hasTrack) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val small = MaterialTheme.typography.labelSmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                if (bpm > 0) Text(stringResource(R.string.set_bpm, bpm), style = small, color = color, maxLines = 1)
                if (capo > 0) Text(stringResource(R.string.editor_data_capo, capo), style = small, color = color, maxLines = 1)
                if (hasTrack) Icon(AppIcons.Play, stringResource(R.string.stage_backing_track), Modifier.size(Spacing.m), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SongFactsSample() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SongListItem("Canção do Vento", "Banda Fictícia", {}, trailing = { SongFacts("Em", 120, 2, true) })
        SongListItem("Um título comprido que não cabe inteiro na linha da lista", "Trio Imaginário", {}, titleLines = 1, trailing = { SongFacts("G", 96, 0, false) })
        SongListItem("Sem tom", "", {}, trailing = { SongFacts("", 0, 3, true) })
        SongListItem("Sem nada", "Banda Fictícia", {}, trailing = { SongFacts("", 0, 0, false) })
    }
}

@LightPreview
@Composable
private fun SongFactsLightPreview() = PreviewSurface(dark = false) { SongFactsSample() }

@DarkPreview
@Composable
private fun SongFactsDarkPreview() = PreviewSurface(dark = true) { SongFactsSample() }
