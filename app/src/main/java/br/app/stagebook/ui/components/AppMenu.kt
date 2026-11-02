package br.app.stagebook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Every menu that opens over the screen (⋮, Import, choice fields): lighter than the cards, with a shadow and a thin
 * border, so it reads as floating above the list and not as one more row of it (user's request, 30/10).
 */
object AppMenu {
    val shadow = 8.dp

    val color: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val border: BorderStroke
        @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
}
