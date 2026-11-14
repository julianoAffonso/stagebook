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
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.app.stagebook.ui.theme.Spacing

/** The card a Settings search result points to: it scrolls into view and flashes once ([nonce] makes a repeat count). */
@androidx.compose.runtime.Immutable
class SettingsFocus(val card: String, val nonce: Int, val item: String? = null)

val LocalSettingsFocus = androidx.compose.runtime.staticCompositionLocalOf<SettingsFocus?> { null }

/**
 * Card for a settings section: small highlighted label and the content below. [help]: the card's explanation, in an ⓘ
 * next to the title (user's request, 29/10: a "How it works" repeated on every card was tiring).
 */
@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, help: String? = null, danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val focus = LocalSettingsFocus.current
    val bring = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var flash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val mine = focus?.takeIf { it.card == title && it.item == null }   // an item flashes by itself
    // from a search result: into view, then a short flash (a border and a small grow, not only a color)
    androidx.compose.runtime.LaunchedEffect(mine?.nonce) {
        if (mine == null) return@LaunchedEffect
        bring.bringIntoView()
        flash = true
        kotlinx.coroutines.delay(1600)
        flash = false
    }
    val grow by androidx.compose.animation.core.animateFloatAsState(if (flash) 1.02f else 1f, label = "found")
    Surface(
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(bring).graphicsLayer { scaleX = grow; scaleY = grow },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = if (flash) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
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
