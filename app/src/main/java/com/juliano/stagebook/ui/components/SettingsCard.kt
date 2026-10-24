package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.juliano.stagebook.ui.theme.Spacing

/** Cartão de uma seção dos ajustes: rótulo pequeno em destaque e o conteúdo embaixo. */
@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
