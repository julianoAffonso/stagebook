package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.data.VersionNotes
import br.app.stagebook.ui.theme.Spacing

/** What's new, in the app's language: one version under "What's new", several under their numbers. */
@Composable
fun ReleaseNotes(notes: List<VersionNotes>, modifier: Modifier = Modifier) {
    val portuguese = LocalConfiguration.current.locales[0].language == "pt"
    val versions = notes.map { it.versionName to if (portuguese) it.pt else it.en }.filter { it.second.isNotEmpty() }
    if (versions.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        versions.forEach { (name, items) ->
            Text(
                if (versions.size == 1) stringResource(R.string.update_notes_title) else stringResource(R.string.update_version, name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items.forEach { item ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private val sample = listOf(VersionNotes(733, "0.1.733", en = listOf("Saving a song opens it on stage."), pt = listOf("Salvar uma música agora abre ela no palco.")))

@LightPreview
@Composable
private fun ReleaseNotesLightPreview() = PreviewSurface(dark = false) { ReleaseNotes(sample) }

@DarkPreview
@Composable
private fun ReleaseNotesDarkPreview() = PreviewSurface(dark = true) { ReleaseNotes(sample) }
