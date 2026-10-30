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
    LaunchedEffect(Unit) { if (!camera && viewModel.code.isEmpty()) ask.launch(Manifest.permission.CAMERA) }
    ScanSetlistScreen(
        ui = ui,
        cameraAllowed = camera,
        onAskCamera = { ask.launch(Manifest.permission.CAMERA) },
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
        camera = { onText, modifier -> QrScanner(onText, modifier) },
        byCode = viewModel.code.isNotEmpty(),
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
    )
}

/**
 * Ler o QR de um setlist e conferir antes de criar. [camera] é a câmera de verdade no app e
 * um quadro vazio nos testes e prints.
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
    onRetry: () -> Unit = {},
    onAddSongs: () -> Unit = {},
    fromName: String = "",   // veio da banda: "Recebido de Ana"
    band: Int = 0,           // da banda na rede agora
    asking: Boolean = false,
    onAskBand: () -> Unit = {},
    internetFrom: String? = null,
    onAskInternet: () -> Unit = {},
    onBandSearch: (Boolean) -> Unit = {},
    changes: (Int) -> List<SongMerge.Change> = { emptyList() },
    onUseIncoming: (Int, Boolean) -> Unit = { _, _ -> },
    onWantChords: (Int, Boolean) -> Unit = { _, _ -> },
) {
    var comparing by rememberSaveable { mutableStateOf<Int?>(null) }   // linha com "Ver o que muda" aberto
    CenteredContent(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            ScreenHeader(
                if (fromName.isNotEmpty()) stringResource(R.string.scan_from, fromName) else stringResource(
                    when {
                        (ui as? ScanUi.Review)?.songsOnly == true -> R.string.scan_songs_title
                        byCode -> R.string.scan_code_title
                        else -> R.string.scan_title
                    },
                ),
                Modifier.padding(top = Spacing.s, bottom = Spacing.m), onBack = onBack,
            )
            when (ui) {
                is ScanUi.Scanning -> Scanning(ui, cameraAllowed, onAskCamera, onText, camera)
                is ScanUi.Fetching -> Fetching(ui, onRetry)
                is ScanUi.Review -> {
                    // faltando música: procura a banda enquanto a conferência está aberta (para pedir a ela antes dos sites)
                    val missing = !ui.songsOnly && ui.rows.any { it.chosen == null && !it.carried && it.candidates.isEmpty() }
                    DisposableEffect(missing) {
                        if (missing) onBandSearch(true)
                        onDispose { if (missing) onBandSearch(false) }
                    }
                    if (ui.songsOnly) SongsReview(ui, onAddSongs, { comparing = it }, onWantChords, onUseIncoming)
                    else Review(ui, onName, onChoose, onScanAgain, existing, onCreate, onCreateAndImport, byCode, band, asking, onAskBand, { comparing = it }, onWantChords, onUseIncoming, internetFrom, onAskInternet)
                    comparing?.let { i ->
                        ChangesDialog(
                            changes(i), useIncoming = ui.rows.getOrNull(i)?.useIncoming == true,
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyLarge)
        if (!cameraAllowed) {
            Text(stringResource(R.string.scan_no_camera), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            ActionButton(stringResource(R.string.scan_allow_camera), onAskCamera, icon = AppIcons.QrCode)
            return@Column
        }
        Box(
            Modifier.fillMaxWidth().weight(1f, fill = false).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            camera(onText, Modifier.fillMaxSize())
        }
        ui.problem?.let { p ->
            Text(
                stringResource(
                    when (p) {
                        ScanProblem.INCOMPLETE -> R.string.scan_incomplete
                        ScanProblem.NEWER -> R.string.scan_newer
                        ScanProblem.NOT_SETLIST -> R.string.scan_not_setlist
                    },
                ),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
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
    var importing by rememberSaveable { mutableStateOf(false) }   // o nome repetido veio de "Importar as que faltam"
    fun finish(replace: String?) = if (importing) onCreateAndImport(replace) else onCreate(replace)
    // primeiro avisa o que fica de fora; depois, se o nome já existe, pergunta se troca ou cria outro
    fun goName() { existing(ui.name.trim())?.let { nameTaken = it } ?: finish(null) }
    fun go() { importing = false; if (ui.missing.isNotEmpty()) confirmMissing = true else goName() }
    // importar as que faltam: o setlist é criado com as achadas e o importador abre buscando a primeira que falta
    fun goImport() { importing = true; goName() }
    val toImport = ui.rows.count { it.chosen == null && !it.carried && it.candidates.isEmpty() }

    val found = ui.rows.count { it.chosen != null || it.carried }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(ui.name, onName, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.scan_name)) }, singleLine = true)
        Text(
            pluralStringResource(R.plurals.scan_found, ui.rows.size, found, ui.rows.size),
            style = MaterialTheme.typography.titleSmall,
            color = if (found < ui.rows.size) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            contentPadding = PaddingValues(bottom = Spacing.s),
        ) {
            itemsIndexed(ui.rows) { i, row -> RowItem(row, { onChoose(i, it) }, { onCompare(i) }, { onWantChords(i, it) }, { onUseIncoming(i, it) }) }
        }
        // antes dos sites: pedir à banda na rede (só aparece com alguém da banda por perto)
        if (toImport > 0 && band > 0) {
            ActionButton(
                if (asking) stringResource(R.string.scan_asking_band) else pluralStringResource(R.plurals.scan_ask_band, band, band),
                onAskBand, Modifier.fillMaxWidth(), icon = AppIcons.Group, tone = ButtonTone.Tonal, enabled = !asking,
            )
        }
        // pedir pela internet a quem mandou (da banda), sem fundo (preferência do usuário): elas chegam depois nos Recebidos
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
            ActionButton(
                stringResource(if (byCode) R.string.scan_fetch_again else R.string.scan_again), onScanAgain, Modifier.weight(1f),
                icon = if (byCode) AppIcons.Public else AppIcons.QrCode, tone = ButtonTone.Tonal,
            )
            ActionButton(stringResource(R.string.scan_create), { go() }, Modifier.weight(1f), icon = AppIcons.Check, enabled = ui.name.isNotBlank() && found > 0)
        }
    }
    // "Criar setlist" com música faltando: diz o que fica de fora e oferece o caminho certo (importar), contra o toque errado
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

// músicas avulsas recebidas: as novas entram na biblioteca; as que já estão aqui ficam como estão
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
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        val (icon, tint, note) = when {
                            row.wantChords || (row.carried && row.lyricsOnly) -> Triple(AppIcons.Download, MaterialTheme.colorScheme.tertiary, R.string.scan_song_new_lyrics_only)
                            row.carried -> Triple(AppIcons.Download, MaterialTheme.colorScheme.primary, R.string.scan_song_new)
                            row.differs -> Triple(AppIcons.Info, MaterialTheme.colorScheme.tertiary, R.string.scan_version_other)
                            row.found -> Triple(AppIcons.Check, MaterialTheme.colorScheme.onSurfaceVariant, R.string.scan_song_have)
                            else -> Triple(AppIcons.Warning, MaterialTheme.colorScheme.error, R.string.scan_song_missing)
                        }
                        Icon(icon, null, Modifier.size(20.dp), tint = tint)
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
        ActionButton(
            if (new > 0) pluralStringResource(R.plurals.scan_songs_add, new, new) else stringResource(R.string.scan_update_chosen), onAdd,
            Modifier.fillMaxWidth().padding(bottom = Spacing.m), icon = AppIcons.Check, enabled = new > 0 || updates > 0,
        )
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
                Text(
                    stringResource(
                        when (ui.problem) {
                            CodeProblem.NOT_FOUND -> R.string.scan_code_not_found
                            CodeProblem.NEWER -> R.string.scan_newer
                            CodeProblem.OFFLINE -> R.string.scan_code_offline
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
                )
                ActionButton(stringResource(R.string.scan_code_retry), onRetry, icon = AppIcons.Undo, tone = ButtonTone.Tonal)
            }
        }
    }
}

// uma música do QR: achada (✓), com dúvida (escolher ou deixar de fora) ou que não está na biblioteca (aviso)
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
                // já tenho outra versão: Fica a minha | Usar a recebida, e o que muda
                row.found && row.differs -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.scan_version_other), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    TwoChoices(stringResource(R.string.scan_keep_mine), stringResource(R.string.scan_use_theirs), second = row.useIncoming) { onUseIncoming(it) }
                    RowAction(AppIcons.Visibility, stringResource(R.string.scan_see_changes), onCompare)
                }
                row.found -> Unit
                // perfil letra e cifra: a que veio só com a letra pode ser recusada e ir buscar com cifra nos sites
                // veio só com a letra (perfil letra e cifra): as duas opções lado a lado, a escolhida marcada
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

// "Ver o que muda": as linhas da letra que saem (a minha) e entram (a recebida); a minha cifra fica de qualquer jeito
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

// ação dentro de uma linha da conferência: ícone + texto sem fundo (padrão dos botões do app, T35)
@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs)) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
    }
}

/** Duas opções lado a lado, a escolhida marcada ([second] = a da direita): mostra o que vai acontecer, sem adivinhar. */
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
