package br.app.stagebook.ui.setlists

import br.app.stagebook.ui.components.QuestionDialog
import br.app.stagebook.ui.components.LocalAppMessage
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import br.app.stagebook.domain.SongMerge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.CircularProgressIndicator
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.OptionDropdown
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.theme.Spacing

@Composable
fun ScanSetlistRoute(
    onDone: (setId: String) -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit = {},
    viewModel: ScanSetlistViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val message = LocalAppMessage.current
    var camera by rememberSaveable {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }
    // asked once and refused: Android no longer shows its question, so the button opens the app's settings; back from
    // there, the permission is read again (device test B-21)
    var asked by rememberSaveable { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    val askCamera = {
        val activity = context as? android.app.Activity
        val androidAsks = activity == null || androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        if (asked && !androidAsks) {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
        } else {
            asked = true
            ask.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.bandResult.collect { (got, asked) ->
            val msg = if (got > 0) resources.getQuantityString(R.plurals.scan_band_got, asked, got, asked) else resources.getString(R.string.scan_band_none)
            message.show(msg, long = true)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.internetResult.collect { ok ->
            val name = viewModel.internetFrom.value.orEmpty()
            message.show(if (ok) resources.getString(R.string.scan_internet_sent, name) else resources.getString(R.string.scan_internet_failed), long = true)
        }
    }
    LaunchedEffect(Unit) { if (!camera && viewModel.readsQr) ask.launch(Manifest.permission.CAMERA) }
    // the other setlists of the same send went to Received
    val parked by viewModel.parked.collectAsStateWithLifecycle()
    LaunchedEffect(parked) {
        if (parked > 0) { message.show(resources.getQuantityString(R.plurals.scan_parked, parked, parked), long = true); viewModel.parkedShown() }
    }
    ScanSetlistScreen(
        ui = ui,
        cameraAllowed = camera,
        onAskCamera = askCamera,
        onText = viewModel::onText,
        onName = viewModel::setName,
        onChoose = viewModel::choose,
        onScanAgain = viewModel::scanAgain,
        existing = viewModel::existing,
        onCreate = { replace -> viewModel.create(replace)?.let(onDone) },
        onCreateAndImport = { replace -> if (viewModel.createAndImport(replace)) onImport() },
        onAddSongs = {
            val n = viewModel.addSongs()
            message.show(resources.getQuantityString(R.plurals.songs_added, n, n))
            onBack()
        },
        onBack = onBack,
        camera = { onText, modifier -> QrScanner(onText, modifier, onNoCamera = { message.show(resources.getString(R.string.scan_no_camera), long = true) }) },
        byCode = viewModel.code.isNotEmpty(),
        canScanAgain = viewModel.code.isNotEmpty() || viewModel.readsQr,
        fromName = viewModel.fromName,
        band = viewModel.bandOnline.collectAsStateWithLifecycle().value,
        asking = viewModel.asking.collectAsStateWithLifecycle().value,
        onAskBand = viewModel::askBand,
        internetFrom = viewModel.internetFrom.collectAsStateWithLifecycle().value,
        onAskInternet = viewModel::askInternet,
        onBandSearch = viewModel::bandSearch,
        changes = viewModel::changes,
        onUseIncoming = viewModel::useIncoming,
        onWantChords = viewModel::searchWithChords,
        onRetry = viewModel::fetch,
        onAcceptInvite = viewModel::acceptInvite,
    )
}

/**
 * Scan a setlist QR and check it before creating. [camera] is the real camera in the app and an empty frame in tests
 * and screenshots.
 */
@Composable
fun ScanSetlistScreen(
    ui: ScanUi,
    cameraAllowed: Boolean,
    onAskCamera: () -> Unit,
    onText: (String) -> Unit,
    onName: (String) -> Unit,
    onChoose: (Int, String?) -> Unit,
    onScanAgain: () -> Unit,
    existing: (String) -> String?,
    onCreate: (replaceId: String?) -> Unit,
    onBack: () -> Unit,
    onCreateAndImport: (replaceId: String?) -> Unit = {},
    camera: @Composable (onText: (String) -> Unit, modifier: Modifier) -> Unit = { _, m -> Box(m) },
    byCode: Boolean = false,
    canScanAgain: Boolean = true,   // what came from the band has nothing to read or fetch again
    onRetry: () -> Unit = {},
    onAddSongs: () -> Unit = {},
    fromName: String = "",   // came from the band: "Received from Ana"
    band: Int = 0,           // from the band on the network now
    asking: Boolean = false,
    onAskBand: () -> Unit = {},
    internetFrom: String? = null,
    onAskInternet: () -> Unit = {},
    onBandSearch: (Boolean) -> Unit = {},
    changes: (Int) -> List<SongMerge.Change> = { emptyList() },
    onUseIncoming: (Int, Boolean) -> Unit = { _, _ -> },
    onWantChords: (Int, Boolean) -> Unit = { _, _ -> },
    onAcceptInvite: () -> Unit = {},
) {
    var comparing by rememberSaveable { mutableStateOf<Int?>(null) }   // row with "See what changes" open
    CenteredContent(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            ScreenHeader(
                if (fromName.isNotEmpty()) stringResource(R.string.scan_from, fromName) else stringResource(
                    when {
                        (ui as? ScanUi.Review)?.songsOnly == true -> R.string.scan_songs_title
                        // reviewing: "Review setlist", however it arrived (the camera and the code are done by now)
                        ui is ScanUi.Review -> R.string.scan_review_title
                        ui is ScanUi.Invite -> R.string.invite_title
                        byCode -> R.string.scan_code_title
                        else -> R.string.scan_title
                    },
                ),
                Modifier.padding(top = Spacing.s, bottom = Spacing.m), onBack = onBack,
            )
            when (ui) {
                is ScanUi.Scanning -> Scanning(ui, cameraAllowed, onAskCamera, onText, camera)
                is ScanUi.Fetching -> Fetching(ui, onRetry)
                is ScanUi.Invite -> InvitePanel(ui, onAcceptInvite, onBack)
                is ScanUi.Review -> {
                    // missing songs: searches for the band while the review is open (to ask them before the sites)
                    val missing = !ui.songsOnly && ui.rows.any { it.chosen == null && !it.carried && it.candidates.isEmpty() }
                    DisposableEffect(missing) {
                        if (missing) onBandSearch(true)
                        onDispose { if (missing) onBandSearch(false) }
                    }
                    if (ui.songsOnly) SongsReview(ui, onAddSongs, { comparing = it }, onWantChords, onUseIncoming)
                    else Review(ui, onName, onChoose, onScanAgain, existing, onCreate, onCreateAndImport, byCode, canScanAgain, band, asking, onAskBand, { comparing = it }, onWantChords, onUseIncoming, internetFrom, onAskInternet)
                    comparing?.let { i ->
                        // the diff of two lyrics, once per song compared (not on every recomposition)
                        val diff = androidx.compose.runtime.remember(i, ui.rows.getOrNull(i)) { changes(i) }
                        ChangesDialog(
                            diff, useIncoming = ui.rows.getOrNull(i)?.useIncoming == true,
                            onChoose = { use -> onUseIncoming(i, use); comparing = null },
                            onDismiss = { comparing = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Scanning(
    ui: ScanUi.Scanning,
    cameraAllowed: Boolean,
    onAskCamera: () -> Unit,
    onText: (String) -> Unit,
    camera: @Composable (onText: (String) -> Unit, modifier: Modifier) -> Unit,
) {
    // without the camera: an empty state in the middle (camera icon, why, the button), not the "point the camera" hint;
    // the hint had no color of its own and came out black on the dark background (device test B-21)
    if (!cameraAllowed) {
        br.app.stagebook.ui.components.EmptyState(
            AppIcons.PhotoCamera,
            stringResource(R.string.scan_no_camera),
            stringResource(R.string.scan_no_camera_why),
            action = { ActionButton(stringResource(R.string.scan_allow_camera), onAskCamera, icon = AppIcons.PhotoCamera) },
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Box(
            Modifier.fillMaxWidth().weight(1f, fill = false).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            camera(onText, Modifier.fillMaxSize())
        }
        ui.problem?.let { p ->
            // newer app: "Update the app" is a link to About, where the update is (user, 01/11)
            if (p == ScanProblem.NEWER) br.app.stagebook.ui.components.StatusBox(br.app.stagebook.ui.components.StatusTone.PROBLEM) {
                br.app.stagebook.ui.components.SettingsLinkText(
                    stringResource(R.string.scan_newer_link), stringResource(R.string.scan_update_link),
                    br.app.stagebook.ui.settings.SettingsSection.ABOUT, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.LocalContentColor.current,
                )
            } else br.app.stagebook.ui.components.StatusNote(
                stringResource(
                    when (p) {
                        ScanProblem.INCOMPLETE -> R.string.scan_incomplete
                        ScanProblem.NEWER -> R.string.scan_newer
                        ScanProblem.NOT_SETLIST -> R.string.scan_not_setlist
                    },
                ),
                br.app.stagebook.ui.components.StatusTone.PROBLEM,
            )
        }
    }
}

@Composable
private fun Review(
    ui: ScanUi.Review,
    onName: (String) -> Unit,
    onChoose: (Int, String?) -> Unit,
    onScanAgain: () -> Unit,
    existing: (String) -> String?,
    onCreate: (replaceId: String?) -> Unit,
    onCreateAndImport: (replaceId: String?) -> Unit,
    byCode: Boolean,
    canScanAgain: Boolean,
    band: Int = 0,
    asking: Boolean = false,
    onAskBand: () -> Unit = {},
    onCompare: (Int) -> Unit = {},
    onWantChords: (Int, Boolean) -> Unit = { _, _ -> },
    onUseIncoming: (Int, Boolean) -> Unit = { _, _ -> },
    internetFrom: String? = null,
    onAskInternet: () -> Unit = {},
) {
    var confirmMissing by rememberSaveable { mutableStateOf(false) }
    var nameTaken by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by rememberSaveable { mutableStateOf(false) }   // the repeated name came from "Import the missing ones"
    fun finish(replace: String?) = if (importing) onCreateAndImport(replace) else onCreate(replace)
    // first say what's left out; then, if the name exists, ask whether to replace or create another
    fun goName() { existing(ui.name.trim())?.let { nameTaken = it } ?: finish(null) }
    fun go() { importing = false; if (ui.missing.isNotEmpty()) confirmMissing = true else goName() }
    // import the missing ones: the setlist is created with the found ones and the importer opens searching for the
    // first missing one
    fun goImport() { importing = true; goName() }
    val toImport = ui.rows.count { it.chosen == null && !it.carried && it.candidates.isEmpty() }

    val found = ui.rows.count { it.chosen != null || it.carried }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(ui.name, onName, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.scan_name)) }, singleLine = true)
        Text(
            pluralStringResource(R.plurals.scan_found, ui.rows.size, found, ui.rows.size),
            style = MaterialTheme.typography.titleSmall,
            // red only when a song really is missing; when only a choice is pending, the same orange as that row
            color = when {
                toImport > 0 -> MaterialTheme.colorScheme.error
                found < ui.rows.size -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
        )
        LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            contentPadding = PaddingValues(bottom = Spacing.s),
        ) {
            itemsIndexed(ui.rows) { i, row -> RowItem(row, { onChoose(i, it) }, { onCompare(i) }, { onWantChords(i, it) }, { onUseIncoming(i, it) }) }
        }
        // before the sites: ask the band on the network (only shows with a band member nearby)
        if (toImport > 0 && band > 0) {
            ActionButton(
                if (asking) stringResource(R.string.scan_asking_band) else pluralStringResource(R.plurals.scan_ask_band, band, band),
                onAskBand, Modifier.fillMaxWidth(), icon = AppIcons.Group, tone = ButtonTone.Tonal, enabled = !asking,
            )
        }
        // ask the sender (in the band) online, with no background (user's preference): they arrive later in Received
        if (toImport > 0 && internetFrom != null) {
            ActionButton(
                stringResource(R.string.scan_ask_internet, internetFrom), onAskInternet,
                Modifier.fillMaxWidth(), icon = AppIcons.Public, tone = ButtonTone.Quiet,
            )
        }
        if (toImport > 0) {
            ActionButton(
                pluralStringResource(R.plurals.scan_import_missing, toImport, toImport), { goImport() },
                Modifier.fillMaxWidth(), icon = AppIcons.Download, tone = ButtonTone.Tonal, enabled = ui.name.isNotBlank(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.m)) {
            if (canScanAgain) ActionButton(
                stringResource(if (byCode) R.string.scan_fetch_again else R.string.scan_again), onScanAgain, Modifier.weight(1f),
                icon = if (byCode) AppIcons.Public else AppIcons.QrCode, tone = ButtonTone.Tonal,
            )
            ActionButton(stringResource(R.string.scan_create), { go() }, Modifier.weight(1f), icon = AppIcons.Check, enabled = ui.name.isNotBlank() && found > 0)
        }
    }
    // "Create setlist" with missing songs: says what's left out and offers the right path (import), against a wrong tap
    if (confirmMissing) {
        AlertDialog(
            onDismissRequest = { confirmMissing = false },
            title = { Text(pluralStringResource(R.plurals.scan_missing_title, ui.missing.size, ui.missing.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(ui.missing.joinToString("\n") { "• " + it.title }, style = MaterialTheme.typography.bodyLarge)
                    if (toImport > 0) {
                        ActionButton(
                            pluralStringResource(R.plurals.scan_import_missing, toImport, toImport),
                            { confirmMissing = false; goImport() }, Modifier.fillMaxWidth(), icon = AppIcons.Download,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmMissing = false; goName() }) { Text(stringResource(R.string.scan_create_without)) } },
            dismissButton = { TextButton(onClick = { confirmMissing = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
    nameTaken?.let { id ->
        QuestionDialog(
            stringResource(R.string.scan_name_taken), stringResource(R.string.scan_name_taken_text, ui.name.trim()),
            stringResource(R.string.scan_replace), { nameTaken = null; finish(id) },
            stringResource(R.string.scan_create_other), { nameTaken = null; finish(null) },
            onOutside = { nameTaken = null },
        )
    }
}

// loose songs received: the new ones go into the library; the ones already here stay as they are
@Composable
private fun SongsReview(
    ui: ScanUi.Review,
    onAdd: () -> Unit,
    onCompare: (Int) -> Unit,
    onWantChords: (Int, Boolean) -> Unit = { _, _ -> },
    onUseIncoming: (Int, Boolean) -> Unit = { _, _ -> },
) {
    val new = ui.rows.count { it.carried }
    val updates = ui.rows.count { it.useIncoming }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            pluralStringResource(R.plurals.scan_songs_received, ui.rows.size, ui.rows.size) + " · " + pluralStringResource(R.plurals.scan_songs_new, new, new),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        )
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.s)) {
            itemsIndexed(ui.rows) { i, row ->
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    // the icon sits on the title line (centered, it floated next to the choice buttons)
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        val (icon, tint, note) = when {
                            row.wantChords || (row.carried && row.lyricsOnly) -> Triple(AppIcons.Download, MaterialTheme.colorScheme.tertiary, R.string.scan_song_new_lyrics_only)
                            row.carried -> Triple(AppIcons.Download, MaterialTheme.colorScheme.primary, R.string.scan_song_new)
                            row.differs -> Triple(AppIcons.Info, MaterialTheme.colorScheme.tertiary, R.string.scan_version_other)
                            row.found -> Triple(AppIcons.Check, MaterialTheme.colorScheme.onSurfaceVariant, R.string.scan_song_have)
                            else -> Triple(AppIcons.Warning, MaterialTheme.colorScheme.error, R.string.scan_song_missing)
                        }
                        Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = tint)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = tint)
                            if (row.differs) {
                                TwoChoices(stringResource(R.string.scan_keep_mine), stringResource(R.string.scan_use_theirs), second = row.useIncoming) { onUseIncoming(i, it) }
                                RowAction(AppIcons.Visibility, stringResource(R.string.scan_see_changes)) { onCompare(i) }
                            }
                            if ((row.carried && row.lyricsOnly) || row.wantChords) {
                                TwoChoices(stringResource(R.string.scan_add_lyrics), stringResource(R.string.scan_dont_add), second = row.wantChords) { onWantChords(i, it) }
                            }
                        }
                    }
                }
            }
        }
        // the label says everything the button does: new songs in, chosen versions updated
        ActionButton(
            when {
                new > 0 && updates > 0 -> stringResource(R.string.scan_songs_add_update, new, updates)
                new > 0 -> pluralStringResource(R.plurals.scan_songs_add, new, new)
                else -> pluralStringResource(R.plurals.scan_songs_update, updates, updates)
            },
            onAdd,
            Modifier.fillMaxWidth().padding(bottom = Spacing.m), icon = AppIcons.Check, enabled = new > 0 || updates > 0,
        )
    }
}

/** An invite to the band (user, 07/11): who invites, accept or not; then that it's done. */
@Composable
private fun InvitePanel(ui: ScanUi.Invite, onAccept: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (ui.joined) {
            br.app.stagebook.ui.components.StatusNote(stringResource(R.string.invite_joined, ui.name), br.app.stagebook.ui.components.StatusTone.OK)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ActionButton(stringResource(R.string.action_ok), onBack) }
        } else {
            Text(stringResource(R.string.invite_text, ui.name), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ActionButton(stringResource(R.string.invite_decline), onBack, icon = AppIcons.Close, tone = ButtonTone.Quiet)
                ActionButton(stringResource(R.string.invite_accept), onAccept, icon = AppIcons.Group)
            }
        }
    }
}

@Composable
private fun Fetching(ui: ScanUi.Fetching, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m), horizontalAlignment = Alignment.CenterHorizontally) {
        when (ui.problem) {
            null -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.scan_code_loading), style = MaterialTheme.typography.bodyLarge)
            }
            else -> {
                if (ui.problem == CodeProblem.NEWER) br.app.stagebook.ui.components.StatusBox(br.app.stagebook.ui.components.StatusTone.PROBLEM) {
                    br.app.stagebook.ui.components.SettingsLinkText(
                        stringResource(R.string.scan_newer_link), stringResource(R.string.scan_update_link),
                        br.app.stagebook.ui.settings.SettingsSection.ABOUT, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.LocalContentColor.current,
                    )
                } else br.app.stagebook.ui.components.StatusNote(
                    stringResource(
                        when (ui.problem) {
                            CodeProblem.NOT_FOUND -> R.string.scan_code_not_found
                            CodeProblem.NEWER -> R.string.scan_newer
                            CodeProblem.OFFLINE -> R.string.scan_code_offline
                        },
                    ),
                    br.app.stagebook.ui.components.StatusTone.PROBLEM,
                )
                ActionButton(stringResource(R.string.scan_code_retry), onRetry, icon = AppIcons.Undo, tone = ButtonTone.Tonal)
            }
        }
    }
}

