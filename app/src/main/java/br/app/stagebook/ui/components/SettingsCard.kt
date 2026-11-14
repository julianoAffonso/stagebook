package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.app.stagebook.ui.theme.Spacing

/**
 * What a Settings search result points to: the item scrolls into view and flashes once ([nonce] makes a repeat count; see
 * [settingsItem]). A State handed down through a local that never changes: a new focus recomposes only the item that reads its value,
 * not the whole screen.
 */
@androidx.compose.runtime.Immutable
class SettingsFocus(val card: String, val nonce: Int, val item: String? = null)

val LocalSettingsFocus = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<SettingsFocus?>> { androidx.compose.runtime.mutableStateOf(null) }

/**
 * Card for a settings section: small highlighted label and the content below. [help]: the card's explanation, in an ⓘ
 * next to the title (user's request, 29/10: a "How it works" repeated on every card was tiring).
 */
@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, help: String? = null, danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (help != null) HelpIcon(help)
            }
            content()
        }
    }
}

@Composable
private fun SettingsCardSample() {
    SettingsCard("Pedal Bluetooth") {
        Text("Aperte o pedal: o último botão aparece aqui.", style = MaterialTheme.typography.bodyLarge)
        ActionButton("Parear pedal", onClick = {}, icon = AppIcons.Bluetooth, tone = ButtonTone.Tonal)
    }
}

@LightPreview
@Composable
private fun SettingsCardLightPreview() = PreviewSurface(dark = false) { SettingsCardSample() }

@DarkPreview
@Composable
private fun SettingsCardDarkPreview() = PreviewSurface(dark = true) { SettingsCardSample() }
