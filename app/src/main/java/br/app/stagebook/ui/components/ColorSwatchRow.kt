package br.app.stagebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing
import br.app.stagebook.ui.theme.StagePalette

/** Color dots (ARGB) to pick one; the chosen one gets a border and a ✓. */
@Composable
fun ColorSwatchRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        colors.forEach { argb ->
            val color = Color(argb)
            val isSelected = argb == selected
            Box(
                modifier = Modifier
                    .size(Sizes.swatch)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(argb) }),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    // ✓ in black or white, whichever shows better on the color
                    Icon(AppIcons.Check, contentDescription = null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White)
                }
            }
        }
    }
}

@LightPreview
@Composable
private fun ColorSwatchRowLightPreview() = PreviewSurface(dark = false) {
    ColorSwatchRow(StagePalette.text, selected = StagePalette.text[1], onSelect = {})
}

@DarkPreview
@Composable
private fun ColorSwatchRowDarkPreview() = PreviewSurface(dark = true) {
    ColorSwatchRow(StagePalette.background, selected = StagePalette.background[0], onSelect = {})
}
