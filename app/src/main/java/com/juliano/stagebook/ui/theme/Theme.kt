package com.juliano.stagebook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StagebookShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),      // cartões (Material 3 Expressive: cantos bem redondos)
    extraLarge = RoundedCornerShape(32.dp),
)

/** O app é escuro (palco); o claro existe para os previews e para quem preferir. */
@Composable
fun StagebookTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StagebookTypography,
        shapes = StagebookShapes,
        content = content,
    )
}
