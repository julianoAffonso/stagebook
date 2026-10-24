package com.juliano.stagebook.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.juliano.stagebook.R
import com.juliano.stagebook.domain.Sections
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.MenuAction
import com.juliano.stagebook.ui.components.OverflowMenu
import com.juliano.stagebook.ui.components.CenteredContent
import com.juliano.stagebook.ui.components.ConfirmDialog
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.ScreenHeader
import com.juliano.stagebook.ui.components.ScreenInsets
import com.juliano.stagebook.ui.theme.StagebookTheme
import com.juliano.stagebook.ui.theme.Spacing

@Composable
fun SongEditorRoute(onDone: () -> Unit, viewModel: SongEditorViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.onDone.collect { onDone() } }
    SongEditorScreen(
        uiState = viewModel.uiState,
        onTitle = viewModel::onTitle,
        onAuthor = viewModel::onAuthor,
        onYear = viewModel::onYear,
        onText = viewModel::onText,
        onSave = viewModel::save,
        onCancel = onDone,
        onDelete = viewModel::delete,
        cursor = viewModel.cursor,
    )
}

@Composable
fun SongEditorScreen(
    uiState: SongEditorUiState,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onYear: (String) -> Unit,
    onText: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    cursor: Int = -1,
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    // Cancelar fica colado no Salvar e o voltar do sistema é fácil de tocar sem querer
    var discarding by rememberSaveable { mutableStateOf(false) }
    // a mesma função a cada tecla: os botões Salvar/Cancelar não se redesenham enquanto digita
    val unsaved by rememberUpdatedState(uiState.unsaved)
    val cancel = remember(onCancel) { { if (unsaved) discarding = true else onCancel() } }
    BackHandler(enabled = !uiState.loading && uiState.unsaved) { discarding = true }
    Scaffold(contentWindowInsets = ScreenInsets, modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { inner ->
        Crossfade(uiState.loading, Modifier.padding(inner).imePadding(), label = "editor") { loading ->
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Crossfade
            }
            // aberta do palco: cursor no fim da linha segurada, já com o teclado
            var lyrics by remember { mutableStateOf(TextFieldValue(uiState.text, TextRange(lineEnd(uiState.text, cursor)))) }
            // o texto vem sempre do estado; a seleção e o cursor ficam aqui
            val shown = if (lyrics.text == uiState.text) lyrics else lyrics.copy(text = uiState.text)
            var editingLyrics by remember { mutableStateOf(false) }
            CenteredContent(Modifier.fillMaxSize()) {
                Column {
                    // cabeçalho fixo com o Salvar: à mão em qualquer ponto da letra (o voltar é o cancelar)
                    ScreenHeader(
                        title = stringResource(
                            when (uiState.mode) {
                                EditorMode.NEW -> R.string.editor_new
                                EditorMode.EDIT -> R.string.editor_edit
                                EditorMode.IMPORTED_NEW -> R.string.editor_imported_new
                                EditorMode.IMPORTED_UPDATE -> R.string.editor_imported_update
                            },
                        ),
                        onBack = cancel,
                        modifier = Modifier.padding(start = Spacing.s, end = Spacing.m, top = Spacing.s),
                    ) {
                        // salvar à vista; excluir (música existente) no ⋮, longe do salvar (padrão do Google)
                        IconButton(onClick = onSave) {
                            Icon(AppIcons.Save, stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (uiState.canDelete) {
                            OverflowMenu(listOf(MenuAction(stringResource(R.string.editor_delete), AppIcons.Delete, { deleting = true }, danger = true)))
                        }
                    }
                    // atalhos das seções no topo enquanto edita a letra: continuam à vista com o teclado
                    AnimatedVisibility(editingLyrics) {
                        SectionBar(onPick = { kind ->
                            lyrics = insertSection(shown, kind)
                            onText(lyrics.text)
                        })
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.m, vertical = Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = onTitle,
                            label = { Text(stringResource(R.string.editor_title)) },
                            isError = uiState.error != null,
                            supportingText = uiState.error?.let { e ->
                                { Text(stringResource(if (e == EditorError.TITLE_EMPTY) R.string.editor_error_title_empty else R.string.editor_error_title_taken)) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            OutlinedTextField(
                                value = uiState.author,
                                onValueChange = onAuthor,
                                label = { Text(stringResource(R.string.editor_author)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(3f),
                            )
                            OutlinedTextField(
                                value = uiState.year,
                                onValueChange = onYear,
                                label = { Text(stringResource(R.string.editor_year)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(stringResource(R.string.editor_auto_meta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val focus = remember { FocusRequester() }
                        if (cursor >= 0) LaunchedEffect(Unit) { focus.requestFocus() }
                        OutlinedTextField(
                            value = shown,
                            onValueChange = { lyrics = it; if (it.text != uiState.text) onText(it.text) },
                            label = { Text(stringResource(R.string.editor_lyrics)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = MaterialTheme.shapes.small,
                            minLines = 14,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).focusRequester(focus).onFocusChanged { editingLyrics = it.isFocused },
                        )
                    }
                }
            }
        }
    }
    if (discarding) {
        ConfirmDialog(
            stringResource(R.string.editor_discard_confirm),
            stringResource(R.string.editor_discard),
            { discarding = false; onCancel() },
            { discarding = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            stringResource(R.string.songs_delete_confirm, uiState.title),
            stringResource(R.string.action_delete),
            onDelete,
            { deleting = false },
        )
    }
}

/** Fim da linha onde está a posição `at` (fora do texto = início). */
internal fun lineEnd(text: String, at: Int): Int {
    if (at !in 0..text.length) return 0
    return text.indexOf('\n', at).let { if (it < 0) text.length else it }
}

/**
 * Atalhos das seções: cada toque põe o título da seção na letra, onde está o cursor. Chips soltos,
 * alinhados aos campos (Material 3 Expressive, sem a tarja de fundo).
 */
@Composable
private fun SectionBar(onPick: (Sections.Kind) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.editor_sections), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SECTION_SHORTCUTS.forEach { kind ->
            SuggestionChip(onClick = { onPick(kind) }, label = { Text(kind.pt, style = MaterialTheme.typography.titleSmall) })
        }
    }
}

private val previewState = SongEditorUiState(
    loading = false,
    mode = EditorMode.IMPORTED_NEW,
    title = "Hazard",
    author = "Richard Marx",
    year = "1991",
    text = "[Verso 1]\nPrimeira linha da letra\nSegunda linha\n\n[Refrão]\nLinha do refrão",
)

@LightPreview
@Composable
private fun SongEditorLightPreview() = StagebookTheme(darkTheme = false) { SongEditorScreen(previewState, {}, {}, {}, {}, {}, {}, {}) }

@DarkPreview
@Composable
private fun SongEditorDarkPreview() = StagebookTheme(darkTheme = true) { SongEditorScreen(previewState, {}, {}, {}, {}, {}, {}, {}) }
