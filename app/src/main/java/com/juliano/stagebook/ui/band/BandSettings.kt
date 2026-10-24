package com.juliano.stagebook.ui.band

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
import com.juliano.stagebook.R
import com.juliano.stagebook.data.BandLink
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ButtonTone
import com.juliano.stagebook.ui.components.ConfirmDialog
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.HelpTip
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.PreviewSurface
import com.juliano.stagebook.ui.components.SettingsCard
import com.juliano.stagebook.ui.components.TextInputDialog
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Ajustes → Banda: o nome com que a banda vê este aparelho, quem está na mesma rede agora (pedir para entrar) e quem é da
 * banda (remover). Sem Wi-Fi, só o aviso; a procura só roda com esta tela aberta ou com alguém da banda guardado.
 */
@Composable
fun BandDetail(band: BandUi, actions: BandActions) {
    var renaming by rememberSaveable { mutableStateOf(!band.nameConfirmed) }   // 1ª vez: pergunta o nome
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingsCard(stringResource(R.string.band_this_device)) {
            Text(band.name, style = MaterialTheme.typography.titleLarge)
            ActionButton(stringResource(R.string.band_rename), { renaming = true }, icon = AppIcons.Edit, tone = ButtonTone.Tonal)
            HelpTip(stringResource(R.string.band_help))
        }
        SettingsCard(stringResource(R.string.band_nearby)) {
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
            onDismiss = { actions.onName(band.name); renaming = false },   // fechar também confirma a sugestão
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

// um aparelho na rede: já é da banda, ou "Pedir para entrar" (e como está o pedido)
@Composable
private fun NearbyRow(peer: NearbyUi, onJoin: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(peer.name, style = MaterialTheme.typography.bodyLarge)
        when {
            peer.member -> Text(stringResource(R.string.band_is_member), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            peer.join == BandLink.Join.WAITING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.band_waiting), style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                peer.join?.let { j ->
                    Text(
                        stringResource(if (j == BandLink.Join.DENIED) R.string.band_denied else R.string.band_failed),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                ActionButton(stringResource(R.string.band_join), { onJoin(peer.id) }, icon = AppIcons.Add, tone = ButtonTone.Tonal)
            }
        }
    }
}

/** "Tablet da Ana quer entrar na banda" → Autorizar / Não autorizar (como no Bluetooth), em qualquer tela. */
@Composable
fun BandRequestDialog(name: String, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = { Icon(AppIcons.Group, null) },
        title = { Text(stringResource(R.string.band_request_title, name)) },
        text = { Text(stringResource(R.string.band_request_text), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.band_allow)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.band_deny)) } },
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
