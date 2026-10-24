package com.juliano.stagebook.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juliano.stagebook.R
import com.juliano.stagebook.data.SyncKind
import com.juliano.stagebook.domain.LineDiff
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.CenteredContent
import com.juliano.stagebook.ui.components.ConfirmDialog
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.EmptyState
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.ScreenHeader
import com.juliano.stagebook.ui.components.ScreenInsets
import com.juliano.stagebook.ui.theme.StagebookTheme
import com.juliano.stagebook.ui.theme.Spacing

@Composable
fun SyncDetailsRoute(onBack: () -> Unit, onOpenSong: (String) -> Unit, viewModel: SyncDetailsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val refusedText = stringResource(R.string.sync_undo_refused)
    LaunchedEffect(viewModel) { viewModel.refused.collect { snackbar.showSnackbar(refusedText) } }
    SyncDetailsScreen(
        uiState,
        SyncDetailsActions(onBack, onOpenSong, viewModel::keepNew, viewModel::keepMine, viewModel::removeAdded, viewModel::acceptAll, viewModel::keepAll),
        snackbar,
    )
}

/** Interações da revisão da atualização. */
data class SyncDetailsActions(
    val onBack: () -> Unit = {},
    val onOpenSong: (String) -> Unit = {},
    val onKeepNew: (String) -> Unit = {},
    val onKeepMine: (String) -> Unit = {},
    val onRemoveAdded: (String) -> Unit = {},
    val onAcceptAll: (SyncGroup) -> Unit = {},
    val onKeepAll: (SyncGroup) -> Unit = {},   // nas novas: remover todas
)

/**
 * Revisão da última atualização das músicas: cada música mostra o que mudou (linhas novas em destaque,
 * retiradas riscadas) e o usuário escolhe com qual versão fica.
 */
@Composable
fun SyncDetailsScreen(
    uiState: SyncDetailsUiState,
    actions: SyncDetailsActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var keepingMine by rememberSaveable { mutableStateOf<String?>(null) }   // cartão: confirma antes
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    // lote que troca letras ou remove músicas: confirma com a quantidade (grupo, aceitar?)
    var batch by rememberSaveable { mutableStateOf<Pair<SyncGroup, Boolean>?>(null) }
    // lote que não muda nenhuma letra: direto
    val onBatch: (SyncGroup, Boolean) -> Unit = { group, accept ->
        val changesLyrics = (group == SyncGroup.CHANGED && !accept) || (group == SyncGroup.PROTECTED && accept) || (group == SyncGroup.ADDED && !accept)
        if (changesLyrics) batch = group to accept else if (accept) actions.onAcceptAll(group) else actions.onKeepAll(group)
    }
    Scaffold(contentWindowInsets = ScreenInsets, containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        CenteredContent(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxHeight().padding(horizontal = Spacing.m)) {
                // cabeçalho fixo; só a lista rola
                ScreenHeader(
                    title = stringResource(R.string.sync_details_title),
                    subtitle = if (uiState.date.isEmpty()) "" else stringResource(R.string.sync_details_when, uiState.date),
                    onBack = actions.onBack,
                    modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.s),
                )
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    contentPadding = PaddingValues(bottom = Spacing.l),
                ) {
                    if (uiState.updating) item { Note(stringResource(R.string.sync_updating), highlight = true) }
                    uiState.lastCheck?.let { c -> item { Note(lastCheckText(c)) } }
                    when {
                        uiState.loading -> item {
                            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        uiState.empty -> item {
                            EmptyState(AppIcons.Download, stringResource(R.string.sync_details_empty_title), stringResource(R.string.sync_details_empty_message))
                        }
                        else -> {
                            item { Note(stringResource(R.string.sync_details_intro)) }
                            if (uiState.rebuilt) item { Note(stringResource(R.string.sync_details_rebuilt)) }
                            cards(SyncGroup.CHANGED, R.string.sync_details_changed, uiState.changed, actions, onBatch, onKeepMine = { keepingMine = it }, onRemove = {})
                            // aqui "manter a atual" não muda nada na letra: sem confirmação
                            cards(SyncGroup.PROTECTED, R.string.sync_details_protected, uiState.protectedSongs, actions, onBatch, onKeepMine = actions.onKeepMine, onRemove = {})
                            cards(SyncGroup.ADDED, R.string.sync_details_added, uiState.added, actions, onBatch, onKeepMine = {}, onRemove = { removing = it })
                        }
                    }
                }
            }
        }
    }
    keepingMine?.let { id ->
        ConfirmDialog(
            stringResource(R.string.sync_keep_mine_confirm, uiState.changed.firstOrNull { it.key == id }?.title.orEmpty()),
            stringResource(R.string.sync_keep_mine),
            onConfirm = { actions.onKeepMine(id) },
            onDismiss = { keepingMine = null },
            destructive = false,
        )
    }
    batch?.let { (group, accept) ->
        val pending = when (group) {
            SyncGroup.CHANGED -> uiState.changed
            SyncGroup.PROTECTED -> uiState.protectedSongs
            SyncGroup.ADDED -> uiState.added
        }.count { it.choice == SyncChoice.PENDING }
        val (message, label) = when (group) {
            SyncGroup.CHANGED -> R.plurals.sync_keep_all_confirm to R.string.sync_keep_all
            SyncGroup.PROTECTED -> R.plurals.sync_accept_all_confirm to R.string.sync_accept_all
            SyncGroup.ADDED -> R.plurals.sync_remove_all_confirm to R.string.sync_remove_all
        }
        ConfirmDialog(
            pluralStringResource(message, pending, pending),
            stringResource(label),
            onConfirm = { if (accept) actions.onAcceptAll(group) else actions.onKeepAll(group) },
            onDismiss = { batch = null },
            destructive = group == SyncGroup.ADDED,
        )
    }
    removing?.let { id ->
        ConfirmDialog(
            stringResource(R.string.sync_remove_confirm, uiState.added.firstOrNull { it.key == id }?.title.orEmpty()),
            stringResource(R.string.sync_remove_added),
            onConfirm = { actions.onRemoveAdded(id) },
            onDismiss = { removing = null },
        )
    }
}

