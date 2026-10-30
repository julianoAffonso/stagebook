package com.juliano.stagebook.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.R
import com.juliano.stagebook.data.BandInbox
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.PreviewSurface
import com.juliano.stagebook.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** O que a banda mandou e ainda não foi conferido (para o aviso nas abas, o número no ícone e a lista). */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inbox: BandInbox,
    private val bandInternet: com.juliano.stagebook.data.BandInternet,
    trust: com.juliano.stagebook.data.BandTrust,
) : ViewModel() {
    val items: StateFlow<List<BandInbox.Item>> = inbox.items

    /** Tem alguém da banda ("Sincronizar com a banda" só aparece aí). */
    val hasBand: StateFlow<Boolean> = trust.members.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), trust.members.value.isNotEmpty())

    /** Sincronizar agora (caixa da internet, reenvios, "ok" pendentes e 30 s de procura no Wi-Fi). */
    fun syncNow() = bandInternet.syncNow()
    fun later(id: String) = inbox.markSeen(id)
    fun discard(id: String) = inbox.remove(id)
}

@Composable
private fun itemText(item: BandInbox.Item) =
    if (item.newer) stringResource(R.string.inbox_newer, item.fromName)
    else if (item.setName.isEmpty()) pluralStringResource(R.plurals.inbox_songs, item.songCount, item.fromName, item.songCount)
    else stringResource(R.string.inbox_setlist, item.fromName, item.setName)

/** O aviso de novidade, como e-mail novo: "Ana enviou o setlist Show de sábado" → Conferir / Agora não. */
@Composable
fun InboxBanner(item: BandInbox.Item, onOpen: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(AppIcons.Group, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(itemText(item), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.inbox_later)) }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.inbox_open)) }
            }
        }
    }
}

/** "Recebidos da banda": tudo o que ainda não foi conferido; tocar abre a conferência, a lixeira descarta. */
@Composable
fun InboxDialog(items: List<BandInbox.Item>, onOpen: (String) -> Unit, onDiscard: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbox_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // o mesmo setlist mandado por mais de um membro fica junto (T11): quem mandou, quando, e o mais novo marcado
                com.juliano.stagebook.data.groupInbox(items).forEach { group ->
                    if (group.size > 1) {
                        Text(
                            stringResource(R.string.inbox_versions, group.first().setName, group.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                    }
                    group.forEachIndexed { i, item ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(item.id) }.padding(vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (group.size > 1) item.fromName else itemText(item), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    timeOf(item.at) + if (group.size > 1 && i == 0) " · " + stringResource(R.string.inbox_latest) else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.IconButton(onClick = { onDiscard(item.id) }) {
                                Icon(AppIcons.Delete, stringResource(R.string.inbox_discard))
                            }
                        }
                    }
                }
                if (items.any { a -> items.count { it.setName.isNotEmpty() && it.setName.equals(a.setName, true) } > 1 }) {
                    Text(stringResource(R.string.inbox_versions_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Hora em que chegou (hoje: "14:05"; outro dia: "28/10 14:05"). */
@Composable
private fun timeOf(at: Long): String {
    val today = android.text.format.DateUtils.isToday(at)
    val pattern = if (today) "HH:mm" else "dd/MM HH:mm"
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(at))
}

private val sample = BandInbox.Item("1", "a", "Celular da Ana", "LETRAS3\n12\nShow de sábado")

@LightPreview
@Composable
private fun InboxBannerLightPreview() = PreviewSurface(dark = false) { InboxBanner(sample, {}, {}) }

@DarkPreview
@Composable
private fun InboxBannerDarkPreview() = PreviewSurface(dark = true) { InboxBanner(sample, {}, {}) }
