package br.app.stagebook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StagebookShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),      // cards (Material 3 Expressive: very round corners)
    extraLarge = RoundedCornerShape(32.dp),
)

/** The app is dark (stage); light exists for the previews and for those who prefer it. */
@Composable
fun StagebookTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StagebookTypography,
        shapes = StagebookShapes,
        content = content,
    )
}
