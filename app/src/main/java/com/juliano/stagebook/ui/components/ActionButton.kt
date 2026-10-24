package com.juliano.stagebook.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing

enum class ButtonTone { Primary, Tonal, Danger, Quiet }

/**
 * Botão do app no padrão Material 3 Expressive: em pílula, que ao pressionar afunda um pouco e os cantos
 * ficam menos redondos (o "morph" dos apps do Google). Ícone opcional.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val corner by animateIntAsState(if (pressed) 30 else 50, label = "corner")
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Sizes.button)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        colors = toneColors(tone),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = Spacing.l),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun toneColors(tone: ButtonTone): ButtonColors {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        ButtonTone.Primary -> ButtonDefaults.buttonColors()
        ButtonTone.Tonal -> ButtonDefaults.filledTonalButtonColors()
        ButtonTone.Danger -> ButtonDefaults.buttonColors(containerColor = c.errorContainer, contentColor = c.onErrorContainer)
        ButtonTone.Quiet -> ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = c.primary)
    }
}

@Composable
private fun ActionButtonSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ActionButton("Palco", onClick = {}, icon = AppIcons.Play)
        ActionButton("Importar da internet", onClick = {}, icon = AppIcons.Public, tone = ButtonTone.Tonal)
        ActionButton("Excluir setlist", onClick = {}, icon = AppIcons.Delete, tone = ButtonTone.Danger)
        ActionButton("Cancelar", onClick = {}, tone = ButtonTone.Quiet)
    }
}

@LightPreview
@Composable
private fun ActionButtonLightPreview() = PreviewSurface(dark = false) { ActionButtonSamples() }

@DarkPreview
@Composable
private fun ActionButtonDarkPreview() = PreviewSurface(dark = true) { ActionButtonSamples() }
