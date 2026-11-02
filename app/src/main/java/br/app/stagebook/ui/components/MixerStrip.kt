package br.app.stagebook.ui.components

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.domain.Meters
import br.app.stagebook.domain.MixChannel
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.NormMode
import br.app.stagebook.domain.OutputInfo
import br.app.stagebook.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * A mixer channel (or a stereo pair) like on a desk: name, on/off and fader, with a summary of the rest. Tapping the
 * name (or the settings icon) opens editing ([MixerChannelEditor]). [first] is the file's channel (0 = channel 1).
 * [upright]: narrow strip with a vertical fader (tablet); otherwise a row with a horizontal fader (phone).
 */
/**
 * The mixer shown read-only (a song on the global mixer, user 01/11): the controls look disabled, but the live meters
 * keep their normal colour so they stay readable on stage.
 */
val LocalMixerLocked = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Dims a control while the mixer is read-only ([LocalMixerLocked]); the meters don't use it. */
@Composable
fun Modifier.lockedDim(): Modifier = if (LocalMixerLocked.current) this.alpha(LOCKED_ALPHA) else this

private const val LOCKED_ALPHA = 0.38f   // Material's disabled look

@Composable
fun MixerStrip(
    first: Int,
    strip: MixChannel,
    pair: Boolean,
    onChange: (MixChannel) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    upright: Boolean = false,
    output: Int = first,                                         // the channel's first output on the output in use
    outputs: OutputInfo = OutputInfo(),                          // the output in use (device or USB interface)
    meters: () -> Meters = { Meters.silent(MixerConfig.MAX) },   // read only when drawing: meter next to the fader
    showMeters: Boolean = false,                                  // chave "Monitorar"
) {
    val lanes = if (pair) listOf(first, first + 1) else listOf(first)   // paired: double meter (left and right)
    val setGain = { db: Float -> onChange(strip.copy(gainDb = (db * 2).roundToInt() / 2f)) }
    // tapping the channel's name renames it, like a setlist's (user, 01/11); the settings icon opens the rest
    var renaming by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (renaming) {
        TextInputDialog(
            stringResource(R.string.mixer_name), strip.name, channelTitle(first, pair),
            { onChange(strip.copy(name = it.trim())) }, { renaming = false },
        )
    }
    val rename = { renaming = true }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        if (upright) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(first, strip, pair, output, outputs, onEdit, rename, Modifier.fillMaxWidth().lockedDim(), centered = true)
                androidx.compose.foundation.layout.Box(Modifier.lockedDim()) { MuteButton(strip, onChange) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.lockedDim()) {
                        VerticalFader(strip.gainDb, setGain, valueRange = MixerConfig.MIN_DB..MixerConfig.MAX_DB, enabled = strip.on, origin = MixerConfig.MIN_DB)
                    }
                    if (showMeters) lanes.forEach { l -> PeakMeter({ meters().levelDb[l] }, { meters().holdDb[l] }) }
                }
                GainText(strip, Modifier.lockedDim())
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                Row(Modifier.lockedDim(), verticalAlignment = Alignment.CenterVertically) {
                    Header(first, strip, pair, output, outputs, onEdit, rename, Modifier.weight(1f), centered = false)
                    MuteButton(strip, onChange)
                }
                Row(Modifier.lockedDim(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Slider(
                        value = strip.gainDb, onValueChange = setGain,
                        valueRange = MixerConfig.MIN_DB..MixerConfig.MAX_DB, enabled = strip.on, modifier = Modifier.weight(1f),
                    )
                    GainText(strip, Modifier.widthIn(min = 72.dp))
                }
                // phone: the horizontal meter right below the fader (one per side when paired)
                if (showMeters) {
                    Column(Modifier.fillMaxWidth().padding(end = 80.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        lanes.forEach { l -> PeakMeter({ meters().levelDb[l] }, { meters().holdDb[l] }, vertical = false) }
                    }
                }
            }
        }
    }
}

// channel name (a tap renames it) and the summary (normalize, output, EQ, center) with the settings icon (a tap opens
// editing)
@Composable
private fun Header(
    first: Int, strip: MixChannel, pair: Boolean, output: Int, outputs: OutputInfo, onEdit: () -> Unit, onRename: () -> Unit,
    modifier: Modifier, centered: Boolean,
) {
    val title = channelTitle(first, pair)
    val summary = listOfNotNull(
        outputName(output, pair, outputs),
        if (strip.norm != NormMode.NONE) normName(strip.norm) else null,
        if (strip.eq) stringResource(R.string.mixer_eq) else null,
        if (strip.center) stringResource(R.string.mixer_center_short) else null,
    ).joinToString(" · ")
    Row(
        modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = stringResource(R.string.mixer_edit), onClick = onEdit).padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, if (centered) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        // upright, the header has a fixed height: the faders side by side start on the same line (tall enough for the
        // name, the channel and a two-line summary, which used to get cut)
        Column(
            Modifier.weight(1f, fill = !centered).then(if (centered) Modifier.height(80.dp) else Modifier),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                strip.name.ifEmpty { title }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = stringResource(R.string.mixer_rename), onClick = onRename),
            )
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

