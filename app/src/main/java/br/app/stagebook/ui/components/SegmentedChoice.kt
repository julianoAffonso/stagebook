package br.app.stagebook.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R

/** One option of a [SegmentedChoice]: its value, its label and, optionally, an icon. */
data class Choice<T>(val value: T, @StringRes val label: Int, val icon: ImageVector? = null)

/**
 * A row of two or three buttons, one chosen (turn pages | scroll, lyrics and chords | lyrics only…), full width with
 * pill ends like the app's other buttons (Material 3 Expressive). One component for every such choice in Settings.
 */
@Composable
fun <T> SegmentedChoice(options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = selected == o.value,
                onClick = { onSelect(o.value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, baseShape = CircleShape),
                icon = { o.icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) } ?: SegmentedButtonDefaults.Icon(selected == o.value) },
            ) {
                // one line whatever the text size: on a narrow phone with bigger text "Letra e cifra" wrapped and was cut
                Text(
                    stringResource(o.label), maxLines = 1,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                )
            }
        }
    }
}

@LightPreview
@Composable
private fun SegmentedChoiceLightPreview() = PreviewSurface(dark = false) {
    SegmentedChoice(listOf(Choice(true, R.string.editor_lyrics_chords), Choice(false, R.string.settings_lyrics_only)), true, {})
}

@DarkPreview
@Composable
private fun SegmentedChoiceDarkPreview() = PreviewSurface(dark = true) {
    SegmentedChoice(listOf(Choice(true, R.string.editor_lyrics_chords), Choice(false, R.string.settings_lyrics_only)), false, {})
}