private fun LazyListScope.cards(
    group: SyncGroup,
    title: Int,
    cards: List<SyncCardUi>,
    actions: SyncDetailsActions,
    onBatch: (SyncGroup, Boolean) -> Unit,
    onKeepMine: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (cards.isEmpty()) return
    item(key = "h$title") {
        Column(Modifier.padding(top = Spacing.m)) {
            Text(
                "${stringResource(title)} (${cards.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // muitas músicas (ex.: 163 com seções próprias): a mesma escolha para todas as pendentes
            if (cards.count { it.choice == SyncChoice.PENDING } >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TextButton(onClick = { onBatch(group, true) }) { Text(stringResource(R.string.sync_accept_all)) }
                    TextButton(onClick = { onBatch(group, false) }) {
                        if (group == SyncGroup.ADDED) Text(stringResource(R.string.sync_remove_all), color = MaterialTheme.colorScheme.error)
                        else Text(stringResource(R.string.sync_keep_all))
                    }
                }
            }
        }
    }
    items(cards, key = { it.key }) { card ->
        SyncCard(card, onOpen = { actions.onOpenSong(card.songId) }, onKeepNew = { actions.onKeepNew(card.key) }, onKeepMine = { onKeepMine(card.key) }, onRemove = { onRemove(card.key) })
    }
}

