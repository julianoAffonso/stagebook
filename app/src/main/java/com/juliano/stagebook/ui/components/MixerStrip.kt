package com.juliano.stagebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
 * Um canal do mixer (ou um par estéreo): nome, liga/desliga, fader, normalizar, saída e as marcas EQ, Centralizar e
 * Par estéreo. [first] é o canal do arquivo (0 = canal 1); [pair] diz se é um par; [canPair] se pode virar par.
 */
@Composable
fun MixerStrip(
    first: Int,
    strip: MixChannel,
    pair: Boolean,
    canPair: Boolean,
    onChange: (MixChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val title = if (pair) stringResource(R.string.mixer_pair, first + 1, first + 2) else stringResource(R.string.mixer_channel, first + 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = strip.name,
                    onValueChange = { onChange(strip.copy(name = it.take(24))) },
                    label = { Text(stringResource(R.string.mixer_name)) },
                    placeholder = { Text(title) },
                    supportingText = { if (strip.name.isNotEmpty()) Text(title) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(strip.copy(on = !strip.on)) }) {
                    Icon(
                        if (strip.on) AppIcons.VolumeUp else AppIcons.VolumeOff,
                        stringResource(if (strip.on) R.string.mixer_mute else R.string.mixer_unmute),
                        tint = if (strip.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Slider(
                    value = strip.gainDb,
                    onValueChange = { onChange(strip.copy(gainDb = (it * 2).roundToInt() / 2f)) },
                    valueRange = MixerConfig.MIN_DB..MixerConfig.MAX_DB,
                    enabled = strip.on,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (strip.gainDb <= MixerConfig.MIN_DB) stringResource(R.string.mixer_db_mute) else stringResource(R.string.mixer_db, strip.gainDb),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 72.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OptionDropdown(
                    stringResource(R.string.mixer_norm), NormMode.entries, strip.norm, { onChange(strip.copy(norm = it)) },
                    { normName(it) }, Modifier.weight(1f),
                )
                val step = if (pair) 2 else 1
                val outputs = (0 until MixerConfig.MAX step step).toList()
                OptionDropdown(
                    stringResource(R.string.mixer_output), outputs, if (strip.output >= 0) strip.output else first,
                    { onChange(strip.copy(output = it)) },
                    { if (pair) stringResource(R.string.mixer_output_pair, it + 1, it + 2) else stringResource(R.string.mixer_output_one, it + 1) },
                    Modifier.weight(1f),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(strip.eq, { onChange(strip.copy(eq = !strip.eq)) }, { Text(stringResource(R.string.mixer_eq)) })
                FilterChip(strip.center, { onChange(strip.copy(center = !strip.center)) }, { Text(stringResource(R.string.mixer_center)) })
                if (canPair) {
                    FilterChip(strip.stereo, { onChange(strip.copy(stereo = !strip.stereo)) }, { Text(stringResource(R.string.mixer_stereo)) })
                }
            }
        }
    }
}

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
    MixerStrip(0, MixChannel(name = "Backing", stereo = true, eq = true, norm = NormMode.VOLUME, gainDb = -3f), pair = true, canPair = true, onChange = {})
    MixerStrip(2, MixChannel(name = "Click", on = false, norm = NormMode.PEAK, output = 6), pair = false, canPair = true, onChange = {})
}

@LightPreview
@Composable
private fun MixerStripLightPreview() = PreviewSurface(dark = false) { MixerStripSample() }

@DarkPreview
@Composable
private fun MixerStripDarkPreview() = PreviewSurface(dark = true) { MixerStripSample() }
