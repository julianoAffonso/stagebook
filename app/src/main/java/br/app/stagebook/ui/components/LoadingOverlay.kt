package br.app.stagebook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.theme.Spacing

/** "Loading" veil over the screen, fading in and out. */
@Composable
fun LoadingOverlay(visible: Boolean, message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    CircularProgressIndicator(Modifier.size(Spacing.l), strokeWidth = 3.dp)
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@LightPreview
@Composable
private fun LoadingOverlayLightPreview() = PreviewSurface(dark = false) {
    Box(Modifier.height(200.dp)) { LoadingOverlay(visible = true, message = "Buscando artista e ano…") }
}

@DarkPreview
@Composable
private fun LoadingOverlayDarkPreview() = PreviewSurface(dark = true) {
    Box(Modifier.height(200.dp)) { LoadingOverlay(visible = true, message = "Buscando artista e ano…") }
}
