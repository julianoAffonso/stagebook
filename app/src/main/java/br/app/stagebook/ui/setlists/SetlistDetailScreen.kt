package br.app.stagebook.ui.setlists

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
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
import br.app.stagebook.R
import br.app.stagebook.ui.share.CloudDialog
import br.app.stagebook.ui.share.CloudUi
import br.app.stagebook.ui.share.ShareSheet
import br.app.stagebook.ui.share.BandResultMessage
import br.app.stagebook.ui.share.rememberCodeActions
import br.app.stagebook.ui.components.InfoDialog
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.TipIcon
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.NumberBadge
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.components.TextInputDialog
import br.app.stagebook.ui.components.wideLists
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetlistDetailRoute(
    onBack: () -> Unit,
    onPlay: (setId: String, start: Int) -> Unit,
    onAddSongs: (String) -> Unit,
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
    val internetOn by viewModel.internetOn.collectAsStateWithLifecycle()
    val cloud by viewModel.cloud.collectAsStateWithLifecycle()
    val codeActions = rememberCodeActions()
    val bandOnline by viewModel.bandOnline.collectAsStateWithLifecycle()
    BandResultMessage(viewModel.bandResult)
    SetlistDetailScreen(
        uiState = uiState,
        qr = qr,
        online = online,
        internetOn = internetOn,
        cloud = cloud,
        onSendInternet = viewModel::sendInternet,
        onDismissCloud = viewModel::dismissCloud,
        // the code goes through any messaging app on the device (WhatsApp, email, SMS…)
        onCopyCode = codeActions.copy,
        band = bandOnline,
        onSendBand = viewModel::sendBand,
        onBandSearch = viewModel::bandSearch,
        onShareCode = codeActions.share,
        onBack = onBack,
        onRename = viewModel::rename,
        onPlay = { start -> onPlay(viewModel.setId, start) },
        onConduct = { viewModel.conductBand(); onPlay(viewModel.setId, 0) },
        onAddSongs = { onAddSongs(viewModel.setId) },
        onShowQr = viewModel::showQr,
        onHideQr = viewModel::hideQr,
        onMove = viewModel::move,
        onRemove = viewModel::remove,
        onDeleteSetlist = viewModel::delete,   // the setlist goes away and the LaunchedEffect above comes back
    )
}

