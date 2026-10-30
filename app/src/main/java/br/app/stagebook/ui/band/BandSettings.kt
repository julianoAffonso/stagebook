package br.app.stagebook.ui.band

import br.app.stagebook.ui.components.QuestionDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.data.BandLink
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.HelpTip
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.components.SettingsCard
import br.app.stagebook.ui.components.TextInputDialog
import br.app.stagebook.ui.theme.Spacing

/**
 * Settings → Band: the name the band sees for this device, who is on the same network now (ask to join) and who is in
 * the band (remove). Without Wi-Fi, just the notice; the search only runs with this screen open or with a saved band
 * member.
 */
@Composable
fun BandDetail(band: BandUi, actions: BandActions, lan: Boolean = true) {   // lan: "Band on the network" on (T19)
    var renaming by rememberSaveable { mutableStateOf(!band.nameConfirmed) }   // first time: asks for the name
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingsCard(stringResource(R.string.band_this_device), help = stringResource(R.string.band_help)) {
            Text(band.name, style = MaterialTheme.typography.titleLarge)
            ActionButton(stringResource(R.string.band_rename), { renaming = true }, icon = AppIcons.Edit, tone = ButtonTone.Tonal)
        }
        if (lan) SettingsCard(stringResource(R.string.band_nearby)) {
            when {
                !band.wifi -> Text(stringResource(R.string.band_no_wifi), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                band.nearby.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (band.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.band_searching), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> band.nearby.forEach { NearbyRow(it, actions.onJoin) }
            }
        }
        SettingsCard(stringResource(R.string.band_members)) {
            if (band.members.isEmpty()) {
                Text(stringResource(R.string.band_members_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            band.members.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(m.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Spacing.s))
                    IconButton(onClick = { removing = m.id }) { Icon(AppIcons.Delete, stringResource(R.string.band_remove, m.name)) }
                }
            }
        }
    }
    if (renaming) {
        TextInputDialog(
            stringResource(R.string.band_name_title), band.name, stringResource(R.string.band_name_hint),
            onConfirm = { actions.onName(it); renaming = false },
            onDismiss = { actions.onName(band.name); renaming = false },   // closing also confirms the suggestion
        )
    }
    removing?.let { id ->
        val name = band.members.firstOrNull { it.id == id }?.name.orEmpty()
        ConfirmDialog(
            stringResource(R.string.band_remove_confirm, name), stringResource(R.string.band_remove_short),
            onConfirm = { actions.onRemove(id); removing = null }, onDismiss = { removing = null },
        )
    }
}

// a device on the network: already in the band, or "Ask to join" (and the request's state)
@Composable
private fun NearbyRow(peer: NearbyUi, onJoin: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(peer.name, style = MaterialTheme.typography.bodyLarge)
        when {
            // another app version: nothing works between the two until both update to the same one
            peer.otherVersion != null -> Text(
                stringResource(R.string.band_other_version, peer.otherVersion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            peer.member -> Text(stringResource(R.string.band_is_member), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            peer.join == BandLink.Join.WAITING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.band_waiting), style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                peer.join?.let { j ->
                    Text(
                        stringResource(
                            when (j) {
                                BandLink.Join.DENIED -> R.string.band_denied
                                BandLink.Join.BUSY -> R.string.band_busy
                                BandLink.Join.VERSION -> R.string.band_version_join
                                else -> R.string.band_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                ActionButton(stringResource(R.string.band_join), { onJoin(peer.id) }, icon = AppIcons.Add, tone = ButtonTone.Tonal)
            }
        }
    }
}

/** "Ana's tablet wants to join the band" → Allow / Don't allow (like Bluetooth), on any screen. */
@Composable
fun BandRequestDialog(name: String, onAnswer: (Boolean) -> Unit) {
    QuestionDialog(
        stringResource(R.string.band_request_title, name), stringResource(R.string.band_request_text),
        stringResource(R.string.band_allow), { onAnswer(true) },
        stringResource(R.string.band_deny), { onAnswer(false) },
        icon = AppIcons.Group,
    )
}

private val sample = BandUi(
    name = "Meu tablet", wifi = true, searching = true,
    nearby = listOf(NearbyUi("1", "Celular da Ana", member = true), NearbyUi("2", "Teclado do Caio", member = false, join = BandLink.Join.WAITING), NearbyUi("3", "Baixo", member = false)),
    members = listOf(MemberUi("1", "Celular da Ana", 0)),
)

@LightPreview
@Composable
private fun BandLightPreview() = PreviewSurface(dark = false) { BandDetail(sample, BandActions()) }

@DarkPreview
@Composable
private fun BandDarkPreview() = PreviewSurface(dark = true) { BandDetail(sample, BandActions()) }
