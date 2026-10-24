package com.juliano.stagebook.ui.components

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.R
import com.juliano.stagebook.domain.MixChannel
import com.juliano.stagebook.domain.MixerConfig
import com.juliano.stagebook.domain.NormMode
import com.juliano.stagebook.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Um canal do mixer (ou um par estéreo) como na mesa: nome, liga/desliga e fader, com um resumo do resto. Tocar no
 * nome (ou no ícone de ajuste) abre a edição ([MixerChannelEditor]). [first] é o canal do arquivo (0 = canal 1).
 * [upright]: faixa estreita com o fader de pé (tablet); senão, uma linha com o fader deitado (celular).
 */
@Composable
fun MixerStrip(
    first: Int,
    strip: MixChannel,
    pair: Boolean,
    onChange: (MixChannel) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    upright: Boolean = false,
) {
    val setGain = { db: Float -> onChange(strip.copy(gainDb = (db * 2).roundToInt() / 2f)) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        if (upright) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(first, strip, pair, onEdit, Modifier.fillMaxWidth(), centered = true)
                MuteButton(strip, onChange)
                VerticalFader(strip.gainDb, setGain, valueRange = MixerConfig.MIN_DB..MixerConfig.MAX_DB, enabled = strip.on, origin = MixerConfig.MIN_DB)
                GainText(strip)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Header(first, strip, pair, onEdit, Modifier.weight(1f), centered = false)
                    MuteButton(strip, onChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Slider(
                        value = strip.gainDb, onValueChange = setGain,
                        valueRange = MixerConfig.MIN_DB..MixerConfig.MAX_DB, enabled = strip.on, modifier = Modifier.weight(1f),
                    )
                    GainText(strip, Modifier.widthIn(min = 72.dp))
                }
            }
        }
    }
}

// nome do canal e o resumo (normalizar, saída, EQ, centralizar); um toque abre a edição
@Composable
private fun Header(first: Int, strip: MixChannel, pair: Boolean, onEdit: () -> Unit, modifier: Modifier, centered: Boolean) {
    val title = channelTitle(first, pair)
    val output = if (strip.output >= 0) strip.output else first
    val summary = listOfNotNull(
        if (pair) stringResource(R.string.mixer_output_pair, output + 1, output + 2) else stringResource(R.string.mixer_output_one, output + 1),
        if (strip.norm != NormMode.NONE) normName(strip.norm) else null,
        if (strip.eq) stringResource(R.string.mixer_eq) else null,
        if (strip.center) stringResource(R.string.mixer_center_short) else null,
    ).joinToString(" · ")
    Row(
        modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = stringResource(R.string.mixer_edit), onClick = onEdit).padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, if (centered) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        // de pé, o cabeçalho tem altura fixa: os faders lado a lado começam na mesma linha
        Column(
            Modifier.weight(1f, fill = !centered).then(if (centered) Modifier.height(64.dp) else Modifier),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(strip.name.ifEmpty { title }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (strip.name.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (centered) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        Icon(AppIcons.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun channelTitle(first: Int, pair: Boolean) =
    if (pair) stringResource(R.string.mixer_pair, first + 1, first + 2) else stringResource(R.string.mixer_channel, first + 1)

/** Edição de um canal do mixer: nome, normalizar, saída, EQ, Centralizar e Par estéreo. Cada mudança vale na hora. */
@Composable
fun MixerChannelEditor(
    first: Int,
    strip: MixChannel,
    pair: Boolean,
    canPair: Boolean,
    onChange: (MixChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channelTitle(first, pair)) },
        text = { MixerChannelForm(first, strip, pair, canPair, onChange) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mixer_done)) } },
    )
}

