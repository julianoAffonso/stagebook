package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.juliano.stagebook.ui.theme.Spacing

/** Uma chave liga/desliga com o texto à esquerda (ajustes, janelinhas do palco); tocar no texto também muda. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SwitchRowSamples() {
    Column {
        SwitchRow("Contagem 1-2-3-4 antes", true, {})
        SwitchRow("Som (desligado: só pisca)", false, {})
    }
}

@LightPreview
@Composable
private fun SwitchRowLightPreview() = PreviewSurface(dark = false) { SwitchRowSamples() }

@DarkPreview
@Composable
private fun SwitchRowDarkPreview() = PreviewSurface(dark = true) { SwitchRowSamples() }