@Composable
private fun SyncCard(card: SyncCardUi, onOpen: () -> Unit, onKeepNew: () -> Unit, onKeepMine: () -> Unit, onRemove: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(card.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                Text(
                    stringResource(
                        when (card.kind) {
                            SyncKind.UPDATED -> R.string.sync_kind_updated
                            SyncKind.SECTIONS -> R.string.sync_kind_sections
                            SyncKind.ADDED -> R.string.sync_kind_added
                            SyncKind.PROTECTED -> R.string.sync_kind_protected
                            SyncKind.META -> R.string.sync_kind_meta
                            SyncKind.META_PROTECTED -> R.string.sync_kind_meta_protected
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.diff.isNotEmpty()) DiffLines(card)
            card.meta.forEach { MetaLine(it) }
            when (card.choice) {
                SyncChoice.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (card.kind == SyncKind.ADDED) {
                        TextButton(onClick = onKeepNew) { Text(stringResource(R.string.sync_keep_added)) }
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.sync_remove_added), color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = onKeepNew) { Text(stringResource(R.string.sync_keep_new)) }
                        TextButton(onClick = onKeepMine) { Text(stringResource(R.string.sync_keep_mine)) }
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(
                            when (card.choice) {
                                SyncChoice.KEPT_MINE -> R.string.sync_chose_mine
                                SyncChoice.REMOVED -> R.string.sync_chose_removed
                                else -> R.string.sync_chose_new
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Linhas novas em destaque (+), retiradas riscadas (−), iguais em volta apagadas, trechos omitidos "…". */
@Composable
private fun DiffLines(card: SyncCardUi) {
    Column {
        card.diff.forEach { line ->
            val (prefix, color, decoration) = when (line.kind) {
                LineDiff.Kind.ADDED -> Triple("+ ", MaterialTheme.colorScheme.primary, null)
                LineDiff.Kind.REMOVED -> Triple("− ", MaterialTheme.colorScheme.error, TextDecoration.LineThrough)
                LineDiff.Kind.SAME -> Triple("   ", MaterialTheme.colorScheme.onSurfaceVariant, null)
                LineDiff.Kind.GAP -> Triple("   …", MaterialTheme.colorScheme.onSurfaceVariant, null)
            }
            Text(prefix + line.text, style = MaterialTheme.typography.bodyLarge, color = color, textDecoration = decoration)
        }
        if (card.moreLines > 0) {
            Text(pluralStringResource(R.plurals.sync_more_lines, card.moreLines, card.moreLines), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Artista: antes → depois", com o de antes riscado (como as linhas da letra). */
@Composable
private fun MetaLine(line: MetaLineUi) {
    val empty = stringResource(R.string.sync_meta_empty)
    val label = stringResource(
        when (line.field) {
            MetaField.TITLE -> R.string.sync_meta_title
            MetaField.AUTHOR -> R.string.sync_meta_author
            MetaField.YEAR -> R.string.sync_meta_year
        },
    )
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$label: ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, textDecoration = TextDecoration.LineThrough)) { append(line.from.ifBlank { empty }) }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("  →  ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(line.to.ifBlank { empty }) }
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun lastCheckText(c: LastCheckUi): String = when (c.result) {
    com.juliano.stagebook.data.LastCheck.Result.UP_TO_DATE -> stringResource(R.string.sync_check_up_to_date, c.date)
    com.juliano.stagebook.data.LastCheck.Result.NOTHING_CHANGED -> stringResource(R.string.sync_check_nothing, c.date)
    com.juliano.stagebook.data.LastCheck.Result.CHANGED -> pluralStringResource(R.plurals.sync_check_changed, c.count, c.count, c.date)
}

@Composable
private fun Note(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// exemplo com texto inventado
internal val syncPreviewState = SyncDetailsUiState(
    loading = false,
    date = "21/10/26 16:40",
    lastCheck = LastCheckUi("21/10/26 21:09", com.juliano.stagebook.data.LastCheck.Result.UP_TO_DATE, 0),
    updating = true,
    protectedSongs = listOf(
        SyncCardUi(
            "4:PROTECTED", "4", "Música D", SyncKind.PROTECTED, SyncChoice.PENDING,
            listOf(DiffLineUi(LineDiff.Kind.SAME, "linha igual nas duas"), DiffLineUi(LineDiff.Kind.REMOVED, "linha só na sua versão"), DiffLineUi(LineDiff.Kind.ADDED, "linha só na atualizada")),
        ),
    ),
    changed = listOf(
        SyncCardUi(
            "1:UPDATED", "1", "Música A", SyncKind.UPDATED, SyncChoice.PENDING,
            listOf(
                DiffLineUi(LineDiff.Kind.GAP, ""),
                DiffLineUi(LineDiff.Kind.SAME, "uma linha que ficou igual"),
                DiffLineUi(LineDiff.Kind.REMOVED, "a linha como era antes"),
                DiffLineUi(LineDiff.Kind.ADDED, "a linha como ficou agora"),
                DiffLineUi(LineDiff.Kind.SAME, "outra linha igual"),
                DiffLineUi(LineDiff.Kind.GAP, ""),
            ),
        ),
        SyncCardUi("5:META", "5", "Música E", SyncKind.META, SyncChoice.PENDING, meta = listOf(MetaLineUi(MetaField.AUTHOR, "Artista antigo", "Artista certo"), MetaLineUi(MetaField.YEAR, "", "1999"))),
        SyncCardUi("2:SECTIONS", "2", "Música B", SyncKind.SECTIONS, SyncChoice.KEPT_MINE, listOf(DiffLineUi(LineDiff.Kind.ADDED, "[Verso 1]"), DiffLineUi(LineDiff.Kind.SAME, "primeira linha"), DiffLineUi(LineDiff.Kind.GAP, ""))),
    ),
    added = listOf(SyncCardUi("3:ADDED", "3", "Música C", SyncKind.ADDED, SyncChoice.PENDING)),
)

@LightPreview
@Composable
private fun SyncDetailsLightPreview() = StagebookTheme(darkTheme = false) { SyncDetailsScreen(syncPreviewState, SyncDetailsActions()) }

@DarkPreview
@Composable
private fun SyncDetailsDarkPreview() = StagebookTheme(darkTheme = true) { SyncDetailsScreen(syncPreviewState, SyncDetailsActions()) }
