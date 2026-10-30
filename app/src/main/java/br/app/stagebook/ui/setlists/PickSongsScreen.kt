package br.app.stagebook.ui.setlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.ui.components.InfoDialog
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.components.SearchField
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
    if (limit) InfoDialog(stringResource(R.string.qr_limit_title), stringResource(R.string.qr_limit_message), viewModel::dismissLimit)
    PickSongsScreen(
        uiState = uiState,
        query = query,
        onQueryChange = { query = it },
        // marcou uma: a busca fica limpa (e com o teclado aberto) para a próxima música
        onToggle = { if (viewModel.toggle(it)) query = "" },
        onConfirm = { viewModel.confirm(); onDone() },
        onBack = onDone,
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
                    title = stringResource(R.string.pick_title),
                    subtitle = uiState.setName,
                    onBack = onBack,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
                ) {
                    // no cabeçalho fixo: o teclado da busca cobria o botão quando ele ficava embaixo
                    ActionButton(
                        text = stringResource(R.string.pick_add_short, uiState.selectedCount),
                        onClick = onConfirm,
                        icon = AppIcons.Check,
                        enabled = uiState.selectedCount > 0,
                    )
                }
                SearchField(query, onQueryChange, stringResource(R.string.songs_search), Modifier.padding(bottom = Spacing.m))
                if (uiState.rows.isEmpty()) {
                    EmptyState(AppIcons.Search, stringResource(R.string.songs_not_found), "")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(if (wide) Spacing.xs else Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
                        items(uiState.rows, key = { it.id }) { row ->
                            SongListItem(
                                title = row.title,
                                subtitle = if (row.inSet) stringResource(R.string.pick_in_set) else row.subtitle,
                                onClick = { if (!row.inSet) onToggle(row.id) },
                                inline = wide,
                                leading = { Checkbox(checked = row.selected || row.inSet, onCheckedChange = null, enabled = !row.inSet) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val previewState = PickSongsUiState(
    setName = "Show de sábado",
    rows = listOf(
        PickRowUi("1", "Hazard", "Richard Marx · 1991", inSet = true, selected = false),
        PickRowUi("2", "Malandragem", "Cássia Eller · 1994", inSet = false, selected = true),
        PickRowUi("3", "Tempo Perdido", "Legião Urbana · 1986", inSet = false, selected = false),
    ),
    selectedCount = 1,
)

@LightPreview
@Composable
private fun PickSongsLightPreview() = StagebookTheme(darkTheme = false) { PickSongsScreen(previewState, "", {}, {}, {}, {}) }

@DarkPreview
@Composable
private fun PickSongsDarkPreview() = StagebookTheme(darkTheme = true) { PickSongsScreen(previewState, "", {}, {}, {}, {}) }
