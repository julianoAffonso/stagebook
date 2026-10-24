package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.juliano.stagebook.ui.theme.Spacing

/** Lista vazia: ícone grande, frase curta e, se houver, o botão do próximo passo. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Spacing.xxl))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@LightPreview
@Composable
private fun EmptyStateLightPreview() = PreviewSurface(dark = false) {
    EmptyState(AppIcons.MusicNote, "Nenhuma música ainda", "Crie uma ou importe da internet.") {
        ActionButton("Nova música", onClick = {}, icon = AppIcons.Add)
    }
}

@DarkPreview
@Composable
private fun EmptyStateDarkPreview() = PreviewSurface(dark = true) {
    EmptyState(AppIcons.QueueMusic, "Nenhum setlist ainda", "Monte o primeiro show.")
}
