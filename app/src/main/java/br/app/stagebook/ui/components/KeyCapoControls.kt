package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.SongMeta
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import br.app.stagebook.ui.theme.Spacing

/**
 * The song's key and capo, both the same way (user's request, 29/10): − and + on the key transpose a half step
 * (rewriting the chords and the saved key); − and + on the capo change the fret (the chord diagrams stay the same).
 * With no saved key, shows the one computed from the chords, dimmed.
 */
@Composable
fun KeyCapoControls(
    text: String,
    onTranspose: (Int) -> Unit,
    onCapo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showCapo: Boolean = true,
    meta: SongMeta = remember(text) { Chords.meta(text) },
) {
    // reading every chord of the song waits for a pause in typing (the saved key and capo come from [meta] right away;
    // audit 01/11)
    val hasChords by produceState(remember { hasChords(text) }, text) { delay(CHORDS_AFTER_TYPING_MS); value = hasChords(text) }
    val detected by produceState(remember { if (meta.key.isEmpty()) Chords.detectKey(text) else "" }, text, meta.key) {
        if (meta.key.isEmpty()) { delay(CHORDS_AFTER_TYPING_MS); value = Chords.detectKey(text) }
    }
    val saved = meta.key
    val key = saved.ifEmpty { detected }
    val capo = meta.capo
    val keyStepper = @Composable {
        Stepper(
            icon = AppIcons.MusicNote,
            label = stringResource(R.string.editor_key),
            value = key.ifEmpty { "–" },
            dimmed = saved.isEmpty(),
            downLabel = stringResource(R.string.editor_transpose_down),
            upLabel = stringResource(R.string.editor_transpose_up),
            onDown = { onTranspose(-1) },
            onUp = { onTranspose(1) },
            downEnabled = hasChords,
            upEnabled = hasChords,
        )
    }
    // keyboard doesn't use a capo (user's request, 29/10): no controls (the song's capo stays saved)
    val capoStepper = @Composable {
        Stepper(
            icon = AppIcons.Capo,
            label = stringResource(R.string.editor_capo),
            // no capo: a dimmed 0 (a "–" looked like a third minus button). "Fret 2" (user's request, 29/10)
            value = if (capo == 0) "0" else stringResource(R.string.editor_capo_fret, capo),
            dimmed = capo == 0,
            downLabel = stringResource(R.string.editor_capo_down),
            upLabel = stringResource(R.string.editor_capo_up),
            onDown = { onCapo(capo - 1) },
            onUp = { onCapo(capo + 1) },
            downEnabled = capo > 0,
            upEnabled = capo < 12,
        )
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            keyStepper()
            Spacer(Modifier.weight(1f))
            if (showCapo) capoStepper()
        }
        // with a capo: the diagrams stay the chart's, but the sound goes up (user's request, 29/10)
        if (showCapo && capo > 0) {
            val validKey = key.isNotEmpty() && Chords.isChord(key)
            Text(
                if (validKey) stringResource(R.string.editor_capo_sounds, key, capo, Chords.soundingKey(key, capo))
                else androidx.compose.ui.res.pluralStringResource(R.plurals.editor_capo_sounds_nokey, capo, capo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Icon and name in view (user's request, 29/10), then − value +. */
@Composable
private fun Stepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    dimmed: Boolean,
    downLabel: String,
    upLabel: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    downEnabled: Boolean,
    upEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // phones: the icon alone (with "Capotraste" written out, "2ª casa" broke over three lines, 01/11); the name stays
        // for accessibility
        val wide = wideLists()
        Icon(icon, if (wide) null else label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (wide) Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
        IconButton(onClick = onDown, enabled = downEnabled) { Icon(AppIcons.Remove, downLabel) }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        IconButton(onClick = onUp, enabled = upEnabled) { Icon(AppIcons.Add, upLabel) }
    }
}

private const val SAMPLE = "{key: G}\n{capo: 2}\n[G]Saio cedo pela [D]rua"

@Composable
private fun KeyCapoSamples() {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.m)) {
        KeyCapoControls(SAMPLE, {}, {})
        KeyCapoControls(SAMPLE, {}, {}, showCapo = false)
    }
}

@LightPreview
@Composable
private fun KeyCapoLightPreview() = PreviewSurface(dark = false) { KeyCapoSamples() }

@DarkPreview
@Composable
private fun KeyCapoDarkPreview() = PreviewSurface(dark = true) { KeyCapoSamples() }

private fun hasChords(text: String) = Chords.hasChords(text) || Chords.fromChordsOverLyrics(text).let(Chords::hasChords)

private const val CHORDS_AFTER_TYPING_MS = 300L
