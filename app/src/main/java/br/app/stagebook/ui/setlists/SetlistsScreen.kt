package br.app.stagebook.ui.setlists

import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.InfoDialog
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing

@Composable
fun SetlistsRoute(
    onOpen: (String) -> Unit,
    onPlay: (setId: String, start: Int, atBreak: Boolean) -> Unit,
    onScan: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    onEditSongs: (String) -> Unit = onOpen,
    viewModel: SetlistsViewModel = hiltViewModel(),
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    val online by viewModel.online.collectAsStateWithLifecycle()
    val localNet by viewModel.localNet.collectAsStateWithLifecycle()
    val internetOn by viewModel.internetOn.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nameLimit by viewModel.nameLimit.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val conductButton by viewModel.conductButton.collectAsStateWithLifecycle()
    val message = br.app.stagebook.ui.components.LocalAppMessage.current
    val armHint = stringResource(R.string.conduct_arm_hint)
    val duplicated = stringResource(R.string.set_duplicated)
    var query by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(query) { viewModel.onQueryChange(query) }
    if (nameLimit) InfoDialog(stringResource(R.string.qr_limit_title), stringResource(R.string.qr_limit_name), viewModel::dismissNameLimit)
    SetlistsScreen(
        uiState = uiState,
        // double tap on OK created two setlists: after the first one the screen is already leaving
        onCreate = { name -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { query = ""; onOpen(viewModel.create(name)) } },
        onCreateAt = { name, date -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { query = ""; onOpen(viewModel.create(name, date)) } },
        onEdit = viewModel::edit,
        // found what they were looking for: coming back the whole list shows, as in Songs
        // armed to lead, a tap on a setlist plays it from where the show stopped and starts leading (the one place that does);
        // otherwise it opens the setlist in the drawer
        onOpen = { query = ""; if (conductButton.armed) { val at = viewModel.play(it); onPlay(it, at.start, at.atBreak) } else onOpen(it) },
        // tapping a setlist opens its stage where the show stopped (an empty one has nothing to play: the edit)
        onPlay = { query = ""; val at = viewModel.play(it); onPlay(it, at.start, at.atBreak) },
        query = query,
        onQueryChange = { query = it },
        onScan = onScan,
        online = online,
        internetOn = internetOn,
        localNet = localNet,
        onReceiveCode = onReceiveCode,
        received = received,
        onReceived = onReceived,
        onSync = onSync,
        onDelete = viewModel::delete,
        onDeleteMany = viewModel::deleteMany,
        onRename = viewModel::rename,
        onShare = onShare,
        onEditSongs = { query = ""; onEditSongs(it) },
        onLead = { query = ""; val at = viewModel.lead(it); onPlay(it, at.start, at.atBreak) },
        onDuplicate = { id, name -> if (viewModel.duplicate(id, name) != null) message.show(duplicated) },
        conduct = conductButton,
        onConduct = { if (viewModel.toggleConduct()) message.show(armHint) },
        cloud = viewModel.cloud.collectAsStateWithLifecycle().value,
        onShareInternet = viewModel::shareInternet,
        onDismissCloud = viewModel::dismissCloud,
        codeActions = br.app.stagebook.ui.share.rememberCodeActions(),
        band = viewModel.bandOnline.collectAsStateWithLifecycle().value,
        onSendBand = viewModel::sendBand,
        onBandSearch = viewModel::bandSearch,
        songsIn = viewModel::songsIn,
        pdfSizesIn = viewModel::pdfSizesIn,
    )
    br.app.stagebook.ui.share.BandResultMessage(viewModel.bandResult)
}

