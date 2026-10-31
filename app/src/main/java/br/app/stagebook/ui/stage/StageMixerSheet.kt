package br.app.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.app.stagebook.R
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.MixerLevels
import br.app.stagebook.player.StagePlayer
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.settings.EqCard
import br.app.stagebook.ui.settings.MixerCard
import br.app.stagebook.ui.theme.Spacing

/**
 * The mixer over the stage (user, 31/10): from Settings → Player the backing track stopped, so the meters showed
 * silence and nothing could be heard while adjusting. Here the song keeps playing, and what's changed stays with the
 * song ([own]), starting from the general mixer; "Back to the general mixer" ([onGeneral]) drops it. "Monitor" starts
 * off and isn't saved, as in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StageMixerSheet(mixer: MixerConfig, own: Boolean, onMixer: (MixerConfig) -> Unit, onGeneral: () -> Unit, onDismiss: () -> Unit) {
    var monitoring by remember { mutableStateOf(false) }
    var backToGeneral by remember { mutableStateOf(false) }
    if (backToGeneral) {
        ConfirmDialog(stringResource(R.string.stage_mix_general_confirm), stringResource(R.string.stage_mix_general), { backToGeneral = false; onGeneral() }, { backToGeneral = false })
    }
    val levels = remember { mutableStateOf(MixerLevels.SILENT) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(monitoring) {
        if (monitoring) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { StagePlayer.liveLevels().collect { levels.value = it } }
        levels.value = MixerLevels.SILENT
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            // whose settings these are: this song's own, or the general ones that become this song's when changed
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(if (own) R.string.stage_mix_own else R.string.stage_mix_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (own) TextButton(onClick = { backToGeneral = true }) { Text(stringResource(R.string.stage_mix_general)) }
            }
            MixerCard(mixer, onMixer, { levels.value }, monitoring) { monitoring = it }
            EqCard(mixer, onMixer, { levels.value }, monitoring)
        }
    }
}
