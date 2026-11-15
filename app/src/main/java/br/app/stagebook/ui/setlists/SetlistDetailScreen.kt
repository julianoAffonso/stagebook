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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
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
import br.app.stagebook.ui.components.QuestionDialog
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
    onAddSongs: (String) -> Unit,
    onPlayAt: (setId: String, index: Int) -> Unit = { _, _ -> },   // tapping a song: its stage, on that song
    viewModel: SetlistDetailViewModel = hiltViewModel(),
    openShare: Boolean = false,
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
    // opened only to share: after a band send the result is shown and then the screen goes back. Leaving at the tap would
    // clear the view model, which is what sends, before it ended
    val onlyShare = rememberSaveable { openShare }
    BandResultMessage(viewModel.bandResult, onShown = { if (onlyShare) onBack() })
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
        onAddSongs = { onAddSongs(viewModel.setId) },
        onPlayAt = { onPlayAt(viewModel.setId, it) },
        pdfSizes = viewModel::pdfSizes,
        onShowQr = viewModel::showQr,
        onHideQr = viewModel::hideQr,
        onMove = viewModel::move,
        onRemove = viewModel::remove,
        onDeleteSetlist = viewModel::delete,   // the setlist goes away and the LaunchedEffect above comes back
        onAddBreak = viewModel::addBreak,
        onRemoveBreak = viewModel::removeBreak,
        onUndoRemove = viewModel::undoRemove,
        onRestore = viewModel::restore,
        onConduct = viewModel::conductBand,
        onClearPlayed = viewModel::clearPlayed,
        openShare = openShare,
    )
}

