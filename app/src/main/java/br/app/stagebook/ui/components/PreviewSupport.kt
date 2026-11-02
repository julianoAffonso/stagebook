package br.app.stagebook.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
annotation class LightPreview

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class DarkPreview

/** Theme background for component previews. */
@Composable
internal fun PreviewSurface(dark: Boolean, content: @Composable () -> Unit) {
    StagebookTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            androidx.compose.foundation.layout.Box(Modifier.padding(Spacing.m)) { content() }
        }
    }
}

data class SampleSong(val title: String, val subtitle: String)

class SampleSongProvider : PreviewParameterProvider<SampleSong> {
    override val values = sequenceOf(
        SampleSong("Tempo Perdido", "Legião Urbana · 1986"),
        SampleSong("Hazard", "Richard Marx · 1991"),
        SampleSong("Uma música com um nome bem comprido para ver a quebra de linha", ""),
    )
}
