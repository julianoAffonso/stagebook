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
import com.juliano.stagebook.R
import com.juliano.stagebook.data.BandInbox
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.PreviewSurface
import com.juliano.stagebook.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** O que a banda mandou e ainda não foi conferido (para o aviso nas abas, o número no ícone e a lista). */
@HiltViewModel
class InboxViewModel @Inject constructor(private val inbox: BandInbox) : ViewModel() {
    val items: StateFlow<List<BandInbox.Item>> = inbox.items
    fun later(id: String) = inbox.markSeen(id)
    fun discard(id: String) = inbox.remove(id)
}

@Composable
private fun itemText(item: BandInbox.Item) =
    if (item.setName.isEmpty()) pluralStringResource(R.plurals.inbox_songs, item.songCount, item.fromName, item.songCount)
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
                items.asReversed().forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(item.id) }.padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(itemText(item), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { onDiscard(item.id) }) {
                            Icon(AppIcons.Delete, stringResource(R.string.inbox_discard))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private val sample = BandInbox.Item("1", "a", "Celular da Ana", "LETRAS3\n12\nShow de sábado")

@LightPreview
@Composable
private fun InboxBannerLightPreview() = PreviewSurface(dark = false) { InboxBanner(sample, {}, {}) }

@DarkPreview
@Composable
private fun InboxBannerDarkPreview() = PreviewSurface(dark = true) { InboxBanner(sample, {}, {}) }
