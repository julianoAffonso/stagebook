package com.juliano.stagebook.ui.setlists

import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.components.InfoDialog
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.MenuAction
import com.juliano.stagebook.ui.components.OverflowMenu
import com.juliano.stagebook.ui.components.ButtonTone
import com.juliano.stagebook.ui.components.CenteredContent
import com.juliano.stagebook.ui.components.ConfirmDialog
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.EmptyState
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.NumberBadge
import com.juliano.stagebook.ui.components.ScreenHeader
import com.juliano.stagebook.ui.components.ScreenInsets
import com.juliano.stagebook.ui.components.SongListItem
import com.juliano.stagebook.ui.components.TextInputDialog
import com.juliano.stagebook.ui.components.wideLists
import com.juliano.stagebook.ui.theme.StagebookTheme
import com.juliano.stagebook.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetlistDetailRoute(
    onBack: () -> Unit,
    onPlay: (setId: String, start: Int) -> Unit,
    onAddSongs: (String) -> Unit,
    onEditSong: (String) -> Unit,
    viewModel: SetlistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val qr by viewModel.qr.collectAsStateWithLifecycle()
    val limit by viewModel.limit.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.loading, uiState.exists) { if (!uiState.loading && !uiState.exists) onBack() }
    limit?.let {
        InfoDialog(
            stringResource(R.string.qr_limit_title),
            stringResource(if (it == SetlistDetailViewModel.LimitNotice.Name) R.string.qr_limit_name else R.string.qr_limit_message),
            viewModel::dismissLimit,
        )
    }
    val online by viewModel.online.collectAsStateWithLifecycle()
    val cloud by viewModel.cloud.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareText = stringResource(R.string.share_message)
    SetlistDetailScreen(
        uiState = uiState,
        qr = qr,
        online = online,
        cloud = cloud,
        onSendInternet = viewModel::sendInternet,
        onDismissCloud = viewModel::dismissCloud,
        // o código vai por qualquer app de mensagem do aparelho (WhatsApp, e-mail, SMS…)
        onCopyCode = { code ->
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Stagebook", code))
            Toast.makeText(context, R.string.share_copied, Toast.LENGTH_SHORT).show()
        },
        onShareCode = { name, code ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText.format(name, code))
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
        },
        onBack = onBack,
        onRename = viewModel::rename,
        onPlay = { start -> onPlay(viewModel.setId, start) },
        onAddSongs = { onAddSongs(viewModel.setId) },
        onShowQr = viewModel::showQr,
        onHideQr = viewModel::hideQr,
        onMove = viewModel::move,
        onEditSong = onEditSong,
        onRemove = viewModel::remove,
        onDeleteSetlist = viewModel::delete,   // o setlist some e o LaunchedEffect acima volta
    )
}

