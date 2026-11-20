package br.app.stagebook.ui.setlists

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.ui.components.InfoDialog
import br.app.stagebook.ui.components.QuestionDialog
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.components.SearchField
import br.app.stagebook.ui.components.SongFacts
import br.app.stagebook.ui.components.SongListItem
import br.app.stagebook.ui.components.wideLists
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing

@Composable
fun PickSongsRoute(onDone: () -> Unit, viewModel: PickSongsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val limit by viewModel.limit.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.onQueryChange(query) }
    // leaving with songs checked and the green check forgotten (back): they go in as if it was tapped. The screen being
    // rebuilt (the pinned menu toggled, the window crossing 600 dp) is not leaving: the checks wait in the saved state
    val leave = { viewModel.confirmPending(); onDone() }
    androidx.activity.compose.BackHandler(enabled = uiState.selectedCount > 0, onBack = leave)
    // closing the menu with songs checked asks first, as a setlist half edited does (user, 15/11)
    var asking by rememberSaveable { mutableStateOf(false) }
    val guard = br.app.stagebook.ui.drawer.LocalDrawerGuard.current
    val drawer = br.app.stagebook.ui.drawer.LocalDrawer.current
    val pending by androidx.compose.runtime.rememberUpdatedState(uiState.selectedCount > 0)
    // answered: lets the close that follows through at once (the count drops only at the next composition)
    val answered = androidx.compose.runtime.remember { booleanArrayOf(false) }
    LaunchedEffect(uiState.selectedCount) { if (uiState.selectedCount > 0) answered[0] = false }
    androidx.compose.runtime.DisposableEffect(guard) {
        val asks: () -> Boolean = { if (pending && !answered[0]) { asking = true; true } else false }
        guard?.add(asks)
        onDispose { guard?.remove(asks) }
    }
    if (asking) QuestionDialog(
        stringResource(R.string.pick_title),
        stringResource(R.string.pick_ask_add, uiState.selectedCount, uiState.setName),
        stringResource(R.string.pick_add_yes), { asking = false; viewModel.confirmPending(); answered[0] = true; drawer?.close?.invoke() },
        stringResource(R.string.set_discard), { asking = false; viewModel.clearPending(); answered[0] = true; drawer?.close?.invoke() },
        onOutside = { asking = false },
        cancelLabel = stringResource(R.string.set_keep_editing), onCancel = { asking = false },
    )
    if (limit) InfoDialog(stringResource(R.string.qr_limit_title), stringResource(R.string.qr_limit_message), viewModel::dismissLimit)
    PickSongsScreen(
        uiState = uiState,
        query = query,
        onQueryChange = { query = it },
        // checked one: the search clears (with the keyboard open) for the next song
        onToggle = { if (viewModel.toggle(it)) query = "" },
        onConfirm = { viewModel.confirm(); onDone() },
        onBack = leave,
        onTagsChange = viewModel::onTagsChange,
    )
}

@Composable
fun PickSongsScreen(
    uiState: PickSongsUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTagsChange: (Set<String>) -> Unit = {},
) {
    val wide = wideLists()
    Scaffold(
        contentWindowInsets = ScreenInsets,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        CenteredContent(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = Spacing.m)) {
                ScreenHeader(
                    // as the open setlist (user, 19/11): the way back names the setlist ("‹ Show de sábado"), and what this screen
                    // does is the big title under it, with the whole width (beside the icons it broke in two)
                    title = stringResource(R.string.pick_screen_title),
                    parent = uiState.setName,
                    onParent = onBack,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                    iconActions = true,
                ) {
                    // in the fixed header: the search keyboard covered the button when it sat at the bottom
                    // an icon, as the rest of the menu (the check; its name says how many go in)
                    // with the number of songs the setlist will have: the ones it already has and the ones checked
                    val total = uiState.inSetCount + uiState.selectedCount
                    val addLabel = stringResource(R.string.pick_add_short, uiState.selectedCount)
                    // the number beside the check, not on it: as a badge it covered the drawing (user, 19/11)
                    androidx.compose.material3.TextButton(onClick = onConfirm, enabled = uiState.selectedCount > 0) {
                        androidx.compose.material3.Icon(
                            AppIcons.Check, addLabel,
                            tint = if (uiState.selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        if (total > 0) androidx.compose.material3.Badge(Modifier.padding(start = Spacing.xs)) { Text(total.toString()) }
                    }
                }
                br.app.stagebook.ui.components.SearchWithTags(
                    query, onQueryChange, stringResource(R.string.songs_search), uiState.tags, uiState.chosenTags, onTagsChange,
                    Modifier.padding(bottom = Spacing.m),
                )
                if (uiState.rows.isEmpty()) {
                    EmptyState(AppIcons.Search, stringResource(R.string.songs_not_found), "")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(if (wide) Spacing.xs else Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
                        items(uiState.rows, key = { it.id }) { row ->
                            SongListItem(
                                title = row.title,
                                subtitle = row.subtitle,
                                // already in the setlist: faded, with its box ticked and off (no phrase: the box says it)
                                modifier = if (row.inSet) Modifier.alpha(IN_SET_ALPHA) else Modifier,
                                onClick = { if (!row.inSet) onToggle(row.id) },
                                // the box and, once chosen, the place in the order they will be added (as SongbookPro)
                                leading = {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Checkbox(checked = row.selected || row.inSet, onCheckedChange = null, enabled = !row.inSet)
                                        if (row.order > 0) br.app.stagebook.ui.components.NumberBadge(row.order, compact = true)
                                    }
                                },
                                trailing = { SongFacts(row.songKey, row.bpm, row.capo, row.hasTrack) },
                                titleStyle = MaterialTheme.typography.titleMedium,
                                dense = true,
                                titleLines = 1,
                                gap = Spacing.s,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val IN_SET_ALPHA = 0.5f

private val previewState = PickSongsUiState(
    setName = "Show de sábado",
    rows = listOf(
        PickRowUi("1", "Hazard", "Richard Marx", inSet = true, selected = false, songKey = "Em", bpm = 120, capo = 2, hasTrack = true),
        PickRowUi("2", "Malandragem", "Cássia Eller", inSet = false, selected = true, songKey = "G"),
        PickRowUi("3", "Tempo Perdido", "Legião Urbana", inSet = false, selected = false, songKey = "C", bpm = 118),
    ),
    selectedCount = 1,
)

@LightPreview
@Composable
private fun PickSongsLightPreview() = StagebookTheme(darkTheme = false) { PickSongsScreen(previewState, "", {}, {}, {}, {}) }

@DarkPreview
@Composable
private fun PickSongsDarkPreview() = StagebookTheme(darkTheme = true) { PickSongsScreen(previewState, "", {}, {}, {}, {}) }
