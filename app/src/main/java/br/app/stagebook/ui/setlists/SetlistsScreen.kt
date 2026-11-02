package br.app.stagebook.ui.setlists

import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import br.app.stagebook.ui.components.ButtonTone
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.InfoDialog
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.share.ImportButton
import br.app.stagebook.ui.components.TextInputDialog
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing

@Composable
fun SetlistsRoute(
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    onScan: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
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
    if (nameLimit) InfoDialog(stringResource(R.string.qr_limit_title), stringResource(R.string.qr_limit_name), viewModel::dismissNameLimit)
    SetlistsScreen(
        uiState = uiState,
        // double tap on OK created two setlists: after the first one the screen is already leaving
        onCreate = { name -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onOpen(viewModel.create(name)) },
        onOpen = onOpen,
        onPlay = onPlay,
        onScan = onScan,
        online = online,
        internetOn = internetOn,
        localNet = localNet,
        onReceiveCode = onReceiveCode,
        received = received,
        onReceived = onReceived,
        onSync = onSync,
        onDelete = viewModel::delete,
        cloud = viewModel.cloud.collectAsStateWithLifecycle().value,
        onShareInternet = viewModel::shareInternet,
        onDismissCloud = viewModel::dismissCloud,
        codeActions = br.app.stagebook.ui.share.rememberCodeActions(),
        band = viewModel.bandOnline.collectAsStateWithLifecycle().value,
        onSendBand = viewModel::sendBand,
        onBandSearch = viewModel::bandSearch,
        songsIn = viewModel::songsIn,
    )
    br.app.stagebook.ui.share.BandResultMessage(viewModel.bandResult)
}

@Composable
fun SetlistsScreen(
    uiState: SetlistsUiState,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    cloud: br.app.stagebook.ui.share.CloudUi? = null,
    onShareInternet: (ids: List<String>, withSongs: Boolean) -> Unit = { _, _ -> },
    onDismissCloud: () -> Unit = {},
    codeActions: br.app.stagebook.ui.share.CodeActions = br.app.stagebook.ui.share.CodeActions({}, { _, _ -> }),
    band: Int = 0,
    onSendBand: (ids: List<String>, withSongs: Boolean) -> Unit = { _, _ -> },
    onBandSearch: (Boolean) -> Unit = {},
    songsIn: (List<String>) -> Int = { 0 },
    onScan: () -> Unit = {},
    online: Boolean = false,
    internetOn: Boolean = true,
    localNet: Boolean = true,   // on Wi-Fi: "Sync with the band" needs it (B-26)
    onReceiveCode: (String) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    // holding a setlist selects it, as in the song list (Google's pattern); rename lives inside the setlist
    val selection = br.app.stagebook.ui.components.rememberSelection()
    var selected by selection.state
    var deletingMany by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val selecting = selection.active
    val toggle = selection::toggle
    CenteredContent(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            if (selecting) {
                br.app.stagebook.ui.components.SelectionTopBar(
                    count = selected.size,
                    onClose = { selected = emptyList() },
                    actions = listOf(
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.share_title), AppIcons.Share) { sharing = true },
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.action_delete), AppIcons.Delete) { deletingMany = true },
                    ),
                    onSelectAll = { selected = uiState.sets.map { it.id } },
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                )
            } else ScreenHeader(
                title = stringResource(R.string.sets_title),
                subtitle = br.app.stagebook.ui.components.setCountText(uiState.sets.size),
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            ) { br.app.stagebook.ui.components.HomeStatusIcons() }
            // import one (QR or code) at the start and the main action, new setlist, at the end (same as the Songs tab)
            if (!selecting) Row(Modifier.fillMaxWidth().padding(bottom = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ImportButton(online, onScan, onReceiveCode, Modifier.weight(1f), received = received, onReceived = onReceived, onSync = onSync, internetOn = internetOn, localNet = localNet)
                ActionButton(stringResource(R.string.sets_new), onClick = { creating = true }, modifier = Modifier.weight(1f), icon = AppIcons.Add)
            }
            // a new setlist goes in at the top: if the list was at the top, it shows (the list kept its place by the item
            // that was first, and the new one stayed hidden above it, device test B-09)
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val firstId = uiState.sets.firstOrNull()?.id
            androidx.compose.runtime.LaunchedEffect(firstId) { if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0) }
            Crossfade(targetState = uiState.loading, label = "sets") { loading ->
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.sets.isEmpty() -> EmptyState(AppIcons.QueueMusic, stringResource(R.string.sets_empty_title), stringResource(R.string.sets_empty_message))
                    else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.l)) {
                        items(uiState.sets, key = { it.id }) { set ->
                            SongListItem(
                                title = set.name,
                                subtitle = br.app.stagebook.ui.components.songCountText(set.count),
                                onClick = { if (selecting) toggle(set.id) else onOpen(set.id) },
                                onLongClick = { toggle(set.id) },
                                leading = if (selecting) { { androidx.compose.material3.Checkbox(set.id in selected, { toggle(set.id) }) } } else null,
                                modifier = Modifier.animateItem(),
                                titleStyle = MaterialTheme.typography.titleLarge,
                                trailing = {
                                    if (!selecting) Row {
                                        IconButton(onClick = { onPlay(set.id) }, enabled = set.count > 0) {
                                            Icon(
                                                AppIcons.Slideshow,
                                                contentDescription = stringResource(R.string.action_stage),
                                                tint = if (set.count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.sets_new),
            initial = "",
            placeholder = stringResource(R.string.sets_name_hint),
            onConfirm = onCreate,
            onDismiss = { creating = false },
        )
    }
    if (sharing) {
        br.app.stagebook.ui.share.ShareSheet(
            songs = songsIn(selected),
            online = online,
            internetOn = internetOn,
            onQr = null,   // a QR holds one setlist: several go by code or to the band
            onInternet = { withSongs -> sharing = false; onShareInternet(selected, withSongs); selected = emptyList() },
            onDismiss = { sharing = false },
            band = band,
            onBand = { withSongs -> sharing = false; onSendBand(selected, withSongs); selected = emptyList() },
            onSearch = onBandSearch,
            limits = br.app.stagebook.ui.share.ShareLimits(lists = selected.size, songs = songsIn(selected), setlists = true),
        )
    }
    cloud?.let { br.app.stagebook.ui.share.CloudDialog(it, onDismissCloud, codeActions.share, codeActions.copy) }
    if (deletingMany) {
        ConfirmDialog(
            message = pluralStringResource(R.plurals.sets_delete_many_confirm, selected.size, selected.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { selected.forEach(onDelete); selected = emptyList() },
            onDismiss = { deletingMany = false },
        )
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