@Composable
fun SetlistsScreen(
    uiState: SetlistsUiState,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,   // the card menu's "Edit setlist", and an empty setlist: the edit
    onPlay: (String) -> Unit,   // tapping a setlist: its stage
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRename: (id: String, name: String) -> Unit = { _, _ -> },
    onShare: (String) -> Unit = {},   // the card's menu: the same sheet as the stage panel's
    conduct: ConductButtonUi = ConductButtonUi(),
    onConduct: () -> Unit = {},
    cloud: br.app.stagebook.ui.share.CloudUi? = null,
    onShareInternet: (ids: List<String>, withSongs: Boolean) -> Unit = { _, _ -> },
    onDismissCloud: () -> Unit = {},
    codeActions: br.app.stagebook.ui.share.CodeActions = br.app.stagebook.ui.share.CodeActions({}, {}),
    band: Int = 0,
    onSendBand: (ids: List<String>, withSongs: Boolean, wifiOnly: Boolean) -> Unit = { _, _, _ -> },
    onBandSearch: (Boolean) -> Unit = {},
    songsIn: (List<String>) -> Int = { 0 },
    pdfSizesIn: (List<String>) -> List<Long> = { emptyList() },   // the PDFs the chosen setlists would carry: the internet says if they fit
    onDeleteMany: (List<String>) -> Unit = { it.forEach(onDelete) },   // the chosen ones deleted in one save
    onScan: () -> Unit = {},
    online: Boolean = false,
    internetOn: Boolean = true,
    localNet: Boolean = true,   // on Wi-Fi: "Sync with the band" needs it (B-26)
    onReceiveCode: (String) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    // the new-setlist window also asks for the day, the edit window for name and day (the defaults keep a caller without a day working)
    onCreateAt: (name: String, date: String) -> Unit = { name, _ -> onCreate(name) },
    onEdit: (id: String, name: String, date: String) -> Unit = { id, name, _ -> onRename(id, name) },
    // the card menu's three that used to be only inside the open setlist (user, 23/11)
    onEditSongs: (String) -> Unit = onOpen,   // the setlist open, already editing its songs
    onLead: (String) -> Unit = {},   // plays it and starts leading the band
    onDuplicate: (id: String, name: String) -> Unit = { _, _ -> },
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    // holding a setlist selects it, as in the song list (Google's pattern); rename lives inside the setlist
    val selection = br.app.stagebook.ui.components.rememberSelection()
    var selected by selection.state
    var deletingMany by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    // the boxes also come on by the checklist button at the bottom, with nothing chosen yet (as in the song list)
    var pickMode by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = pickMode && !selection.active) { pickMode = false }
    val selecting = selection.active || pickMode
    val toggle = selection::toggle
    // full width beside the rail, like Settings (user, 03/11: centred at 840 dp left a gap after the rail)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            if (selecting) {
                br.app.stagebook.ui.components.SelectionTopBar(
                    count = selected.size,
                    onClose = { selected = emptyList(); pickMode = false },
                    actions = listOf(
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.share_title), AppIcons.Share) { sharing = true },
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.action_delete), AppIcons.Delete) { deletingMany = true },
                    ),
                    onSelectAll = { selected = uiState.sets.map { it.id } },
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                )
            } else ScreenHeader(
                title = stringResource(R.string.sets_title),
                subtitle = br.app.stagebook.ui.components.filteredCountText(
                    br.app.stagebook.ui.components.setCountText(uiState.total), uiState.sets.size, query.isNotBlank(),
                ),
                onClearFilter = if (query.isNotBlank()) { { onQueryChange("") } } else null,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            ) {
                if (conduct.shown) ConductArmButton(conduct.armed, onConduct)
                br.app.stagebook.ui.components.HomeStatusIcons()
            }
            // the search alone: the list has one order, newest show first (user, 19/11)
            br.app.stagebook.ui.components.SearchWithFilter(
                query, onQueryChange, stringResource(R.string.sets_search), stringResource(R.string.sets_search_hint), Modifier.padding(bottom = Spacing.m),
            )
            // a new setlist goes in at the top: if the list was at the top, it shows (the list kept its place by the item
            // that was first, and the new one stayed hidden above it, device test B-09). The same when the first item
            // changes for any reason (a new show day), judged by where the list was before the change: the list
            // already follows the old first item when the effect runs. Changing the search always starts at
            // the top (it followed the old first item down the list; audit 03/11)
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val firstId = uiState.sets.firstOrNull()?.id
            val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
            val topBefore = atTop   // read while composing the change, before the list is measured with the new items
            androidx.compose.runtime.LaunchedEffect(firstId) { if (topBefore) listState.scrollToItem(0) }
            br.app.stagebook.ui.components.ScrollToTopOn(uiState.query, listState)
            val locale: java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)
            Crossfade(targetState = uiState.loading, label = "sets", modifier = Modifier.weight(1f)) { loading ->
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.total == 0 -> EmptyState(AppIcons.QueueMusic, stringResource(R.string.sets_empty_title), stringResource(R.string.sets_empty_message))
                    uiState.sets.isEmpty() -> EmptyState(AppIcons.Search, stringResource(R.string.sets_not_found), "")
                    else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.l)) {
                        // newest first: one heading per month (and "No date" for the old ones)
                        setlistMonths(uiState.sets).forEach { (month, rows) ->
                        item(key = "month-$month") {
                            Text(
                                if (month.isEmpty()) stringResource(R.string.sets_no_date) else monthTitle(month, locale),
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.s).animateItem(),
                            )
                        }
                        items(rows, key = { it.id }) { set ->
                            SongListItem(
                                title = set.name,
                                subtitle = listOfNotNull(
                                    br.app.stagebook.ui.components.songCountText(set.count),
                                    dayText(set.date, locale),
                                ).joinToString(" · "),
                                onClick = { if (selecting) toggle(set.id) else onOpen(set.id) },
                                onLongClick = { toggle(set.id) },
                                leading = if (selecting) { { androidx.compose.material3.Checkbox(set.id in selected, { toggle(set.id) }) } } else null,
                                modifier = Modifier.animateItem(),
                                titleStyle = MaterialTheme.typography.titleLarge,
                                trailing = {
                                    // the ⋮ replaces the pencil: edit is its first option, as in the stage panel's menu (user, 14/11)
                                    val copyName = stringResource(R.string.set_copy_name, set.name)
                                    if (!selecting) OverflowMenu(
                                        setlistCardActions(
                                            { if (set.count > 0) onPlay(set.id) else onOpen(set.id) }, { onShare(set.id) }, { renamingId = set.id }, { deletingId = set.id },
                                            onEditSongs = { onEditSongs(set.id) },
                                            // leading needs a band to lead and songs to play
                                            onLead = if (conduct.shown && set.count > 0) ({ onLead(set.id) }) else null,
                                            onDuplicate = { onDuplicate(set.id, copyName) },
                                        ),
                                    )
                                },
                            )
                        }
                        }
                    }
                }
            }
            // at the bottom, as SongbookPro: the + (a new setlist, then the ways to bring one in) and the checklist for the boxes
            br.app.stagebook.ui.components.ListBottomBar(selecting, stringResource(R.string.select_sets), { pickMode = !pickMode; if (!pickMode) selected = emptyList() }) {
                br.app.stagebook.ui.share.AddMenuButton(
                    online, onScan, onReceiveCode, onNew = { creating = true }, newLabel = R.string.sets_new, received = received,
                    onReceived = onReceived, onSync = onSync, internetOn = internetOn, localNet = localNet,
                )
            }
        }
    }
    if (creating) {
        SetlistEditDialog(
            title = stringResource(R.string.sets_new),
            name = "",
            date = br.app.stagebook.data.model.Setlist.today(),
            namePlaceholder = stringResource(R.string.sets_name_hint),
            onConfirm = onCreateAt,
            onDismiss = { creating = false },
        )
    }
    renamingId?.let { id ->
        val set = uiState.sets.firstOrNull { it.id == id }
        if (set != null) SetlistEditDialog(
            title = stringResource(R.string.sets_edit_title),
            name = set.name,
            date = set.date,
            namePlaceholder = stringResource(R.string.sets_name),
            onConfirm = { name, date -> renamingId = null; onEdit(id, name, date) },
            onDismiss = { renamingId = null },
        )
    }
    deletingId?.let { id ->
        val set = uiState.sets.firstOrNull { it.id == id }
        if (set != null) ConfirmDialog(
            message = stringResource(R.string.sets_delete_confirm, set.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { deletingId = null; onDelete(id) },
            onDismiss = { deletingId = null },
        )
    }
    if (sharing) {
        br.app.stagebook.ui.share.ShareSheet(
            songs = remember(selected) { songsIn(selected) },
            online = online,
            internetOn = internetOn,
            onQr = null,   // a QR holds one setlist: several go by code or to the band
            onInternet = { withSongs -> sharing = false; onShareInternet(selected, withSongs); selected = emptyList() },
            onDismiss = { sharing = false },
            band = band,
            onBand = { withSongs, wifiOnly -> sharing = false; onSendBand(selected, withSongs, wifiOnly); selected = emptyList() },
            onSearch = onBandSearch,
            limits = remember(selected) { br.app.stagebook.ui.share.ShareLimits(lists = selected.size, songs = songsIn(selected), setlists = true, pdfSizes = pdfSizesIn(selected)) },   // looks at the disk: once per choice
        )
    }
    cloud?.let { br.app.stagebook.ui.share.CloudDialog(it, onDismissCloud, codeActions.share, codeActions.copy) }
    if (deletingMany) {
        ConfirmDialog(
            message = pluralStringResource(R.plurals.sets_delete_many_confirm, selected.size, selected.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onDeleteMany(selected); selected = emptyList() },
            onDismiss = { deletingMany = false },
        )
    }
}