/** Editing a mixer channel: name, normalize, output, EQ, Center and Stereo pair. Each change applies right away. */
@Composable
fun MixerChannelEditor(
    first: Int,
    strip: MixChannel,
    pair: Boolean,
    canPair: Boolean,
    output: Int,
    outputs: OutputInfo,
    onChange: (MixChannel) -> Unit,
    onOutput: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channelTitle(first, pair)) },
        text = { MixerChannelForm(first, strip, pair, canPair, output, outputs, onChange, onOutput) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mixer_done)) } },
    )
}

/** What editing a channel shows (separate from the dialog for the preview and screenshots). */
@Composable
fun MixerChannelForm(
    first: Int,
    strip: MixChannel,
    pair: Boolean,
    canPair: Boolean,
    output: Int,
    outputs: OutputInfo,
    onChange: (MixChannel) -> Unit,
    onOutput: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        NameField(strip, channelTitle(first, pair), onChange, Modifier.fillMaxWidth())
        NormDropdown(strip, onChange, Modifier.fillMaxWidth())
        HelpTip(stringResource(R.string.mixer_norm_help), label = stringResource(R.string.mixer_norm_about))
        OutputDropdown(output, pair, outputs, onOutput, Modifier.fillMaxWidth())
        SwitchRow(stringResource(R.string.mixer_eq_switch), strip.eq, { onChange(strip.copy(eq = it)) })
        SwitchRow(stringResource(if (pair) R.string.mixer_center_pair else R.string.mixer_center_one), strip.center, {
            onChange(strip.copy(center = it))
        })
        // pairs this physical channel with the next one as stereo (on a pair, splits it again)
        if (canPair) {
            SwitchRow(stringResource(R.string.mixer_join, first + 2), strip.stereo, { onChange(strip.copy(stereo = it)) })
        }
    }
}

@Composable
private fun NameField(strip: MixChannel, title: String, onChange: (MixChannel) -> Unit, modifier: Modifier) = OutlinedTextField(
    value = strip.name,
    onValueChange = { onChange(strip.copy(name = it.take(24))) },
    label = { Text(stringResource(R.string.mixer_name)) },
    placeholder = { Text(title) },
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

// the outputs the output in use has (on the device: left and right); the chosen one shows even if this one lacks it
@Composable
private fun OutputDropdown(output: Int, pair: Boolean, outputs: OutputInfo, onOutput: (Int) -> Unit, modifier: Modifier) =
    OptionDropdown(
        stringResource(R.string.mixer_output_in, outputs.label()),
        (0 until maxOf(outputs.count, output + 1) step if (pair) 2 else 1).toList(),
        output, onOutput, { outputName(it, pair, outputs) }, modifier,
    )

/** Output name: "Output 7"/"Outputs 7-8" on the interface; "Left"/"Right"/"Left and right" on the device. */
@Composable
fun outputName(out: Int, pair: Boolean, outputs: OutputInfo): String = when {
    outputs.key == OutputInfo.DEVICE && pair && out == 0 -> stringResource(R.string.mixer_out_left_right)
    outputs.key == OutputInfo.DEVICE && !pair && out == 0 -> stringResource(R.string.mixer_out_left)
    outputs.key == OutputInfo.DEVICE && !pair && out == 1 -> stringResource(R.string.mixer_out_right)
    pair -> stringResource(R.string.mixer_output_pair, out + 1, out + 2)
    else -> stringResource(R.string.mixer_output_one, out + 1)
}

/** Output name for the screen: the interface's, or "device output". */
@Composable
fun OutputInfo.label(): String = if (key == OutputInfo.DEVICE || name.isEmpty()) stringResource(R.string.mixer_out_device) else name

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
        MixerStrip(2, MixChannel(name = "Click", norm = NormMode.PEAK), pair = false, onChange = {}, onEdit = {}, Modifier.weight(1f), upright = true, output = 6)
        MixerStrip(3, MixChannel(name = "Guia", on = false), pair = false, onChange = {}, onEdit = {}, Modifier.weight(1f), upright = true)
    }
}

@LightPreview
@Composable
private fun MixerStripLightPreview() = PreviewSurface(dark = false) { MixerStripSample() }

@DarkPreview
@Composable
private fun MixerStripDarkPreview() = PreviewSurface(dark = true) { MixerStripSample() }