/** O que a edição de um canal mostra (separado do diálogo para o preview e os prints). */
@Composable
fun MixerChannelForm(first: Int, strip: MixChannel, pair: Boolean, canPair: Boolean, onChange: (MixChannel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        NameField(strip, channelTitle(first, pair), onChange, Modifier.fillMaxWidth())
        NormDropdown(strip, onChange, Modifier.fillMaxWidth())
        HelpTip(stringResource(R.string.mixer_norm_help), label = stringResource(R.string.mixer_norm_about))
        OutputDropdown(first, strip, pair, onChange, Modifier.fillMaxWidth())
        OptionSwitch(stringResource(R.string.mixer_eq_switch), strip.eq) { onChange(strip.copy(eq = it)) }
        OptionSwitch(stringResource(if (pair) R.string.mixer_center_pair else R.string.mixer_center_one), strip.center) {
            onChange(strip.copy(center = it))
        }
        // une este canal físico com o seguinte num par estéreo (no par, separa de novo)
        if (canPair) {
            OptionSwitch(stringResource(R.string.mixer_join, first + 2), strip.stereo) { onChange(strip.copy(stereo = it)) }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }

@Composable
private fun NameField(strip: MixChannel, title: String, onChange: (MixChannel) -> Unit, modifier: Modifier) = OutlinedTextField(
    value = strip.name,
    onValueChange = { onChange(strip.copy(name = it.take(24))) },
    label = { Text(stringResource(R.string.mixer_name)) },
    placeholder = { Text(title) },
    supportingText = { if (strip.name.isNotEmpty()) Text(title) },
    singleLine = true,
    modifier = modifier,
)

@Composable
private fun MuteButton(strip: MixChannel, onChange: (MixChannel) -> Unit) = IconButton(onClick = { onChange(strip.copy(on = !strip.on)) }) {
    Icon(
        if (strip.on) AppIcons.VolumeUp else AppIcons.VolumeOff,
        stringResource(if (strip.on) R.string.mixer_mute else R.string.mixer_unmute),
        tint = if (strip.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun GainText(strip: MixChannel, modifier: Modifier = Modifier) = Text(
    if (strip.gainDb <= MixerConfig.MIN_DB) stringResource(R.string.mixer_db_mute) else stringResource(R.string.mixer_db, strip.gainDb),
    style = MaterialTheme.typography.labelLarge,
    modifier = modifier,
)

@Composable
private fun NormDropdown(strip: MixChannel, onChange: (MixChannel) -> Unit, modifier: Modifier) = OptionDropdown(
    stringResource(R.string.mixer_norm), NormMode.entries, strip.norm, { onChange(strip.copy(norm = it)) }, { normName(it) }, modifier,
)

@Composable
private fun OutputDropdown(first: Int, strip: MixChannel, pair: Boolean, onChange: (MixChannel) -> Unit, modifier: Modifier) =
    OptionDropdown(
        stringResource(R.string.mixer_output), (0 until MixerConfig.MAX step if (pair) 2 else 1).toList(),
        if (strip.output >= 0) strip.output else first,
        { onChange(strip.copy(output = it)) },
        { if (pair) stringResource(R.string.mixer_output_pair, it + 1, it + 2) else stringResource(R.string.mixer_output_one, it + 1) },
        modifier,
    )

@Composable
private fun normName(m: NormMode) = stringResource(
    when (m) {
        NormMode.NONE -> R.string.mixer_norm_none
        NormMode.VOLUME -> R.string.mixer_norm_volume
        NormMode.PEAK -> R.string.mixer_norm_peak
    },
)

@Composable
private fun MixerStripSample() = Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
    MixerStrip(0, MixChannel(name = "Backing", stereo = true, eq = true, norm = NormMode.VOLUME, gainDb = -3f), pair = true, onChange = {}, onEdit = {})
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MixerStrip(2, MixChannel(name = "Click", norm = NormMode.PEAK, output = 6), pair = false, onChange = {}, onEdit = {}, Modifier.weight(1f), upright = true)
        MixerStrip(3, MixChannel(name = "Guia", on = false), pair = false, onChange = {}, onEdit = {}, Modifier.weight(1f), upright = true)
    }
}

@LightPreview
@Composable
private fun MixerStripLightPreview() = PreviewSurface(dark = false) { MixerStripSample() }

@DarkPreview
@Composable
private fun MixerStripDarkPreview() = PreviewSurface(dark = true) { MixerStripSample() }