/**
 * The card's ⋮ menu, in this order. Editing the songs, leading the band and duplicating are here too (user, 23/11): they
 * were only inside the open setlist, a tap further. An action with nothing to do it is left out (leading with no band,
 * or of an empty setlist).
 */
@Composable
internal fun setlistCardActions(
    onPlay: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onEditSongs: (() -> Unit)? = null, onLead: (() -> Unit)? = null, onDuplicate: (() -> Unit)? = null,
) = listOfNotNull(
    MenuAction(stringResource(R.string.sets_play), AppIcons.Play, onPlay),
    onEditSongs?.let { MenuAction(stringResource(R.string.set_edit), AppIcons.Edit, it) },
    MenuAction(stringResource(R.string.share_title), AppIcons.Share, onShare),
    onLead?.let { MenuAction(stringResource(R.string.conduct_start), AppIcons.Group, it) },
    MenuAction(stringResource(R.string.sets_edit), AppIcons.Rename, onRename),
    onDuplicate?.let { MenuAction(stringResource(R.string.set_duplicate), AppIcons.Copy, it) },
    MenuAction(stringResource(R.string.sets_delete), AppIcons.Delete, onDelete, danger = true),
)

/**
 * "Lead the band" as one icon in the header: tapping arms it (lit) and the next setlist tapped is led; tapping again
 * cancels. The name is the content description and shows when held.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConductArmButton(armed: Boolean, onClick: () -> Unit) {
    val name = stringResource(R.string.conduct_start)
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider(androidx.compose.material3.TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(name) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.semantics { selected = armed },
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = if (armed) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) { Icon(AppIcons.Group, contentDescription = name) }
    }
}

private val previewState = SetlistsUiState(
    loading = false,
    sets = listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "Ensaio 18/10", 6)),
)

@LightPreview
@Composable
private fun SetlistsScreenLightPreview() = StagebookTheme(darkTheme = false) { SetlistsScreen(previewState, {}, {}, {}, {}) }

@DarkPreview
@Composable
private fun SetlistsScreenDarkPreview() = StagebookTheme(darkTheme = true) { SetlistsScreen(previewState, {}, {}, {}, {}) }
