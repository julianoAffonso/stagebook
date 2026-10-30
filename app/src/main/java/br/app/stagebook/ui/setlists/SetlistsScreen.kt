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
        onReceiveCode = onReceiveCode,
        received = received,
        onReceived = onReceived,
        onSync = onSync,
        onDelete = viewModel::delete,
        onRename = viewModel::rename,
    )
}

@Composable
fun SetlistsScreen(
    uiState: SetlistsUiState,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onScan: () -> Unit = {},
    online: Boolean = false,
    internetOn: Boolean = true,
    onReceiveCode: (String) -> Unit = {},
    received: Int = 0,
    onReceived: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    CenteredContent(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.m)) {
            ScreenHeader(
                title = stringResource(R.string.sets_title),
                subtitle = pluralStringResource(R.plurals.set_count, uiState.sets.size, uiState.sets.size),
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            )
            // import one (QR or code) at the start and the main action, new setlist, at the end (same as the Songs tab)
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ImportButton(online, onScan, onReceiveCode, Modifier.weight(1f), received = received, onReceived = onReceived, onSync = onSync, internetOn = internetOn)
                ActionButton(stringResource(R.string.sets_new), onClick = { creating = true }, modifier = Modifier.weight(1f), icon = AppIcons.Add)
            }
            Crossfade(targetState = uiState.loading, label = "sets") { loading ->
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.sets.isEmpty() -> EmptyState(AppIcons.QueueMusic, stringResource(R.string.sets_empty_title), stringResource(R.string.sets_empty_message))
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.l)) {
                        items(uiState.sets, key = { it.id }) { set ->
                            SongListItem(
                                title = set.name,
                                subtitle = pluralStringResource(R.plurals.song_count, set.count, set.count),
                                onClick = { onOpen(set.id) },
                                modifier = Modifier.animateItem(),
                                titleStyle = MaterialTheme.typography.titleLarge,
                                trailing = {
                                    Row {
                                        IconButton(onClick = { onPlay(set.id) }, enabled = set.count > 0) {
                                            Icon(
                                                AppIcons.Play,
                                                contentDescription = stringResource(R.string.action_stage),
                                                tint = if (set.count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        // rename and delete in the ⋮ (Google's pattern), without opening the setlist
                                        OverflowMenu(
                                            listOf(
                                                MenuAction(stringResource(R.string.sets_rename), AppIcons.Edit, { renaming = set.id }),
                                                MenuAction(stringResource(R.string.sets_delete), AppIcons.Delete, { pendingDelete = set.id }, danger = true),
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
    renaming?.let { id ->
        val name = uiState.sets.firstOrNull { it.id == id }?.name.orEmpty()
        TextInputDialog(stringResource(R.string.sets_rename), name, stringResource(R.string.sets_name), { onRename(id, it) }, { renaming = null })
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
    pendingDelete?.let { id ->
        ConfirmDialog(
            message = stringResource(R.string.sets_delete_confirm, uiState.sets.firstOrNull { it.id == id }?.name.orEmpty()),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onDelete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

private val previewState = SetlistsUiState(
    loading = false,
    sets = listOf(SetlistRowUi("1", "Show de sábado", 15), SetlistRowUi("2", "Ensaio 18/10", 6)),
)

@LightPreview
@Composable
private fun SetlistsScreenLightPreview() = StagebookTheme(darkTheme = false) { SetlistsScreen(previewState, {}, {}, {}, {}, { _, _ -> }) }

@DarkPreview
@Composable
private fun SetlistsScreenDarkPreview() = StagebookTheme(darkTheme = true) { SetlistsScreen(previewState, {}, {}, {}, {}, { _, _ -> }) }
