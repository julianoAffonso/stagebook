package br.app.stagebook.ui.songs

import br.app.stagebook.ui.share.rememberCodeActions
import br.app.stagebook.ui.share.ShareSheet
import br.app.stagebook.ui.share.BandResultMessage
import br.app.stagebook.ui.share.ImportButton
import br.app.stagebook.ui.share.CodeActions
import br.app.stagebook.ui.share.CloudDialog
import br.app.stagebook.ui.share.CloudUi
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.SearchField
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import br.app.stagebook.ui.components.wideLists
import br.app.stagebook.ui.components.InfoDialog
import br.app.stagebook.ui.components.SearchWithTags
import br.app.stagebook.ui.components.TagsDialog
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing

@Composable
fun SongsRoute(
    onOpenSong: (String) -> Unit,
    onEditSong: (String?) -> Unit,
    onImport: (String) -> Unit,
    viewModel: SongsViewModel = hiltViewModel(),
    onScan: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val internetOn by viewModel.internetOn.collectAsStateWithLifecycle()
    BandResultMessage(viewModel.bandResult)
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.onQueryChange(query) }
    SongsScreen(
        uiState = uiState,
        online = online,
        internetOn = internetOn,
        query = query,
        onQueryChange = { query = it },
        onNewSong = { onEditSong(null) },
        onImport = { onImport(query) },
        // found what they were looking for: coming back from the stage the whole list shows
        onOpen = { query = ""; onOpenSong(it) },
        cloud = viewModel.cloud.collectAsStateWithLifecycle().value,
        onShareSongs = viewModel::shareSongs,
        onDismissCloud = viewModel::dismissCloud,
        codeActions = rememberCodeActions(),
        band = viewModel.bandOnline.collectAsStateWithLifecycle().value,
        onSendBand = viewModel::sendBand,
        onBandSearch = viewModel::bandSearch,
        onScan = onScan,
        onReceiveCode = onReceiveCode,
        received = received,
        onReceived = onReceived,
        onSync = onSync,
        onTagsChange = viewModel::onTagsChange,
        onSetTags = viewModel::setTags,
        tagsOf = viewModel::tagsOf,
        onDeleteMany = viewModel::deleteMany,
        onAddToSetlist = viewModel::addToSetlist,
    )
}