// a song from the QR: found (✓), uncertain (pick or leave out) or not in the library (notice)
@Composable
private fun RowItem(
    row: ScanRow,
    onChoose: (String?) -> Unit,
    onCompare: () -> Unit = {},
    onWantChords: (Boolean) -> Unit = {},
    onUseIncoming: (Boolean) -> Unit = {},
) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val (icon, tint) = when {
                    row.found -> AppIcons.Check to MaterialTheme.colorScheme.primary
                    row.wantChords -> AppIcons.Public to MaterialTheme.colorScheme.tertiary
                    row.carried && row.lyricsOnly -> AppIcons.Download to MaterialTheme.colorScheme.tertiary
                    row.carried -> AppIcons.Download to MaterialTheme.colorScheme.primary
                    row.candidates.isEmpty() -> AppIcons.Warning to MaterialTheme.colorScheme.error
                    else -> AppIcons.Info to MaterialTheme.colorScheme.tertiary
                }
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                    if (row.artist.isNotEmpty()) Text(row.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                // I already have another version: Keep mine | Use theirs, and what changes
                row.found && row.differs -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.scan_version_other), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    TwoChoices(stringResource(R.string.scan_keep_mine), stringResource(R.string.scan_use_theirs), second = row.useIncoming) { onUseIncoming(it) }
                    RowAction(AppIcons.Visibility, stringResource(R.string.scan_see_changes), onCompare)
                }
                row.found -> Unit
                // lyrics and chords profile: one that came with lyrics only can be refused and searched with chords on
                // the sites. Came with lyrics only (lyrics and chords profile): both options side by side, the chosen
                // one marked
                row.wantChords || (row.carried && row.lyricsOnly) -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.scan_lyrics_only_row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    TwoChoices(stringResource(R.string.scan_accept_lyrics), stringResource(R.string.scan_search_chords), second = row.wantChords) { onWantChords(it) }
                }
                row.carried -> Text(
                    stringResource(
                        when {
                            row.lyricsOnly -> R.string.scan_lyrics_only_row
                            row.fromBand -> R.string.scan_band_row
                            else -> R.string.scan_carried_row
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                )
                row.candidates.isEmpty() -> Text(
                    stringResource(R.string.scan_missing_row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
                else -> OptionDropdown(
                    stringResource(R.string.scan_which),
                    listOf<SongRef?>(null) + row.candidates,
                    row.candidates.firstOrNull { it.id == row.chosen },
                    { onChoose(it?.id) },
                    { it?.let { s -> if (s.author.isEmpty()) s.title else "${s.title} · ${s.author}" } ?: stringResource(R.string.scan_leave_out) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// "See what changes": the lyrics lines leaving (mine) and coming in (theirs); my chords stay either way
@Composable
private fun ChangesDialog(changes: List<SongMerge.Change>, useIncoming: Boolean, onChoose: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_changes_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (changes.isEmpty()) item { Text(stringResource(R.string.scan_changes_chords_only), style = MaterialTheme.typography.bodyMedium) }
                itemsIndexed(changes) { _, c ->
                    val added = c.kind == SongMerge.Kind.ADDED
                    Text(
                        (if (added) "+ " else "− ") + c.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                item {
                    Text(stringResource(R.string.scan_changes_keep_chords), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(true) }) { Text(stringResource(R.string.scan_use_theirs)) } },
        dismissButton = { TextButton(onClick = { onChoose(false) }) { Text(stringResource(R.string.scan_keep_mine)) } },
    )
}

// action inside a review row: icon + text with no background (the app's button pattern, T35)
@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    // no start padding: the icon lines up with the row's text above
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 0.dp, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs)) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
    }
}

/**
 * Two options side by side, the chosen one marked ([second] = the right one): shows what will happen, without guessing.
 */
@Composable
private fun TwoChoices(first: String, secondLabel: String, second: Boolean, onChoose: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(first to false, secondLabel to true).forEachIndexed { i, (label, value) ->
            SegmentedButton(
                selected = second == value,
                onClick = { onChoose(value) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            )
        }
    }
}
