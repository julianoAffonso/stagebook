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
import br.app.stagebook.ui.components.SetlistBreakRow
import br.app.stagebook.ui.components.SetlistDragHandle
import br.app.stagebook.ui.components.SongFacts
import br.app.stagebook.ui.components.SetlistSongRow
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.components.TextInputDialog
import br.app.stagebook.ui.components.wideLists
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing
import br.app.stagebook.domain.SetlistBreaks
import br.app.stagebook.domain.SetlistShow
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import androidx.compose.foundation.layout.size
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetlistDetailRoute(
    onBack: () -> Unit,
    onPlay: (setId: String, start: Int) -> Unit,
    onPlayBreak: (setId: String, start: Int) -> Unit = { _, _ -> },
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
        onOpenBreak = { songsBefore -> onPlayBreak(viewModel.setId, songsBefore - 1) },
        onConduct = { viewModel.conductBand(); onPlay(viewModel.setId, 0) },
        onAddSongs = { onAddSongs(viewModel.setId) },
        onShowQr = viewModel::showQr,
        onHideQr = viewModel::hideQr,
        onMove = viewModel::move,
        onRemove = viewModel::remove,
        onDeleteSetlist = viewModel::delete,   // the setlist goes away and the LaunchedEffect above comes back
        onAddBreak = viewModel::addBreak,
        onRemoveBreak = viewModel::removeBreak,
        onUndoRemove = viewModel::undoRemove,
        onClearPlayed = viewModel::clearPlayed,
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
    onMove: (Int, Int) -> Unit,   // a row dragged: positions on screen, songs and breaks counted together
    onRemove: (Int, String) -> Unit,
    onDeleteSetlist: () -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    internetOn: Boolean = true,
    cloud: CloudUi? = null,
    onSendInternet: (withSongs: Boolean) -> Unit = {},
    onDismissCloud: () -> Unit = {},
    onShareCode: (br.app.stagebook.ui.share.CloudUi.Sent) -> Unit = {},
    onCopyCode: (code: String) -> Unit = {},
    band: Int = 0,
    onSendBand: (withSongs: Boolean) -> Unit = {},
    onBandSearch: (Boolean) -> Unit = {},
    onConduct: () -> Unit = {},
    onAddBreak: () -> Unit = {},
    onRemoveBreak: (position: Int) -> Unit = {},   // the break before the song at that position
    onUndoRemove: () -> Unit = {},   // the last song taken out goes back to its place
    onClearPlayed: () -> Unit = {},
    onOpenBreak: (songsBefore: Int) -> Unit = {},   // tap on the break left open on the stage: opens it, with its time
) {
    val wide = wideLists()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }   // the "Share" sheet open
    var deleting by rememberSaveable { mutableStateOf(false) }
    // two faces: the show (what was played, what's next, nothing to move by accident) and the edit (drag and remove)
    var editing by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }
    val message = br.app.stagebook.ui.components.LocalAppMessage.current
    // holding a song selects it (Google's pattern, as in the song list): the top bar removes them from the setlist
    val selection = br.app.stagebook.ui.components.rememberSelection()   // item keys
    var selected by selection.state
    var removingMany by rememberSaveable { mutableStateOf(false) }
    val selecting = selection.active
    val toggle = selection::toggle
    // local copy: the list changes right away during the drag; the order is saved once, on release (saving at every
    // position rewrote the library several times per drag), and the saved order comes back through uiState
    var rows by remember(uiState.items, uiState.breaks) { mutableStateOf(setRows(uiState.items, uiState.breaks)) }
    val latestRows by rememberUpdatedState(rows)
    val items = remember(rows) { rows.mapNotNull { (it as? SetRow.Song)?.item } }
    val marks = remember(rows) { rowMarks(rows) }
    val breakCount = rows.size - items.size
    val dragStart = remember { DragStart() }
    var dragNow by remember { mutableStateOf(false) }
    // busy here holds a band follower on this screen until done (a rename or a reorder isn't lost; audit 02/11)
    val followHold = br.app.stagebook.ui.components.LocalFollowHold.current
    val busy = renaming || sharing || deleting || removingMany || selecting || dragNow || editing
    LaunchedEffect(busy) { followHold.value = busy }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { followHold.value = false } }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    fun dropped() {
        dragNow = false
        val key = dragStart.key ?: return
        dragStart.key = null
        val to = latestRows.indexOfFirst { it.key == key }
        // a break let go at the top, at the bottom or beside another one goes where it will be saved
        rows = settled(latestRows)
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
                } else {
                    val count = br.app.stagebook.ui.components.songCountText(items.size).let { songs ->
                        if (breakCount == 0) songs
                        else stringResource(R.string.set_count_with_breaks, songs, pluralStringResource(R.plurals.break_count, breakCount, breakCount))
                    }
                    if (editing) ScreenHeader(
                        title = stringResource(R.string.set_edit),
                        subtitle = count,
                        onBack = { editing = false },
                        closes = true,
                        iconActions = false,
                        modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                    ) {
                        TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_done)) }
                    } else ScreenHeader(
                        title = uiState.name,
                        // with breaks the line is already long: the hint would wrap on a phone
                        subtitle = if (items.isNotEmpty() && breakCount == 0) stringResource(R.string.set_tap_to_play, count) else count,
                        onBack = onBack,
                        // tapping the name renames it, like a song's name in its editor (user, 01/11)
                        onTitleClick = { renaming = true },
                        titleClickLabel = stringResource(R.string.sets_rename),
                        modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                    ) {
                        IconButton(onClick = { editing = true }) { Icon(AppIcons.Edit, stringResource(R.string.set_edit)) }
                        // small play: the stage from the start (tapping a song opens on it)
                        IconButton(onClick = { onPlay(0) }, enabled = items.isNotEmpty()) {
                            Icon(AppIcons.Slideshow, stringResource(R.string.action_stage), tint = MaterialTheme.colorScheme.primary)
                        }
                        // Conducting the band only with a band member and songs
                        if (band > 0 && items.isNotEmpty()) TipIcon(stringResource(R.string.conduct_start), AppIcons.Group, onConduct)
                        // what is rarely used, and deleting, away from the thumb during a show (user, 13/11)
                        OverflowMenu(
                            listOfNotNull(
                                MenuAction(stringResource(R.string.sets_rename), AppIcons.Rename, { renaming = true }),
                                MenuAction(stringResource(R.string.set_clear_played), AppIcons.Undo, onClearPlayed).takeIf { uiState.anyPlayed },
                                MenuAction(stringResource(R.string.sets_delete), AppIcons.Delete, { deleting = true }, danger = true),
                            ),
                        )
                    }
                }
                if (!selecting && !editing) {
                    val canBreak = br.app.stagebook.domain.SetlistBreaks.canAdd(uiState.breaks, uiState.items.size)
                    SetActions(items.isNotEmpty(), onAddSongs, { sharing = true }, canBreak, onAddBreak, Modifier.padding(bottom = Spacing.m))
                }
                Crossfade(targetState = uiState.loading, label = "set") { loading ->
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        items.isEmpty() -> EmptyState(AppIcons.QueueMusic, stringResource(R.string.set_empty_title), stringResource(R.string.set_empty_message))
                        else -> LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(if (wide) Spacing.xs else Spacing.s),
                            contentPadding = PaddingValues(bottom = Spacing.l),
                        ) {
                            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                                val mark = marks[index]
                                ReorderableItem(reorderState, key = row.key) { dragging ->
                                    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "drag")
                                    val handle = Modifier.draggableHandle(
                                        onDragStarted = { dragNow = true; dragStart.key = row.key; dragStart.index = latestRows.indexOfFirst { it.key == row.key } },
                                        onDragStopped = { dropped() },
                                    )
                                    Column {
                                        // with breaks, each block says its number and how many songs it has
                                        if (mark.block > 0) BlockHeader(mark.block, mark.blockSongs)
                                        when (row) {
                                            // outside the edit a break only shows: nothing to drag or remove
                                            is SetRow.Break -> SetlistBreakRow(
                                                handle = handle.takeIf { editing },
                                                onRemove = { onRemoveBreak(mark.songsBefore) }.takeIf { editing },
                                                timer = uiState.openBreaks[mark.songsBefore].takeIf { !editing && !selecting },
                                                onOpen = { onOpenBreak(mark.songsBefore) },
                                                modifier = Modifier.shadow(elevation, MaterialTheme.shapes.medium),
                                            )
                                            is SetRow.Song -> {
                                                val item = row.item
                                                val title = if (item.missing) stringResource(R.string.set_item_missing) else item.title
                                                val left = stringResource(R.string.set_song_left, title)
                                                val undo = stringResource(R.string.action_undo)
                                                SetlistSongRow(
                                                    // the numbers go on counting past a break
                                                    number = mark.songsBefore + 1,
                                                    title = title,
                                                    subtitle = item.subtitle,
                                                    state = if (editing) SetlistShow.State.NONE else item.state,
                                                    onClick = { if (selecting) toggle(item.key) else if (!editing) onPlay(mark.songsBefore) },
                                                    modifier = Modifier.shadow(elevation, MaterialTheme.shapes.medium),
                                                    onLongClick = { toggle(item.key) }.takeUnless { editing },
                                                    inline = wide,
                                                    // the edit's handle sits at the row's very edge, in a narrow column
                                                    // (user, 13/11: the wide number and handle took the title's room)
                                                    stripRoom = !editing,
                                                    beforeNumber = when {
                                                        selecting -> ({ androidx.compose.material3.Checkbox(item.key in selected, { toggle(item.key) }) })
                                                        editing -> ({ SetlistDragHandle(handle) })
                                                        else -> null
                                                    },
                                                    trailing = {
                                                        // out at once, with "Undo" in the message: a confirmation for
                                                        // every song made cleaning a setlist slow (user, 13/11)
                                                        if (editing) IconButton(onClick = {
                                                            onRemove(mark.songsBefore, item.songId)
                                                            message.show(left, long = true, action = undo, onAction = onUndoRemove)
                                                        }) {
                                                            Icon(AppIcons.RemoveCircle, stringResource(R.string.set_remove_song), tint = MaterialTheme.colorScheme.error)
                                                        } else SongFacts(item.songKey, item.bpm, item.capo, item.hasTrack)
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

/** Songs, Break and Share side by side (the stage opens by tapping a song in the list). */
@Composable
private fun SetActions(hasSongs: Boolean, onAddSongs: () -> Unit, onShare: () -> Unit, canBreak: Boolean, onAddBreak: () -> Unit, modifier: Modifier = Modifier) {
    // three buttons don't fit one line on a phone with large text ("Compartilha / r", 13/11): the one that doesn't fit
    // goes whole to the next line
    androidx.compose.foundation.layout.FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // all secondary: side by side at the start (stretched, Share floated in the middle)
        ActionButton(stringResource(R.string.sets_add_songs), onAddSongs, icon = AppIcons.Add, tone = ButtonTone.Tonal)
        // a break sits between two songs: with fewer than two there's no place for it
        ActionButton(stringResource(R.string.sets_add_break), onAddBreak, icon = AppIcons.Add, tone = ButtonTone.Tonal, enabled = canBreak,
            disabledHint = stringResource(R.string.sets_break_needs_two))
        ActionButton(stringResource(R.string.share_title), onShare, icon = AppIcons.Share, tone = ButtonTone.Tonal, enabled = hasSongs)
    }
}

/** A row of the setlist: a song or a break. [key] is stable while dragging. */
private sealed interface SetRow {
    val key: String
    data class Song(val item: SetItemUi) : SetRow { override val key: String get() = item.key }
    data class Break(override val key: String) : SetRow
}

/** What a row shows besides itself: how many songs come before it and, on the first song of a block, the block. */
private class RowMark(val songsBefore: Int, val block: Int, val blockSongs: Int)

private fun setRows(items: List<SetItemUi>, breaks: List<Int>): List<SetRow> {
    var n = 0
    return SetlistBreaks.rows(items.size, breaks).map { if (it == SetlistBreaks.BREAK) SetRow.Break("break:${n++}") else SetRow.Song(items[it]) }
}

/** The rows as they will be saved (a break never at the top, at the bottom or beside another one). */
private fun settled(rows: List<SetRow>): List<SetRow> {
    val songs = rows.mapNotNull { (it as? SetRow.Song)?.item }
    var n = 0
    val breaks = SetlistBreaks.split(rows.map { if (it is SetRow.Break) SetlistBreaks.BREAK else n++ }).breaks
    return setRows(songs, breaks).takeIf { it != rows } ?: rows
}

private fun rowMarks(rows: List<SetRow>): List<RowMark> {
    val headers = rows.any { it is SetRow.Break }
    var songs = 0
    var block = 0
    return rows.mapIndexed { i, row ->
        // the first song after a break (or of the setlist) opens a block
        val opens = headers && row is SetRow.Song && (i == 0 || rows[i - 1] is SetRow.Break)
        if (opens) block++
        val inBlock = if (opens) rows.subList(i, rows.size).takeWhile { it is SetRow.Song }.size else 0
        RowMark(songs, if (opens) block else 0, inBlock).also { if (row is SetRow.Song) songs++ }
    }
}

/** "SET 1 · 3 songs", over the first song of each block. */
@Composable
private fun BlockHeader(block: Int, songs: Int, modifier: Modifier = Modifier) {
    Text(
        pluralStringResource(R.plurals.set_block, songs, block, songs),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.m, top = Spacing.s, bottom = Spacing.s),
    )
}

@Composable
private fun QrDialog(qr: QrUi, onDismiss: () -> Unit) {
    br.app.stagebook.ui.components.FollowBusyWhile()
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
        SetItemUi("1", "1", "Malandragem", "Cássia Eller · 1994", false, songKey = "G", bpm = 96, state = SetlistShow.State.PLAYED),
        SetItemUi("2", "2", "Hazard", "Richard Marx · 1991", false, songKey = "Em", bpm = 120, capo = 2, hasTrack = true, state = SetlistShow.State.NOW),
        SetItemUi("3", "3", "", "", true, state = SetlistShow.State.NEXT),
    ),
    breaks = listOf(2),
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

/** Where the dragged row came from (saved once, on release). */
private class DragStart {
    var key: String? = null
    var index = -1
}