@Composable
fun SongsScreen(
    uiState: SongsUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onNewSong: () -> Unit,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean = true,
    internetOn: Boolean = true,
    cloud: CloudUi? = null,
    onShareSongs: (List<String>) -> Unit = {},
    onDismissCloud: () -> Unit = {},
    codeActions: CodeActions = CodeActions({}, { _, _ -> }),
    initialSelection: List<String> = emptyList(),   // screenshots and tests
    onScan: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    band: Int = 0,
    onSendBand: (List<String>) -> Unit = {},
    onBandSearch: (Boolean) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
    onTagsChange: (Set<String>) -> Unit = {},
    onSetTags: (ids: List<String>, add: List<String>, remove: List<String>) -> Unit = { _, _, _ -> },
    // the tags of every chosen song, the ones the search hides included
    tagsOf: (ids: List<String>) -> List<List<String>> = { ids -> uiState.songs.filter { it.id in ids }.map { it.tags } },
    onDeleteMany: (List<String>) -> Unit = {},
    onAddToSetlist: (ids: List<String>, setId: String?, newName: String) -> AddResult = { _, _, _ -> AddResult.Limit },
) {
    var deletingMany by remember { mutableStateOf(false) }
    var addingToSet by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(false) }
    val message = br.app.stagebook.ui.components.LocalAppMessage.current
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    fun addedMessage(r: AddResult.Added) = res.getQuantityString(R.plurals.songs_added_to_set, r.count, r.count, r.set)
    val wide = wideLists()
    // tags of these songs (one from the ⋮, or the selection)
    var tagging by remember { mutableStateOf<List<String>?>(null) }
    // selection to share several: holding a song (or ⋮ → Share) starts it; back or ✕ ends it
    val selection = br.app.stagebook.ui.components.rememberSelection(initialSelection)
    var selected by selection.state
    var sharing by rememberSaveable { mutableStateOf(false) }
    val selecting = selection.active
    val toggle = selection::toggle
    CenteredContent(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            // selecting, the top bar becomes the selection's, as in Google's apps (Gmail, Files, Photos): ✕, how many and
            // the actions as icons (holding one shows its name), the rest in the ⋮ (user, 01/11)
            if (selecting) {
                br.app.stagebook.ui.components.SelectionTopBar(
                    count = selected.size,
                    onClose = { selected = emptyList() },
                    actions = listOf(
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.tags_action), AppIcons.Label) { tagging = selected },
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.songs_add_to_set), AppIcons.PlaylistAdd) { addingToSet = true },
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.share_title), AppIcons.Share) { sharing = true },
                        br.app.stagebook.ui.components.SelectionAction(stringResource(R.string.action_delete), AppIcons.Delete) { deletingMany = true },
                    ),
                    onSelectAll = { selected = (selected + uiState.songs.map { it.id }).distinct() },
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                )
            } else ScreenHeader(
                title = stringResource(R.string.songs_title),
                subtitle = pluralStringResource(R.plurals.song_count, uiState.total, uiState.total),
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            )
            SearchWithTags(
                query, onQueryChange, stringResource(R.string.songs_search), uiState.tags, uiState.chosenTags, onTagsChange,
                // selecting, the Import/New row goes away: the list keeps its distance from the search
                Modifier.padding(bottom = if (selecting) Spacing.m else 0.dp),
            )
            // while selecting, the actions are in the top bar
            if (!selecting) Row(Modifier.padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // secondary at the start, the main action at the end (like Google's apps). Import from sites (with
                // internet), scan the QR or type the code: one button, one menu.
                ImportButton(online, onScan, onReceiveCode, Modifier.weight(1f), onSites = onImport, received = received, onReceived = onReceived, onSync = onSync, internetOn = internetOn)
                ActionButton(stringResource(R.string.songs_new), onNewSong, Modifier.weight(1f), icon = AppIcons.Add)
            }
            AnimatedVisibility(!online) {
                Row(Modifier.padding(bottom = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.import_needs_internet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Crossfade(targetState = uiState.loading, label = "songs") { loading ->
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.total == 0 -> EmptyState(AppIcons.MusicNote, stringResource(R.string.songs_empty_title), stringResource(R.string.songs_empty_message))
                    uiState.songs.isEmpty() -> EmptyState(AppIcons.Search, stringResource(R.string.songs_not_found), "")
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(if (wide) Spacing.xs else Spacing.s),
                        contentPadding = PaddingValues(bottom = Spacing.l),
                    ) {
                        items(uiState.songs, key = { it.id }) { song ->
                            SongListItem(
                                title = song.title,
                                subtitle = song.subtitle,
                                onClick = { if (selecting) toggle(song.id) else onOpen(song.id) },
                                onLongClick = { toggle(song.id) },
                                leading = if (selecting) { { Checkbox(song.id in selected, { toggle(song.id) }) } } else null,
                                // animate only the removal on delete: enter and move animated dozens of rows on every
                                // search letter
                                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = null),
                                titleStyle = MaterialTheme.typography.titleLarge,
                                inline = wide,
                                // tapping opens the stage and holding selects; editing is on the stage's pencil, tags and delete
                                // in the editor (user, 01/11: the row's own pencil was one too many)
                            )
                        }
                    }
                }
            }
        }
    }
    if (sharing) {
        ShareSheet(
            songs = selected.size, online = online, onQr = null, internetOn = internetOn,
            onInternet = { sharing = false; onShareSongs(selected); selected = emptyList() },
            onDismiss = { sharing = false },
            band = band,
            onBand = { sharing = false; onSendBand(selected); selected = emptyList() },
            onSearch = onBandSearch,
        )
    }
    cloud?.let { CloudDialog(it, onDismissCloud, codeActions.share, codeActions.copy) }
    tagging?.let { ids ->
        // all the chosen songs, not only the ones the search shows (audit 01/11)
        val rows = remember(ids) { tagsOf(ids) }
        // the suggestions come first even before anyone uses them
        val options = br.app.stagebook.ui.components.tagOptions(uiState.tags)
        val initial = br.app.stagebook.ui.components.tagStates(options, rows)
        TagsDialog(options, initial, { add, remove -> onSetTags(ids, add, remove); selected = emptyList() }, { tagging = null })
    }
    if (deletingMany) {
        ConfirmDialog(
            pluralStringResource(R.plurals.songs_delete_many_confirm, selected.size, selected.size),
            stringResource(R.string.action_delete),
            { onDeleteMany(selected); selected = emptyList() },
            { deletingMany = false },
        )
    }
    if (addingToSet) {
        SetPickDialog(uiState.sets, onPick = { id ->
            addingToSet = false
            if (id == null) naming = true
            else when (val r = onAddToSetlist(selected, id, "")) {
                is AddResult.Added -> { message.show(addedMessage(r)); selected = emptyList() }
                AddResult.Limit -> limit = true
                AddResult.Gone -> message.show(res.getString(R.string.songs_set_gone))
            }
        }, onDismiss = { addingToSet = false })
    }
    if (naming) {
        br.app.stagebook.ui.components.TextInputDialog(
            title = stringResource(R.string.sets_new),
            initial = "",
            placeholder = stringResource(R.string.sets_name_hint),
            onConfirm = { name ->
                when (val r = onAddToSetlist(selected, null, name)) {
                    is AddResult.Added -> { message.show(addedMessage(r)); selected = emptyList() }
                    AddResult.Limit -> limit = true
                    AddResult.Gone -> message.show(res.getString(R.string.songs_set_gone))
                }
            },
            onDismiss = { naming = false },
        )
    }
    if (limit) InfoDialog(stringResource(R.string.qr_limit_title), stringResource(R.string.qr_limit_message), { limit = false })
}

/** Which setlist the selection goes into, or a new one. */
@Composable
private fun SetPickDialog(sets: List<SetPick>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.songs_add_to_set_title)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                SongListItem(title = stringResource(R.string.sets_new), subtitle = "", onClick = { onPick(null) }, leading = { Icon(AppIcons.Add, null, tint = MaterialTheme.colorScheme.primary) })
                sets.forEach { s -> SongListItem(title = s.name, subtitle = "", onClick = { onPick(s.id) }) }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val previewState = SongsUiState(
    loading = false,
    songs = listOf(
        SongRowUi("1", "Hazard", "Richard Marx · 1991"),
        SongRowUi("2", "Tempo Perdido", "Legião Urbana · 1986"),
        SongRowUi("3", "Wild Horses", "The Rolling Stones · 1971"),
    ),
    total = 3,
)

@LightPreview
@Composable
private fun SongsScreenLightPreview() = StagebookTheme(darkTheme = false) {
    SongsScreen(previewState, "", {}, {}, {}, {})
}

@DarkPreview
@Composable
private fun SongsScreenDarkPreview() = StagebookTheme(darkTheme = true) {
    SongsScreen(previewState, "", {}, {}, {}, {})
}