@Composable
fun SetlistDetailScreen(
    uiState: SetlistDetailUiState,
    qr: QrUi?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
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
    onAddBreak: () -> Unit = {},
    onRemoveBreak: (position: Int) -> Unit = {},   // the break before the song at that position
    onUndoRemove: () -> Unit = {},   // the last song taken out goes back to its place
    openShare: Boolean = false,   // from the stage's menu: opens with the Share sheet
    onPlayAt: (index: Int) -> Unit = {},   // a tap on a song (outside selecting): the stage opens on it
    pdfSizes: () -> List<Long> = { emptyList() },   // the PDFs the songs carry: the internet says if they fit
    onConduct: () -> Unit = {},   // the menu's "Lead the band"
    onClearPlayed: () -> Unit = {},   // the menu's "Clear played"
    startEditing: Boolean = false,   // opens in editing (tests, and a setlist just created)
    onRestore: (songIds: List<String>, breaks: List<Int>) -> Unit = { _, _ -> },   // "Discard": the setlist as it was when editing began
) {
    val wide = wideLists()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(openShare) }   // the "Share" sheet open
    // opened only to share (the list's or the stage's menu): when the sharing is over the screen goes back to where it came
    // from instead of staying in the setlist's edit
    val onlyShare = rememberSaveable { openShare }
    val doneSharing = { if (onlyShare) onBack() }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val message = br.app.stagebook.ui.components.LocalAppMessage.current
    // holding a song selects it (Google's pattern, as in the song list): the top bar removes them from the setlist
    val selection = br.app.stagebook.ui.components.rememberSelection()   // item keys
    var selected by selection.state
    var removingMany by rememberSaveable { mutableStateOf(false) }
    val selecting = selection.active
    // the setlist opens to be looked at; Edit turns on the handles, the removals and the adding, and Save closes that. Every
    // change is already in the library when made, so Discard puts back what it was when editing began
    var editing by rememberSaveable { mutableStateOf(startEditing) }
    var asking by rememberSaveable { mutableStateOf(false) }
    var before by remember { mutableStateOf<Pair<List<String>, List<Int>>?>(null) }
    var afterAsk by remember { mutableStateOf<(() -> Unit)?>(null) }
    val drawerGuard = br.app.stagebook.ui.drawer.LocalDrawerGuard.current
    val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
    val toggle = selection::toggle
    // local copy: the list changes right away during the drag; the order is saved once, on release (saving at every
    // position rewrote the library several times per drag), and the saved order comes back through uiState
    var rows by remember(uiState.items, uiState.breaks) { mutableStateOf(setRows(uiState.items, uiState.breaks)) }
    val latestRows by rememberUpdatedState(rows)
    val items = remember(rows) { rows.mapNotNull { (it as? SetRow.Song)?.item } }
    // something differs from when editing began (a screen that opened already editing has nothing to compare: it counts as changed)
    val changed = before?.let { it != (items.map { i -> i.songId } to uiState.breaks) } ?: true
    val changedNow by rememberUpdatedState(changed)
    val marks = remember(rows) { rowMarks(rows) }
    val breakCount = rows.size - items.size
    val dragStart = remember { DragStart() }
    var dragNow by remember { mutableStateOf(false) }
    // this screen is an action under way: while it is open the band's song doesn't take the follower to the stage (the strip
    // says so; FollowToStage holds any screen that is not Home or the stage). Every change but two is already in the library
    // when made (a break, a removal), so "Go" on the strip only asks when a drag is going on or the rename window is open:
    // the same question the song editor asks
    val unsaved by rememberUpdatedState(renaming || dragNow)
    var discarding by rememberSaveable { mutableStateOf(false) }
    var afterLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val leave = remember(onBack) { { onBack(); afterLeave?.invoke(); afterLeave = null } }
    val gate = br.app.stagebook.ui.components.LocalFollowGate.current
    androidx.compose.runtime.DisposableEffect(gate, leave) {
        val guard: (() -> Unit) -> Unit = { go -> afterLeave = go; if (unsaved) discarding = true else leave() }
        gate.leaveGuard = guard
        onDispose { if (gate.leaveGuard === guard) gate.leaveGuard = null }
    }
    fun finishEditing() { editing = false; asking = false; before = null; selected = emptyList(); drawerGuard?.asks = null }
    androidx.compose.runtime.DisposableEffect(drawerGuard, editing) {
        // the drawer closing (button, scrim, swipe, Back) while editing asks first; the answer closes it
        val asks: () -> Boolean = { if (editing && changedNow) { afterAsk = { drawer?.close?.invoke() }; asking = true; true } else false }
        if (editing) drawerGuard?.asks = asks
        onDispose { if (drawerGuard?.asks === asks) drawerGuard?.asks = null }
    }
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
                if (selecting && editing) {
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
                    ScreenHeader(
                        // the setlist's name on one line either way: "Edit setlist" over it took the room of the icons and broke in two
                        // (user, 15/11); editing shows in the ✕, the floppy and the handles
                        title = uiState.name,
                        subtitle = count,
                        onBack = if (editing) ({ if (changed) { afterAsk = null; asking = true } else finishEditing() }) else onBack,
                        closes = editing,
                        iconActions = true,
                        modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                    ) {
                        // all the setlist's own actions on this line, beside the back arrow, as SongbookPro's open set (user, 15/11):
                        // no buttons above and below at once
                        val canBreak = br.app.stagebook.domain.SetlistBreaks.canAdd(uiState.breaks, uiState.items.size)
                        SetActions(
                            editing, items.isNotEmpty(), onAddSongs, { sharing = true }, canBreak, onAddBreak,
                            onEdit = { before = items.map { it.songId } to uiState.breaks; editing = true },
                            onSave = { finishEditing() }, canSave = changed,
                            menu = listOfNotNull(
                                MenuAction(stringResource(R.string.conduct_start), AppIcons.Group, onConduct).takeIf { uiState.canConduct },
                                MenuAction(stringResource(R.string.set_clear_played), AppIcons.Undo, onClearPlayed).takeIf { uiState.anyPlayed },
                                MenuAction(stringResource(R.string.sets_rename), AppIcons.Rename, { renaming = true }),
                                MenuAction(stringResource(R.string.sets_delete), AppIcons.Delete, { deleting = true }, danger = true),
                            ),
                        )
                    }
                }
                Crossfade(targetState = uiState.loading, label = "set", modifier = Modifier.weight(1f)) { loading ->
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
                                            is SetRow.Break -> SetlistBreakRow(
                                                handle = if (editing) handle else null,
                                                onRemove = if (editing) ({ onRemoveBreak(mark.songsBefore) }) else null,
                                                dense = true,
                                                // an open break says its time, running or paused, as the stage's panel did
                                                timer = uiState.openBreaks[mark.songsBefore],
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
                                                    state = if (editing) SetlistShow.State.NONE else item.state,   // where the show is: played, now, next (hidden while editing)
                                                    onClick = { if (selecting) toggle(item.key) else if (!editing) onPlayAt(mark.songsBefore) },
                                                    modifier = Modifier.shadow(elevation, MaterialTheme.shapes.medium),
                                                    onLongClick = if (editing) ({ toggle(item.key) }) else null,
                                                    // the compact row of the stage's old panel (user, 15/11): more songs on screen
                                                    titleStyle = MaterialTheme.typography.titleMedium,
                                                    dense = true,
                                                    // the edit's handle sits at the row's very edge, in a narrow column
                                                    // (user, 13/11: the wide number and handle took the title's room)
                                                    stripRoom = false,
                                                    beforeNumber = if (!editing) null else if (selecting) ({ androidx.compose.material3.Checkbox(item.key in selected, { toggle(item.key) }) }) else ({ SetlistDragHandle() }),
                                                    dragZone = if (editing && !selecting) handle else null,
                                                    trailing = { Row(verticalAlignment = Alignment.CenterVertically) {
                                                        SongFacts(item.songKey, item.bpm, item.capo, item.hasTrack, hasPdf = item.hasPdf)
                                                        // out at once, with "Undo" in the message: a confirmation for
                                                        // every song made cleaning a setlist slow (user, 13/11)
                                                        if (editing) IconButton(onClick = {
                                                            onRemove(mark.songsBefore, item.songId)
                                                            message.show(left, long = true, action = undo, onAction = onUndoRemove)
                                                        }) {
                                                            Icon(AppIcons.RemoveCircle, stringResource(R.string.set_remove_song), tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    } },
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

    if (asking) {
        // the question of closing the menu (or leaving the edit) with the setlist half done
        QuestionDialog(
            stringResource(R.string.set_edit),
            stringResource(R.string.set_save_question, uiState.name),
            stringResource(R.string.action_save), { val go = afterAsk; afterAsk = null; finishEditing(); go?.invoke() },
            stringResource(R.string.set_discard), { val go = afterAsk; afterAsk = null; before?.let { onRestore(it.first, it.second) }; finishEditing(); go?.invoke() },
            onOutside = { asking = false; afterAsk = null },
            cancelLabel = stringResource(R.string.set_keep_editing), onCancel = { asking = false; afterAsk = null },
        )
    }
    if (discarding) {
        ConfirmDialog(
            stringResource(R.string.editor_discard_confirm),
            stringResource(R.string.editor_discard),
            { discarding = false; leave() },
            { discarding = false; afterLeave = null },
        )
    }
    if (renaming) {
        TextInputDialog(stringResource(R.string.sets_rename), uiState.name, stringResource(R.string.sets_name), onRename, { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(stringResource(R.string.sets_delete_confirm, uiState.name), stringResource(R.string.action_delete), onDeleteSetlist, { deleting = false })
    }
    if (removingMany) {
        val only = selected.singleOrNull()?.let { key -> items.firstOrNull { it.key == key } }
        ConfirmDialog(
            if (only != null) stringResource(R.string.set_remove_one_confirm, br.app.stagebook.ui.songs.shortName(only.title))
            else pluralStringResource(R.plurals.set_remove_many_confirm, selected.size, selected.size),
            stringResource(R.string.set_remove),
            {
                // from the end, so the positions of the others don't move; the repository checks each is still there
                items.withIndex().filter { it.value.key in selected }.sortedByDescending { it.index }.forEach { onRemove(it.index, it.value.songId) }
                selected = emptyList()
            },
            { removingMany = false },
        )
    }
    if (qr != null) QrDialog(qr, { onHideQr(); doneSharing() })
    if (sharing) {
        ShareSheet(
            songs = uiState.items.count { !it.missing },
            online = online,
            internetOn = internetOn,
            limits = br.app.stagebook.ui.share.ShareLimits(lists = 1, songs = uiState.items.count { !it.missing }, setlists = true, pdfSizes = pdfSizes()),
            onQr = { sharing = false; onShowQr() },
            onInternet = { withSongs -> sharing = false; onSendInternet(withSongs) },
            onDismiss = { sharing = false; doneSharing() },
            band = band,
            onBand = { withSongs -> sharing = false; onSendBand(withSongs) },
            onSearch = onBandSearch,
        )
    }
    cloud?.let { CloudDialog(it, { onDismissCloud(); doneSharing() }, onShareCode, onCopyCode) }
}

/**
 * The setlist's own actions as icons on the header's line: looking, Edit and Share; editing, Songs and Break (no Share: it
 * waits for Save) and the floppy (off until something changed). The ⋮ with what is rarely used closes the line.
 */
@Composable
private fun RowScope.SetActions(
    editing: Boolean, hasSongs: Boolean, onAddSongs: () -> Unit, onShare: () -> Unit, canBreak: Boolean, onAddBreak: () -> Unit,
    onEdit: () -> Unit, onSave: () -> Unit, canSave: Boolean, menu: List<MenuAction>,
) {
    if (editing) {
        IconButton(onClick = onAddSongs) { Icon(AppIcons.Add, stringResource(R.string.sets_add_songs), tint = MaterialTheme.colorScheme.onSurface) }
        val message = br.app.stagebook.ui.components.LocalAppMessage.current
        val why = stringResource(R.string.sets_break_needs_two)
        val breakName = stringResource(R.string.sets_add_break)
        val breakLabel = if (canBreak) breakName else "$breakName. $why"
        // off but still touchable: the touch says why, as the first one on the off button met nothing (user test, 14/11)
        IconButton(onClick = { if (canBreak) onAddBreak() else message.show(why) }, modifier = Modifier.semantics { if (!canBreak) disabled() }) {
            Icon(AppIcons.Pause, breakLabel, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canBreak) 1f else 0.38f))
        }
        br.app.stagebook.ui.components.TipIcon(stringResource(R.string.action_save), AppIcons.Save, onSave, tint = MaterialTheme.colorScheme.primary, enabled = canSave)
    } else {
        IconButton(onClick = onEdit) { Icon(AppIcons.Edit, stringResource(R.string.set_edit), tint = MaterialTheme.colorScheme.onSurface) }
        // sharing is for the finished setlist: not while it is half edited (what Discard undoes would have gone out)
        IconButton(onClick = onShare, enabled = hasSongs) {
            Icon(AppIcons.Share, stringResource(R.string.share_title), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hasSongs) 1f else 0.38f))
        }
    }
    OverflowMenu(menu)
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
    SetlistDetailScreen(previewState, null, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
}

@DarkPreview
@Composable
private fun SetlistDetailDarkPreview() = StagebookTheme(darkTheme = true) {
    SetlistDetailScreen(previewState, null, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
}

/** Where the dragged row came from (saved once, on release). */
private class DragStart {
    var key: String? = null
    var index = -1
}
