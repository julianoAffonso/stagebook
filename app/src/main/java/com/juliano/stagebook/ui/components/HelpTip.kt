package com.juliano.stagebook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Explicação escondida, como um "alt": uma linha discreta (ⓘ + [label]) e, ao tocar, um balão com o [text]. Fica até
 * tocar fora. Para as explicações longas dos ajustes não ocuparem a tela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTip(text: String, modifier: Modifier = Modifier, label: String = stringResource(R.string.help_how)) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = { RichTooltip { Text(text) } },
        state = state,
        modifier = modifier,
    ) {
        Row(
            Modifier.clip(MaterialTheme.shapes.small).clickable { scope.launch { state.show() } }.padding(vertical = Spacing.xs, horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(AppIcons.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Só o ⓘ (ao lado do título de um cartão): ao tocar, o balão com o [text]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpIcon(text: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = { RichTooltip { Text(text) } },
        state = state,
        modifier = modifier,
    ) {
        Icon(
            AppIcons.Info,
            stringResource(R.string.help_how),
            Modifier.clip(CircleShape).clickable { scope.launch { state.show() } }.padding(Spacing.xs).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HelpTipSample() = Column {
    HelpTip("Toca o backing track de cada música numa interface de áudio USB ou na saída do aparelho.")
    HelpTip("Interface USB Audio Class 2.0, 48 kHz, USB OTG.", label = "O que a saída USB precisa")
    HelpIcon("Explicação do cartão, ao lado do título.")
}

@LightPreview
@Composable
private fun HelpTipLightPreview() = PreviewSurface(dark = false) { HelpTipSample() }

@DarkPreview
@Composable
private fun HelpTipDarkPreview() = PreviewSurface(dark = true) { HelpTipSample() }
