package com.juliano.stagebook.ui.setlists

import android.widget.Toast
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ButtonTone
import com.juliano.stagebook.ui.components.CenteredContent
import com.juliano.stagebook.ui.components.OptionDropdown
import com.juliano.stagebook.ui.components.ScreenHeader
import com.juliano.stagebook.ui.theme.Spacing

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
    var camera by rememberSaveable {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }
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
            Toast.makeText(context, resources.getQuantityString(R.plurals.songs_added, n, n), Toast.LENGTH_SHORT).show()
            onBack()
        },
        onBack = onBack,
        camera = { onText, modifier -> QrScanner(onText, modifier) },
        byCode = viewModel.code.isNotEmpty(),
        fromName = viewModel.fromName,
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
) {
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
                is ScanUi.Review -> if (ui.songsOnly) SongsReview(ui, onAddSongs) else Review(ui, onName, onChoose, onScanAgain, existing, onCreate, onCreateAndImport, byCode)
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
                stringResource(if (p == ScanProblem.INCOMPLETE) R.string.scan_incomplete else R.string.scan_not_setlist),
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
            itemsIndexed(ui.rows) { i, row -> RowItem(row) { onChoose(i, it) } }
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
    if (confirmMissing) {
        AlertDialog(
            onDismissRequest = { confirmMissing = false },
            title = { Text(pluralStringResource(R.plurals.scan_missing_title, ui.missing.size, ui.missing.size)) },
            text = { Text(ui.missing.joinToString("\n") { "• " + it.title }, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { confirmMissing = false; goName() }) { Text(stringResource(R.string.scan_create_anyway)) } },
            dismissButton = { TextButton(onClick = { confirmMissing = false }) { Text(stringResource(R.string.action_back)) } },
        )
    }
    nameTaken?.let { id ->
        AlertDialog(
            onDismissRequest = { nameTaken = null },
            title = { Text(stringResource(R.string.scan_name_taken)) },
            text = { Text(stringResource(R.string.scan_name_taken_text, ui.name.trim()), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { nameTaken = null; finish(id) }) { Text(stringResource(R.string.scan_replace)) } },
            dismissButton = { TextButton(onClick = { nameTaken = null; finish(null) }) { Text(stringResource(R.string.scan_create_other)) } },
        )
    }
}

// músicas avulsas recebidas: as novas entram na biblioteca; as que já estão aqui ficam como estão
@Composable
private fun SongsReview(ui: ScanUi.Review, onAdd: () -> Unit) {
    val new = ui.rows.count { it.carried }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            pluralStringResource(R.plurals.scan_songs_summary, ui.rows.size, ui.rows.size, new),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        )
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.s)) {
            itemsIndexed(ui.rows) { _, row ->
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        val (icon, tint, note) = when {
                            row.carried -> Triple(AppIcons.Download, MaterialTheme.colorScheme.primary, R.string.scan_song_new)
                            row.found -> Triple(AppIcons.Check, MaterialTheme.colorScheme.onSurfaceVariant, R.string.scan_song_have)
                            else -> Triple(AppIcons.Warning, MaterialTheme.colorScheme.error, R.string.scan_song_missing)
                        }
                        Icon(icon, null, Modifier.size(20.dp), tint = tint)
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = tint)
                        }
                    }
                }
            }
        }
        ActionButton(
            pluralStringResource(R.plurals.scan_songs_add, new, new), onAdd,
            Modifier.fillMaxWidth().padding(bottom = Spacing.m), icon = AppIcons.Check, enabled = new > 0,
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
                    stringResource(if (ui.problem == CodeProblem.NOT_FOUND) R.string.scan_code_not_found else R.string.scan_code_offline),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
                )
                ActionButton(stringResource(R.string.scan_code_retry), onRetry, icon = AppIcons.Undo, tone = ButtonTone.Tonal)
            }
        }
    }
}

// uma música do QR: achada (✓), com dúvida (escolher ou deixar de fora) ou que não está na biblioteca (aviso)
@Composable
private fun RowItem(row: ScanRow, onChoose: (String?) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val (icon, tint) = when {
                    row.found -> AppIcons.Check to MaterialTheme.colorScheme.primary
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
                row.found -> Unit
                row.carried -> Text(stringResource(R.string.scan_carried_row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
