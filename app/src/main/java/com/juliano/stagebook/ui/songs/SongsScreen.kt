package com.juliano.stagebook.ui.songs

import com.juliano.stagebook.ui.share.rememberCodeActions
import com.juliano.stagebook.ui.share.ShareSheet
import com.juliano.stagebook.ui.share.BandResultMessage
import com.juliano.stagebook.ui.share.ImportButton
import com.juliano.stagebook.ui.share.CodeActions
import com.juliano.stagebook.ui.share.CloudDialog
import com.juliano.stagebook.ui.share.CloudUi
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.activity.compose.BackHandler
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
import com.juliano.stagebook.R
import com.juliano.stagebook.ui.components.ActionButton
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.ButtonTone
import com.juliano.stagebook.ui.components.CenteredContent
import com.juliano.stagebook.ui.components.ConfirmDialog
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.EmptyState
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.ScreenHeader
import com.juliano.stagebook.ui.components.SearchField
import com.juliano.stagebook.ui.components.SongListItem
import com.juliano.stagebook.ui.components.MenuAction
import com.juliano.stagebook.ui.components.OverflowMenu
import com.juliano.stagebook.ui.components.wideLists
import com.juliano.stagebook.ui.theme.StagebookTheme
import com.juliano.stagebook.ui.theme.Spacing

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
        // achou o que procurava: na volta do palco a lista vem inteira
        onOpen = { query = ""; onOpenSong(it) },
        onEdit = onEditSong,
        onDelete = viewModel::delete,
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
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    online: Boolean = true,
    internetOn: Boolean = true,
    cloud: CloudUi? = null,
    onShareSongs: (List<String>) -> Unit = {},
    onDismissCloud: () -> Unit = {},
    codeActions: CodeActions = CodeActions({}, { _, _ -> }),
    initialSelection: List<String> = emptyList(),   // prints e testes
    onScan: () -> Unit = {},
    onReceiveCode: (String) -> Unit = {},
    band: Int = 0,
    onSendBand: (List<String>) -> Unit = {},
    onBandSearch: (Boolean) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    val wide = wideLists()
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    // seleção para compartilhar várias: segurar uma música (ou ⋮ → Compartilhar) começa; voltar ou ✕ termina
    var selected by rememberSaveable { mutableStateOf(initialSelection) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    BackHandler(enabled = selecting) { selected = emptyList() }
    CenteredContent(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            ScreenHeader(
                title = stringResource(R.string.songs_title),
                subtitle = pluralStringResource(R.plurals.song_count, uiState.total, uiState.total),
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            )
            SearchField(query, onQueryChange, stringResource(R.string.songs_search))
            if (selecting) {
                // barra da seleção no lugar de Nova/Importar: quantas, Compartilhar e sair
                Row(Modifier.padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    IconButton(onClick = { selected = emptyList() }) { Icon(AppIcons.Close, stringResource(R.string.songs_select_cancel)) }
                    Text(
                        pluralStringResource(R.plurals.songs_selected, selected.size, selected.size),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    )
                    ActionButton(stringResource(R.string.share_title), { sharing = true }, icon = AppIcons.Upload, tone = ButtonTone.Tonal)
                }
            } else Row(Modifier.padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // secundário no começo, a ação principal no fim (como nos apps do Google)
                // importar dos sites (com internet), ler o QR ou digitar o código: um botão, um menu
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
                                // só o sumiço ao apagar: entrar e deslocar animavam dezenas de linhas a cada letra da busca
                                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = null),
                                titleStyle = MaterialTheme.typography.titleLarge,
                                inline = wide,
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(AppIcons.Play, contentDescription = stringResource(R.string.action_stage), tint = MaterialTheme.colorScheme.primary)
                                        // editar e apagar no ⋮ (padrão do Google), à vista na linha
                                        OverflowMenu(
                                            listOf(
                                                MenuAction(stringResource(R.string.action_edit), AppIcons.Edit, { onEdit(song.id) }),
                                                MenuAction(stringResource(R.string.share_title), AppIcons.Upload, { selected = listOf(song.id); sharing = true }),
                                                MenuAction(stringResource(R.string.action_delete), AppIcons.Delete, { pendingDelete = song.id }, danger = true),
                                            ),
                                        )
                                    }
                                },
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
    pendingDelete?.let { id ->
        val title = uiState.songs.firstOrNull { it.id == id }?.title.orEmpty()
        ConfirmDialog(
            message = stringResource(R.string.songs_delete_confirm, title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onDelete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
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
    SongsScreen(previewState, "", {}, {}, {}, {}, {}, {})
}

@DarkPreview
@Composable
private fun SongsScreenDarkPreview() = StagebookTheme(darkTheme = true) {
    SongsScreen(previewState, "", {}, {}, {}, {}, {}, {})
}
