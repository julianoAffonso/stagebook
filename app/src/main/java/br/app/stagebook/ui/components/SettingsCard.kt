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
 * Cartão de uma seção dos ajustes: rótulo pequeno em destaque e o conteúdo embaixo. [help]: a explicação do cartão, num ⓘ
 * ao lado do título (pedido do usuário, 29/10: um "Como funciona" repetido em todo cartão cansava).
 */
@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, help: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