@Composable
fun SetlistDetailScreen(
    uiState: SetlistDetailUiState,
    qr: QrUi?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onPlay: (start: Int) -> Unit,   // toque numa música: abre o palco nela
    onAddSongs: () -> Unit,
    onShowQr: () -> Unit,
    onHideQr: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onEditSong: (String) -> Unit,
    onRemove: (Int, String) -> Unit,
    onDeleteSetlist: () -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    cloud: CloudUi? = null,
    onSendInternet: (withSongs: Boolean) -> Unit = {},
    onDismissCloud: () -> Unit = {},
    onShareCode: (name: String, code: String) -> Unit = { _, _ -> },
    onCopyCode: (code: String) -> Unit = {},
) {
    val wide = wideLists()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }   // a folha "Compartilhar" aberta
    var deleting by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<Int?>(null) }   // posição da música a tirar (confirma antes)
    // cópia local: a lista muda na hora durante o arrasto; a ordem é gravada uma vez, ao soltar (gravar a
    // cada posição regravava a biblioteca várias vezes por arrasto), e a ordem salva volta pelo uiState
    var items by remember(uiState.items) { mutableStateOf(uiState.items) }
    val latestItems by rememberUpdatedState(items)
    val dragStart = remember { DragStart() }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    fun dropped() {
        val key = dragStart.key ?: return
        dragStart.key = null
        val to = latestItems.indexOfFirst { it.key == key }
        if (to >= 0 && to != dragStart.index) onMove(dragStart.index, to)
    }

    Scaffold(contentWindowInsets = ScreenInsets, modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { inner ->
        CenteredContent(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = Spacing.m)) {
                ScreenHeader(
                    title = uiState.name,
                    subtitle = pluralStringResource(R.plurals.song_count, items.size, items.size).let { count ->
                        if (items.isNotEmpty()) stringResource(R.string.set_tap_to_play, count) else count
                    },
                    onBack = onBack,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                ) {
                    // play pequeno: o palco do começo (tocar numa música abre nela)
                    IconButton(onClick = { onPlay(0) }, enabled = items.isNotEmpty()) {
                        Icon(AppIcons.Play, stringResource(R.string.action_stage), tint = MaterialTheme.colorScheme.primary)
                    }
                    // renomear e apagar no ⋮ (padrão do Google): o nome do setlist não corta no celular
                    OverflowMenu(
                        listOf(
                            MenuAction(stringResource(R.string.sets_rename), AppIcons.Edit, { renaming = true }),
                            MenuAction(stringResource(R.string.sets_delete), AppIcons.Delete, { deleting = true }, danger = true),
                        ),
                    )
                }
                SetActions(items.isNotEmpty(), onAddSongs, { sharing = true }, Modifier.padding(bottom = Spacing.m))
                Crossfade(targetState = uiState.loading, label = "set") { loading ->
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        items.isEmpty() -> EmptyState(AppIcons.QueueMusic, stringResource(R.string.set_empty_title), stringResource(R.string.set_empty_message))
                        else -> LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(if (wide) Spacing.xs else Spacing.s),
                            contentPadding = PaddingValues(bottom = Spacing.l),
                        ) {
                            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                                ReorderableItem(reorderState, key = item.key) { dragging ->
                                    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "drag")
                                    SongListItem(
                                        title = if (item.missing) stringResource(R.string.set_item_missing) else item.title,
                                        subtitle = item.subtitle,
                                        onClick = { onPlay(index) },
                                        modifier = Modifier.shadow(elevation, MaterialTheme.shapes.medium),
                                        titleStyle = MaterialTheme.typography.titleLarge,
                                        inline = wide,
                                        // padrão do YouTube Music: a alça de arrastar no começo da linha e o ⋮ sozinho na ponta
                                        leading = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {},
                                                    modifier = Modifier.draggableHandle(
                                                        onDragStarted = { dragStart.key = item.key; dragStart.index = latestItems.indexOfFirst { it.key == item.key } },
                                                        onDragStopped = { dropped() },
                                                    ),
                                                ) {
                                                    Icon(AppIcons.DragHandle, stringResource(R.string.set_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                NumberBadge(index + 1)
                                            }
                                        },
                                        trailing = {
                                            // editar a música e tirar do setlist no ⋮ (padrão do Google)
                                            OverflowMenu(
                                                buildList {
                                                    if (!item.missing) add(MenuAction(stringResource(R.string.action_edit), AppIcons.Edit, { onEditSong(item.songId) }))
                                                    add(MenuAction(stringResource(R.string.set_remove), AppIcons.Delete, { removing = index }, danger = true))
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(stringResource(R.string.sets_rename), uiState.name, stringResource(R.string.sets_name), onRename, { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(stringResource(R.string.sets_delete_confirm, uiState.name), stringResource(R.string.action_delete), onDeleteSetlist, { deleting = false })
    }
    removing?.let { at ->
        items.getOrNull(at)?.let { item ->
            val title = if (item.missing) stringResource(R.string.set_item_missing) else item.title
            // a repository só tira se ainda for a mesma música nessa posição
            ConfirmDialog(stringResource(R.string.set_remove_confirm, title), stringResource(R.string.set_remove), { onRemove(at, item.songId) }, { removing = null })
        } ?: run { removing = null }
    }
    if (qr != null) QrDialog(qr, onHideQr)
    if (sharing) {
        ShareSheet(
            songs = uiState.items.count { !it.missing },
            online = online,
            onQr = { sharing = false; onShowQr() },
            onInternet = { withSongs -> sharing = false; onSendInternet(withSongs) },
            onDismiss = { sharing = false },
        )
    }
    cloud?.let { CloudDialog(it, onDismissCloud, onShareCode, onCopyCode) }
}

/** Músicas e Compartilhar lado a lado (o palco abre tocando numa música da lista). */
@Composable
private fun SetActions(hasSongs: Boolean, onAddSongs: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ActionButton(stringResource(R.string.sets_add_songs), onAddSongs, Modifier.weight(1f), icon = AppIcons.Add, tone = ButtonTone.Tonal)
        ActionButton(stringResource(R.string.share_title), onShare, Modifier.weight(1f), icon = AppIcons.Upload, tone = ButtonTone.Tonal, enabled = hasSongs)
    }
}

/** A folha "Compartilhar": cada jeito de enviar só aparece quando dá para usar (QR sempre; internet só com internet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(songs: Int, online: Boolean, onQr: () -> Unit, onInternet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShareOptions(songs, online, onQr, onInternet, Modifier.padding(bottom = Spacing.l))
    }
}

/** O conteúdo da folha (separado para o preview e os prints). */
@Composable
fun ShareOptions(songs: Int, online: Boolean, onQr: () -> Unit, onInternet: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var withSongs by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = Spacing.s))
        ShareOption(AppIcons.QrCode, stringResource(R.string.share_qr), stringResource(R.string.share_qr_hint), onQr)
        if (online) {
            ShareOption(AppIcons.Public, stringResource(R.string.share_internet), stringResource(R.string.share_internet_hint)) { onInternet(withSongs) }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pluralStringResource(R.plurals.share_with_songs, songs, songs), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.share_with_songs_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(withSongs, { withSongs = it })
            }
        } else {
            Text(stringResource(R.string.share_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// enviando, o código pronto (com "Mandar por mensagem") ou o erro
@Composable
private fun CloudDialog(cloud: CloudUi, onDismiss: () -> Unit, onShareCode: (String, String) -> Unit, onCopyCode: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (cloud != CloudUi.Sending) onDismiss() },
        title = {
            Text(
                stringResource(
                    when (cloud) {
                        CloudUi.Sending -> R.string.share_sending
                        is CloudUi.Sent -> R.string.share_sent
                        CloudUi.Failed -> R.string.share_failed
                    },
                ),
            )
        },
        text = {
            when (cloud) {
                CloudUi.Sending -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                CloudUi.Failed -> Text(stringResource(R.string.share_failed_text), style = MaterialTheme.typography.bodyLarge)
                is CloudUi.Sent -> CodeBox(cloud, onCopy = { onCopyCode(cloud.code) })
            }
        },
        confirmButton = {
            if (cloud is CloudUi.Sent) TextButton(onClick = { onShareCode(cloud.name, cloud.code) }) { Text(stringResource(R.string.share_send_code)) }
            else if (cloud == CloudUi.Failed) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = { if (cloud is CloudUi.Sent) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** O código grande (para ditar), o QR dele (para foto ou "Ler QR"), Copiar, e o que foi junto. */
@Composable
fun CodeBox(sent: CloudUi.Sent, onCopy: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        sent.qr?.let { img ->
            Image(
                bitmap = img,
                contentDescription = stringResource(R.string.share_code_qr),
                modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(Color.White),
            )
        }
        SelectionContainer {
            Text(sent.code, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        ActionButton(stringResource(R.string.share_copy), onCopy, icon = AppIcons.Copy, tone = ButtonTone.Tonal)
        Text(pluralStringResource(R.plurals.share_sent_songs, sent.songs, sent.songs), style = MaterialTheme.typography.bodyMedium)
        if (sent.skipped > 0) {
            Text(pluralStringResource(R.plurals.share_skipped, sent.skipped, sent.skipped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.share_sent_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QrDialog(qr: QrUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(qr.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qr.image,
                    contentDescription = stringResource(R.string.qr_description),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White),
                )
                Text(
                    pluralStringResource(R.plurals.qr_count, qr.count, qr.count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (qr.missing > 0) {
                    Text(
                        pluralStringResource(R.plurals.qr_missing, qr.missing, qr.missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.qr_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private val previewState = SetlistDetailUiState(
    loading = false,
    name = "Show de sábado",
    items = listOf(
        SetItemUi("1", "1", "Malandragem", "Cássia Eller · 1994", false),
        SetItemUi("2", "2", "Hazard", "Richard Marx · 1991", false),
        SetItemUi("3", "3", "", "", true),
    ),
)

@LightPreview
@Composable
private fun SetlistDetailLightPreview() = StagebookTheme(darkTheme = false) {
    SetlistDetailScreen(previewState, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {})
}

@DarkPreview
@Composable
private fun SetlistDetailDarkPreview() = StagebookTheme(darkTheme = true) {
    SetlistDetailScreen(previewState, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {})
}

/** De onde saiu a música sendo arrastada (gravada uma vez, ao soltar). */
private class DragStart {
    var key: String? = null
    var index = -1
}
