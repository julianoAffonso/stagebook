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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
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
 * Botão de ação do app (regra da T35, pedido do usuário: "botões com fundo parecem dos anos 2000"): **sem fundo**, ícone +
 * texto, como nos apps do Google. A cor diz a importância ([ButtonTone]): Primary = destaque, Tonal = cor do texto,
 * Danger = vermelho, Quiet = apagado. Ao tocar, a onda na forma de pílula e um leve afundar (o "morph" do Expressive).
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
    // sem fundo, um botão esticado deixaria o texto boiando no meio: ele encosta no começo do espaço (como nos apps do Google)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = Sizes.button)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        colors = toneColors(tone),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = Spacing.s),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
}

@Composable
private fun toneColors(tone: ButtonTone): ButtonColors {
    val c = MaterialTheme.colorScheme
    val content = when (tone) {
        ButtonTone.Primary -> c.primary
        ButtonTone.Tonal -> c.onSurface
        ButtonTone.Danger -> c.error
        ButtonTone.Quiet -> c.onSurfaceVariant
    }
    return ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = content,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = c.onSurface.copy(alpha = DISABLED_ALPHA),
    )
}

private const val DISABLED_ALPHA = 0.38f

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
