package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import br.app.stagebook.ui.theme.Spacing

/**
 * A subtitle over a group of cards inside a Settings section (user, 22/11: the cards seemed out of context), as the
 * Pixel's Settings do: small, in the accent color, a little apart from the cards above.
 */
@Composable
fun SettingsSubheader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() }.padding(start = Spacing.s, top = Spacing.s),
    )
}

@LightPreview
@Composable
private fun SettingsSubheaderLightPreview() = PreviewSurface(dark = false) { SettingsSubheader("Cópias") }

@DarkPreview
@Composable
private fun SettingsSubheaderDarkPreview() = PreviewSurface(dark = true) { SettingsSubheader("Cópias") }
