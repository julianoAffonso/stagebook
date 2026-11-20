package br.app.stagebook.ui.songs

import br.app.stagebook.ui.share.rememberCodeActions
import br.app.stagebook.ui.share.ShareSheet
import br.app.stagebook.ui.share.BandResultMessage
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
import br.app.stagebook.data.message
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
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.components.SongFacts
import br.app.stagebook.ui.components.LetterHeader
import br.app.stagebook.ui.components.LetterIndex
import br.app.stagebook.ui.components.rememberLetterIndexShown
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
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
    onImport: (query: String, kind: String) -> Unit,
    viewModel: SongsViewModel = hiltViewModel(),
    onScan: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorQueue = br.app.stagebook.data.LocalEditorQueue.current
    val online by viewModel.online.collectAsStateWithLifecycle()
    val localNet by viewModel.localNet.collectAsStateWithLifecycle()
    val internetOn by viewModel.internetOn.collectAsStateWithLifecycle()
    BandResultMessage(viewModel.bandResult)
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.onQueryChange(query) }
    var askKind by rememberSaveable { mutableStateOf(false) }
    if (askKind) br.app.stagebook.ui.importer.ImportKindDialog({ kind -> askKind = false; onImport(query, kind.name) }, { askKind = false })
    // PDF files chosen in the + menu: each becomes a song named after the file (checked first, the reason told when one is left out)
    val appMessage = br.app.stagebook.ui.components.LocalAppMessage.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    val pickPdfs = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importScope.launch { appMessage.show(viewModel.importPdfs(uris).message(context), long = true) }
    }
    SongsScreen(
        uiState = uiState,
        online = online,
        internetOn = internetOn,
        localNet = localNet,
        query = query,
        onQueryChange = { query = it },
        onNewSong = { onEditSong(null) },
        // the automatic profile has no kind of its own: the musician says what this one is, to open on the right site
        onImport = { if (viewModel.asksKind()) askKind = true else onImport(query, "") },
        onImportPdfs = { pickPdfs.launch(br.app.stagebook.data.PdfStore.MIME) },
        onImportPdfSites = { onImport(query, br.app.stagebook.data.model.SiteKind.PDF.name) },
        // found what they were looking for: coming back from the stage the whole list shows
        // the editor opened from this song steps through the list as it is now (search and filters on)
        onOpen = { editorQueue.offerList(uiState.songs.map { s -> s.id }); query = ""; onOpenSong(it) },
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
        onShowChange = viewModel::onShowChange,
        onOrderChange = viewModel::onOrderChange,
        onSetTags = viewModel::setTags,
        tagsOf = viewModel::tagsOf,
        onDeleteMany = viewModel::deleteMany,
        onAddToSetlist = viewModel::addToSetlist,
        pdfSizesOf = viewModel::pdfSizesOf,
    )
}