@Composable
fun SetlistDetailScreen(
    uiState: SetlistDetailUiState,
    qr: QrUi?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onPlay: (start: Int) -> Unit,   // tap on a song: opens the stage on it
    onAddSongs: () -> Unit,
    onShowQr: () -> Unit,
    onHideQr: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int, String) -> Unit,
    onDeleteSetlist: () -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    internetOn: Boolean = true,
    cloud: CloudUi? = null,
    onSendInternet: (withSongs: Boolean) -> Unit = {},
    onDismissCloud: () -> Unit = {},
    onShareCode: (name: String, code: String) -> Unit = { _, _ -> },
    onCopyCode: (code: String) -> Unit = {},
    band: Int = 0,
    onSendBand: (withSongs: Boolean) -> Unit = {},
    onBandSearch: (Boolean) -> Unit = {},
    onConduct: () -> Unit = {},
) {
    val wide = wideLists()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }   // the "Share" sheet open
    var deleting by rememberSaveable { mutableStateOf(false) }
    // holding a song selects it (Google's pattern, as in the song list): the top bar removes them from the setlist
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }   // item keys
    var removingMany by rememberSaveable { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptyList() }
    // local copy: the list changes right away during the drag; the order is saved once, on release (saving at every
    // position rewrote the library several times per drag), and the saved order comes back through uiState
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
                if (selecting) {
                    br.app.stagebook.ui.components.SelectionTopBar(
                        count = selected.size,
                        onClose = { selected = emptyList() },
                        actions = listOf(br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.set_remove), AppIcons.Delete) { removingMany = true }),
                        onSelectAll = { selected = items.map { it.key } },
                        modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                    )
                } else ScreenHeader(
                    title = uiState.name,
                    subtitle = pluralStringResource(R.plurals.song_count, items.size, items.size).let { count ->
                        if (items.isNotEmpty()) stringResource(R.string.set_tap_to_play, count) else count
                    },
                    onBack = onBack,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                ) {
                    // small play: the stage from the start (tapping a song opens on it)
                    IconButton(onClick = { onPlay(0) }, enabled = items.isNotEmpty()) {
                        Icon(AppIcons.Slideshow, stringResource(R.string.action_stage), tint = MaterialTheme.colorScheme.primary)
                    }
                    // everything in view, no ⋮ (user, 01/11, as in the song editor); holding an icon shows its name.
                    // Conducting the band only with a band member and songs
                    if (band > 0 && items.isNotEmpty()) TipIcon(stringResource(R.string.conduct_start), AppIcons.Group, onConduct)
                    TipIcon(stringResource(R.string.sets_rename), AppIcons.Edit) { renaming = true }
                    IconButton(onClick = { deleting = true }) {
                        Icon(AppIcons.Delete, stringResource(R.string.sets_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (!selecting) SetActions(items.isNotEmpty(), onAddSongs, { sharing = true }, Modifier.padding(bottom = Spacing.m))
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
                                        onClick = { if (selecting) toggle(item.key) else onPlay(index) },
                                        onLongClick = { toggle(item.key) },
                                        modifier = Modifier.shadow(elevation, MaterialTheme.shapes.medium),
                                        titleStyle = MaterialTheme.typography.titleLarge,
                                        inline = wide,
                                        // YouTube Music pattern: the drag handle at the start of the row and the ⋮
                                        // alone at the end
                                        leading = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (selecting) androidx.compose.material3.Checkbox(item.key in selected, { toggle(item.key) })
                                                else IconButton(
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
                                        // editing is on the stage's pencil; removing is done by selecting (user, 01/11)
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
    if (removingMany) {
        ConfirmDialog(
            pluralStringResource(R.plurals.set_remove_many_confirm, selected.size, selected.size),
            stringResource(R.string.set_remove),
            {
                // from the end, so the positions of the others don't move; the repository checks each is still there
                items.withIndex().filter { it.value.key in selected }.sortedByDescending { it.index }.forEach { onRemove(it.index, it.value.songId) }
                selected = emptyList()
            },
            { removingMany = false },
        )
    }
    if (qr != null) QrDialog(qr, onHideQr)
    if (sharing) {
        ShareSheet(
            songs = uiState.items.count { !it.missing },
            online = online,
            internetOn = internetOn,
            onQr = { sharing = false; onShowQr() },
            onInternet = { withSongs -> sharing = false; onSendInternet(withSongs) },
            onDismiss = { sharing = false },
            band = band,
            onBand = { withSongs -> sharing = false; onSendBand(withSongs) },
            onSearch = onBandSearch,
        )
    }
    cloud?.let { CloudDialog(it, onDismissCloud, onShareCode, onCopyCode) }
}

/** Songs and Share side by side (the stage opens by tapping a song in the list). */
@Composable
private fun SetActions(hasSongs: Boolean, onAddSongs: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // both secondary: side by side at the start (stretched, Share floated in the middle)
        ActionButton(stringResource(R.string.sets_add_songs), onAddSongs, icon = AppIcons.Add, tone = ButtonTone.Tonal)
        ActionButton(stringResource(R.string.share_title), onShare, icon = AppIcons.Share, tone = ButtonTone.Tonal, enabled = hasSongs)
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
    SetlistDetailScreen(previewState, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
}

@DarkPreview
@Composable
private fun SetlistDetailDarkPreview() = StagebookTheme(darkTheme = true) {
    SetlistDetailScreen(previewState, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
}

/** Where the dragged song came from (saved once, on release). */
private class DragStart {
    var key: String? = null
    var index = -1
}
