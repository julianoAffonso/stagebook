package br.app.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.MixerLevels
import br.app.stagebook.player.StagePlayer
import br.app.stagebook.ui.settings.EqCard
import br.app.stagebook.ui.settings.MixerCard
import br.app.stagebook.ui.theme.Spacing

/**
 * The mixer over the stage (user, 31/10): from Settings → Player the backing track stopped, so the meters showed
 * silence and nothing could be heard while adjusting. Here the song keeps playing. It's the same single mixer as
 * Settings (one setting for every song, the user's decision); "Monitor" starts off and isn't saved, as there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StageMixerSheet(mixer: MixerConfig, onMixer: (MixerConfig) -> Unit, onDismiss: () -> Unit) {
    var monitoring by remember { mutableStateOf(false) }
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
            MixerCard(mixer, onMixer, { levels.value }, monitoring) { monitoring = it }
            EqCard(mixer, onMixer, { levels.value }, monitoring)
        }
    }
}
