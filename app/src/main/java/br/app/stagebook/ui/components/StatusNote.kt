package br.app.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.theme.Spacing

/** What a [StatusNote] says: all set, a problem to act on, or just information. */
enum class StatusTone { OK, PROBLEM, INFO }

/**
 * A state the app reports (the battery is free, there's no Wi-Fi, another version): a tinted box with an icon, so it
 * doesn't read like one more setting among the others (user, 02/11: "it confused me"). [content] goes under the text,
 * inside the box (the button that fixes it, for example).
 */
@Composable
fun StatusNote(text: String, tone: StatusTone, modifier: Modifier = Modifier, content: (@Composable () -> Unit)? = null) =
    StatusBox(tone, modifier) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        content?.invoke()
    }

/** The same box with any [content] (a text with a link, for example), in the box's text color. */
@Composable
fun StatusBox(tone: StatusTone, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, onContainer) = when (tone) {
        StatusTone.OK -> colors.primaryContainer to colors.onPrimaryContainer
        StatusTone.PROBLEM -> colors.errorContainer to colors.onErrorContainer
        StatusTone.INFO -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Icon(
                when (tone) { StatusTone.OK -> AppIcons.Check; StatusTone.PROBLEM -> AppIcons.Warning; StatusTone.INFO -> AppIcons.InfoOutline },
                null,
                Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
        }
    }
}

@LightPreview
@Composable
private fun StatusNoteLightPreview() = PreviewSurface(dark = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        StatusNote("O Android não vai parar o Stagebook para economizar bateria.", StatusTone.OK)
        StatusNote("Sem Wi-Fi: conecte a uma rede para achar a banda.", StatusTone.PROBLEM)
        StatusNote("Funciona só na mesma rede.", StatusTone.INFO)
    }
}

@DarkPreview
@Composable
private fun StatusNoteDarkPreview() = PreviewSurface(dark = true) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        StatusNote("O Android não vai parar o Stagebook para economizar bateria.", StatusTone.OK)
        StatusNote("Sem Wi-Fi: conecte a uma rede para achar a banda.", StatusTone.PROBLEM)
        StatusNote("Funciona só na mesma rede.", StatusTone.INFO)
    }
}
