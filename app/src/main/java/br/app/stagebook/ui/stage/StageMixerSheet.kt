package br.app.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * silence and nothing could be heard while adjusting. Here the song keeps playing. With "Use the global mixer" on, the
 * controls change the global mixer, every song's (user, 01/11: read-only, it couldn't be adjusted while listening
 * anywhere); off ([own]), they change the song's own copy ([onOwn] makes it from the global one) and "Back to the
 * global mixer" ([onGeneral]) drops it. [onMixer] saves whichever is shown. "Monitor" starts off and isn't saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StageMixerSheet(
    mixer: MixerConfig,
    own: Boolean,
    onMixer: (MixerConfig) -> Unit,
    onOwn: () -> Unit,
    onGeneral: () -> Unit,
    onDismiss: () -> Unit,
) {
    br.app.stagebook.ui.components.FollowBusyWhile()
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
            // "Use the global mixer" on top: on, the controls below change the global mixer (every song); turning it off
            // makes the song's own copy; turning it on again asks first and drops the song's settings
            br.app.stagebook.ui.components.SwitchRow(
                stringResource(R.string.stage_mix_use_global), !own,
                { on -> if (on) backToGeneral = true else onOwn() },
            )
            // which mixer the controls change, as text: a link to Settings would leave the stage mid-song (user, 01/11)
            androidx.compose.material3.Text(
                stringResource(if (own) R.string.stage_mix_own else R.string.stage_mix_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (shown, onShown) = br.app.stagebook.ui.settings.rememberMixerDraft(mixer, onMixer)
            MixerCard(shown, onShown, { levels.value }, monitoring, { monitoring = it })
            EqCard(shown, onShown, { levels.value }, monitoring)
        }
    }
}

