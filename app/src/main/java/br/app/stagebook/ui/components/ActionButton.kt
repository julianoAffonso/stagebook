package br.app.stagebook.ui.components

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
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

enum class ButtonTone { Primary, Tonal, Danger, Quiet }

/**
 * The app's action button, in Google's app pattern (T35 rule, user's decision): **the screen's only main action**
 * ([ButtonTone.Primary]) has a filled background; the others are icon + text **with no background** (Tonal = text
 * color, Danger = red, Quiet = dimmed). The main one sits at the end of the space; the others at the start. On tap, the
 * pill ripple and a slight press (the Expressive "morph").
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
    // like Google's apps: in a row, the secondary sits at the start and the main action at the end (Cancel … OK); a
    // stretched button never leaves the text floating in the middle
    Box(modifier, contentAlignment = if (tone == ButtonTone.Primary) Alignment.CenterEnd else Alignment.CenterStart) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = Sizes.button)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        colors = toneColors(tone),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = if (tone == ButtonTone.Primary) Spacing.l else Spacing.s),
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
    // the screen's main action: a filled pill, like Gmail's "Compose" or "Save" on Google's screens
    if (tone == ButtonTone.Primary) return ButtonDefaults.buttonColors()
    val content = when (tone) {
        ButtonTone.Tonal -> c.onSurface
        ButtonTone.Danger -> c.error
        else -> c.onSurfaceVariant
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
        ActionButton("Palco", onClick = {}, icon = AppIcons.Slideshow)
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