/** A name for a question in a dialog: a very long one is cut with an ellipsis so the dialog stays readable. */
internal fun shortName(name: String, max: Int = 40): String = name.trim().let { if (it.length > max) it.take(max - 1).trimEnd() + "…" else it }

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
    localNet: Boolean = true,   // on Wi-Fi: "Sync with the band" needs it (B-26)
    cloud: CloudUi? = null,
    onShareSongs: (List<String>) -> Unit = {},
    onDismissCloud: () -> Unit = {},
    codeActions: CodeActions = CodeActions({}, {}),
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
    onShowChange: (br.app.stagebook.domain.PlayShow) -> Unit = {},
    onOrderChange: (br.app.stagebook.domain.SongOrder) -> Unit = {},
    onSetTags: (ids: List<String>, add: List<String>, remove: List<String>) -> Unit = { _, _, _ -> },
    // the tags of every chosen song, the ones the search hides included
    tagsOf: (ids: List<String>) -> List<List<String>> = { ids -> uiState.songs.filter { it.id in ids }.map { it.tags } },
    onDeleteMany: (List<String>) -> Unit = {},
    onAddToSetlist: (ids: List<String>, setId: String?, newName: String) -> AddResult = { _, _, _ -> AddResult.Limit },
    pdfSizesOf: (List<String>) -> List<Long> = { emptyList() },   // the PDFs the chosen songs would carry: the internet says if they fit
    onImportPdfs: (() -> Unit)? = null,   // "Import PDFs" in the + menu
    onImportPdfSites: (() -> Unit)? = null,   // "PDFs from sites" in the + menu
    indexPinned: Boolean = false,   // screenshots and tests: the A to Z index shows without a scroll
) {
    var deletingMany by remember { mutableStateOf(false) }
    var addingToSet by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(false) }
    val message = br.app.stagebook.ui.components.LocalAppMessage.current
    val res = androidx.compose.ui.platform.LocalResources.current
    // none added: they were already there ("0 música adicionada" said nothing useful, device test B-29)
    fun addedMessage(r: AddResult.Added) =
        if (r.count == 0) res.getString(R.string.songs_already_in_set, r.set)
        else res.getQuantityString(R.plurals.songs_added_to_set, r.count, r.count, r.set)
    val wide = wideLists()
    val filtering = query.isNotBlank() || uiState.chosenTags.isNotEmpty() || uiState.show != br.app.stagebook.domain.PlayShow.ALL
    // tags of these songs (one from the ⋮, or the selection)
    var tagging by remember { mutableStateOf<List<String>?>(null) }
    // selection to share several: holding a song (or ⋮ → Share) starts it; back or ✕ ends it
    val selection = br.app.stagebook.ui.components.rememberSelection(initialSelection)
    var selected by selection.state
    var sharing by rememberSaveable { mutableStateOf(false) }
    // the boxes also come on by the checklist button at the bottom, with nothing chosen yet (SongbookPro's way)
    var pickMode by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = pickMode && !selection.active) { pickMode = false }
    val selecting = selection.active || pickMode
    val toggle = selection::toggle
    // selecting lets go of the search: otherwise, when a dialog of the selection closed (new setlist, tags), the focus
    // went back to the search and the keyboard opened over the list (device test B-02)
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(selecting) { if (selecting) focus.clearFocus() }
    // full width beside the rail, like Settings (user, 03/11: centred at 840 dp left a gap after the rail)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            // selecting, the top bar becomes the selection's, as in Google's apps (Gmail, Files, Photos): ✕, how many and
            // the actions as icons (holding one shows its name), the rest in the ⋮ (user, 01/11)
            if (selecting) {
                br.app.stagebook.ui.components.SelectionTopBar(
                    count = selected.size,
                    onClose = { selected = emptyList(); pickMode = false },
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
                subtitle = br.app.stagebook.ui.components.filteredCountText(
                    br.app.stagebook.ui.components.songCountText(uiState.total), uiState.songs.size,
                    filtering,
                ),
                onClearFilter = if (filtering) { { onQueryChange(""); onTagsChange(emptySet()); onShowChange(br.app.stagebook.domain.PlayShow.ALL) } } else null,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            ) { br.app.stagebook.ui.components.HomeStatusIcons() }
            // the tags, what shows by plays and the order, in one field (user, 19/11)
            br.app.stagebook.ui.components.SearchWithFilter(
                query, onQueryChange, stringResource(R.string.songs_search), stringResource(R.string.search_hint), Modifier.padding(bottom = Spacing.m),
                filter = { m -> br.app.stagebook.ui.components.SongFilter(uiState.tags, uiState.chosenTags, onTagsChange, uiState.show, onShowChange, uiState.order, onOrderChange, m) },
            )
            AnimatedVisibility(!online) {
                Row(Modifier.padding(bottom = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.import_needs_internet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            br.app.stagebook.ui.components.ScrollToTopOn("${uiState.query}|${uiState.chosenTags.sorted()}", listState)
            Crossfade(targetState = uiState.loading, label = "songs", modifier = Modifier.weight(1f)) { loading ->
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.total == 0 -> EmptyState(AppIcons.MusicNote, stringResource(R.string.songs_empty_title), stringResource(R.string.songs_empty_message), logo = true)
                    uiState.songs.isEmpty() -> EmptyState(AppIcons.Search, stringResource(R.string.songs_not_found), "")
                    else -> Box {
                        val groups = uiState.groups
                        val row: @Composable androidx.compose.foundation.lazy.LazyItemScope.(SongRowUi) -> Unit = { song ->
                            SongListItem(
                                title = song.title,
                                subtitle = song.subtitle,
                                onClick = { if (selecting) toggle(song.id) else onOpen(song.id) },
                                onLongClick = { toggle(song.id) },
                                leading = if (selecting) { { Checkbox(song.id in selected, { toggle(song.id) }) } } else null,
                                // what the musician checks before the song, as on a setlist's rows (user, 13/11)
                                trailing = { SongFacts(song.songKey, song.bpm, song.capo, song.hasTrack, hasPdf = song.hasPdf) },
                                // animate only the removal on delete: enter and move animated dozens of rows on every
                                // search letter
                                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = null),
                                // the compact row of the setlist's (user, 15/11: one pattern in the drawer)
                                titleStyle = MaterialTheme.typography.titleMedium,
                                dense = true,
                                titleLines = 1,
                                gap = Spacing.s,   // as the setlist's rows, with no number: the order is alphabetical
                                // by plays, each row says how many and when the last was
                                note = if (uiState.order == br.app.stagebook.domain.SongOrder.AZ) "" else playsLine(song),
                                // tapping opens the stage and holding selects; editing is on the stage's pencil, tags and delete
                                // in the editor (user, 01/11: the row's own pencil was one too many)
                            )
                        }
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(if (wide) Spacing.xs else Spacing.s),
                            contentPadding = PaddingValues(bottom = Spacing.l),
                        ) {
                            if (groups.isEmpty()) items(uiState.songs, key = { it.id }, contentType = { ROW }) { row(it) }
                            // a letter before each group; the index at the edge counts on this order to find them
                            else groups.forEachIndexed { g, group ->
                                item(key = HEADER_KEY + group.letter, contentType = HEADER_KEY) { LetterHeader(group.letter) }
                                val end = groups.getOrNull(g + 1)?.first ?: uiState.songs.size
                                items(uiState.songs.subList(group.first, end), key = { it.id }, contentType = { ROW }) { row(it) }
                            }
                        }
                        if (groups.isNotEmpty()) {
                            var held by remember { mutableStateOf(false) }
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            val letters = remember(groups) { groups.map { it.letter } }
                            val firsts = remember(groups) { groups.map { it.first } }
                            val onScreen by remember(firsts) {
                                androidx.compose.runtime.derivedStateOf {
                                    val items = listState.layoutInfo.visibleItemsInfo
                                    if (items.isEmpty()) IntRange.EMPTY else br.app.stagebook.ui.components.lettersOnScreen(firsts, items.first().index, items.last().index)
                                }
                            }
                            LetterIndex(
                                letters = letters,
                                shown = rememberLetterIndexShown(listState, held) || indexPinned,
                                // each header sits after the rows and the headers before it
                                onLetter = { g -> groups.getOrNull(g)?.let { scope.launch { listState.scrollToItem(it.first + g) } } },
                                onHeld = { held = it },
                                // over the screen's margin, so it covers nothing of a row's key
                                modifier = Modifier.align(Alignment.CenterEnd).offset(x = Spacing.m),
                                onScreen = { onScreen },
                            )
                        }
                    }
                }
            }
            // at the bottom, as SongbookPro: the + with what is new (a song, the imports) and the checklist that turns the boxes on
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                br.app.stagebook.ui.share.AddMenuButton(
                    online, onScan, onReceiveCode, onNew = onNewSong, onPdfs = onImportPdfs, onPdfSites = onImportPdfSites, onSites = onImport, received = received, onReceived = onReceived,
                    onSync = onSync, internetOn = internetOn, localNet = localNet,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = { pickMode = !pickMode; if (!pickMode) selected = emptyList() }) {
                    Icon(
                        AppIcons.Checklist, stringResource(R.string.select_mode),
                        tint = if (selecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    if (sharing) {
        ShareSheet(
            songs = selected.size, online = online, onQr = null, internetOn = internetOn,
            limits = br.app.stagebook.ui.share.ShareLimits(lists = 1, songs = selected.size, setlists = false, pdfSizes = pdfSizesOf(selected)),
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
        // one song: its name (and artist), so no one deletes the wrong one; several: the count. The selection can hold
        // songs the search hides, so the name comes from the rows and falls back to the count.
        val only = selected.singleOrNull()?.let { id -> uiState.songs.firstOrNull { it.id == id } }
        ConfirmDialog(
            if (only != null) {
                if (only.subtitle.isBlank()) stringResource(R.string.songs_delete_one_confirm, shortName(only.title))
                else stringResource(R.string.songs_delete_one_artist_confirm, shortName(only.title), shortName(only.subtitle))
            } else pluralStringResource(R.plurals.songs_delete_many_confirm, selected.size, selected.size),
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
    // several songs at once: the setlist may still have room, only not for all of them (device test B-11)
    if (limit) InfoDialog(stringResource(R.string.qr_limit_batch_title), stringResource(R.string.qr_limit_batch), { limit = false })
}

/** Which setlist the selection goes into, or a new one. */
@Composable
private fun SetPickDialog(sets: List<SetPick>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    br.app.stagebook.ui.components.FollowBusyWhile()
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

// a header's key can't be a song's id
private const val HEADER_KEY = "letter:"
private const val ROW = "song"

private val previewState = SongsUiState(
    loading = false,
    songs = listOf(
        SongRowUi("1", "Hazard", "Richard Marx", songKey = "A", bpm = 76),
        SongRowUi("2", "Tempo Perdido", "Legião Urbana", songKey = "C", bpm = 118, hasTrack = true),
        SongRowUi("3", "Wild Horses", "The Rolling Stones", songKey = "G", capo = 2),
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

/** "18 times · last 2 days ago", or "Never played": the row's line when the list is in an order by plays. */
@Composable
internal fun playsLine(song: SongRowUi): String {
    val ago = song.ago
    if (song.plays == 0 || ago == null) return stringResource(R.string.songs_never_played)
    val times = pluralStringResource(R.plurals.songs_plays, song.plays, song.plays)
    val whenText = when (ago.unit) {
        br.app.stagebook.domain.PlayAgo.Unit.TODAY -> stringResource(R.string.songs_played_today)
        br.app.stagebook.domain.PlayAgo.Unit.YESTERDAY -> stringResource(R.string.songs_played_yesterday)
        br.app.stagebook.domain.PlayAgo.Unit.DAYS -> pluralStringResource(R.plurals.songs_played_days, ago.count, ago.count)
        br.app.stagebook.domain.PlayAgo.Unit.WEEKS -> pluralStringResource(R.plurals.songs_played_weeks, ago.count, ago.count)
        br.app.stagebook.domain.PlayAgo.Unit.MONTHS -> pluralStringResource(R.plurals.songs_played_months, ago.count, ago.count)
    }
    return stringResource(R.string.songs_plays_line, times, whenText)
}
