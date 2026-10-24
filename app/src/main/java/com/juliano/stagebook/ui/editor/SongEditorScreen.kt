package com.juliano.stagebook.ui.editor

import com.juliano.stagebook.domain.LyricsView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import com.juliano.stagebook.ui.stage.family
import com.juliano.stagebook.ui.stage.rememberSectionNames
import com.juliano.stagebook.ui.stage.stageLines
import com.juliano.stagebook.ui.stage.joinLines
import com.juliano.stagebook.ui.stage.ChordText
import com.juliano.stagebook.ui.stage.ChordLook
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.domain.Chords
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
fun SongEditorRoute(
    onDone: () -> Unit,
    viewModel: SongEditorViewModel = hiltViewModel(),
    onNextImport: () -> Unit = onDone,
    onSetlistReady: (String) -> Unit = { onDone() },
) {
    LaunchedEffect(viewModel) {
        viewModel.onDone.collect { e ->
            when (e) {
                EditorExit.Close -> onDone()
                EditorExit.NextImport -> onNextImport()
                is EditorExit.SetlistReady -> onSetlistReady(e.setId)
            }
        }
    }
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
        onTranspose = viewModel::transpose,
        onCapo = viewModel::setCapo,
        onKey = viewModel::setKey,
        look = viewModel.look.collectAsStateWithLifecycle().value,
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
    onTranspose: (Int) -> Unit = {},
    onCapo: (Int) -> Unit = {},
    onKey: (String) -> Unit = {},
    look: Look = Look(),
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var previewing by rememberSaveable { mutableStateOf(false) }   // a música como fica no palco
    var newChord by rememberSaveable { mutableStateOf(false) }
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
            // perfil só letra: o campo mostra a letra sem a cifra, e cada mudança volta para a música com a cifra
            // no lugar (LyricsView): quem só canta não apaga os acordes de ninguém
            val fieldText = remember(uiState.text, look.chords) { if (look.chords) uiState.text else LyricsView.view(uiState.text) }
            var lyrics by remember { mutableStateOf(TextFieldValue(fieldText, TextRange(lineEnd(fieldText, cursor)))) }
            // o texto vem sempre do estado; a seleção e o cursor ficam aqui
            val shown = if (lyrics.text == fieldText) lyrics else lyrics.copy(text = fieldText)
            fun edit(v: TextFieldValue) {
                lyrics = v
                if (v.text != fieldText) onText(if (look.chords) v.text else LyricsView.merge(uiState.text, v.text))
            }
            var editingLyrics by remember { mutableStateOf(false) }
            // acorde novo: entra onde estava o cursor da letra
            if (newChord) {
                ChordDialog(
                    onDone = { chord -> newChord = false; edit(insertChord(shown, chord)) },
                    onDismiss = { newChord = false },
                )
            }
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
                        // prévia do palco (o olho aceso = mostrando a prévia; tocar de novo volta ao texto)
                        IconButton(onClick = { previewing = !previewing }) {
                            Icon(
                                if (previewing) AppIcons.Edit else AppIcons.Visibility,
                                stringResource(if (previewing) R.string.editor_back_to_text else R.string.editor_preview),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // salvar à vista; excluir (música existente) no ⋮, longe do salvar (padrão do Google)
                        IconButton(onClick = onSave) {
                            Icon(AppIcons.Save, stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (uiState.canDelete) {
                            OverflowMenu(listOf(MenuAction(stringResource(R.string.editor_delete), AppIcons.Delete, { deleting = true }, danger = true)))
                        }
                    }
                    // atalhos das seções no topo enquanto edita a letra: continuam à vista com o teclado
                    AnimatedVisibility(editingLyrics && !previewing) {
                        Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            SectionBar(onPick = { kind ->
                                edit(insertSection(shown, kind))
                            })
                            // perfil só letra: nada de cifra no editor (a letra guardada não muda)
                            if (look.chords) {
                                ChordBar(
                                    chords = remember(uiState.text) { usedChords(uiState.text) },
                                    onPick = { chord -> edit(insertChord(shown, chord)) },
                                    onNew = { newChord = true },
                                )
                            }
                        }
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
                        if (look.chords) KeyCapoRow(uiState.text, onTranspose, onCapo, onKey)
                        if (previewing) {
                            EditorPreview(uiState.text, look)
                            return@Column
                        }
                        val focus = remember { FocusRequester() }
                        if (cursor >= 0) LaunchedEffect(Unit) { focus.requestFocus() }
                        OutlinedTextField(
                            value = shown,
                            onValueChange = ::edit,
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
        Icon(AppIcons.ListBulleted, stringResource(R.string.editor_sections), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        val names = rememberSectionNames(true)
        SECTION_SHORTCUTS.forEach { kind ->
            SuggestionChip(onClick = { onPick(kind) }, label = { Text(names.label(Sections.Header(kind, "", "", kind.pt)), style = MaterialTheme.typography.titleSmall) })
        }
    }
}

/** Atalhos de acorde: os que a música já usa e o "+" para um novo; cada toque põe `[acorde]` no cursor. */
@Composable
private fun ChordBar(chords: List<String>, onPick: (String) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.MusicNote, stringResource(R.string.editor_chords), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        chords.forEach { c -> SuggestionChip(onClick = { onPick(c) }, label = { Text(c, style = MaterialTheme.typography.titleSmall) }) }
        SuggestionChip(onClick = onNew, icon = { Icon(AppIcons.Add, null) }, label = { Text(stringResource(R.string.editor_chord_new), style = MaterialTheme.typography.titleSmall) })
    }
}

/**
 * Tom e capotraste da música, com ícones: ▼▲ transpõem meio tom (reescrevem os acordes e o tom guardado);
 * − e + mudam a casa do capo (os desenhos dos acordes ficam iguais). O tom também pode ser escrito.
 */
@Composable
private fun KeyCapoRow(text: String, onTranspose: (Int) -> Unit, onCapo: (Int) -> Unit, onKey: (String) -> Unit) {
    val hasChords = remember(text) { Chords.hasChords(text) || Chords.fromChordsOverLyrics(text).let(Chords::hasChords) }
    val key = remember(text) { Chords.key(text) }
    val capo = remember(text) { Chords.capo(text) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = key,
            onValueChange = { v -> onKey(v.take(6)) },
            label = { Text(stringResource(R.string.editor_key)) },
            singleLine = true,
            isError = key.isNotEmpty() && !Chords.isChord(key),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.width(96.dp),
        )
        IconButton(onClick = { onTranspose(-1) }, enabled = hasChords) { Icon(AppIcons.ArrowDown, stringResource(R.string.editor_transpose_down)) }
        IconButton(onClick = { onTranspose(1) }, enabled = hasChords) { Icon(AppIcons.ArrowUp, stringResource(R.string.editor_transpose_up)) }
        Spacer(Modifier.weight(1f))
        Icon(AppIcons.Capo, stringResource(R.string.editor_capo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onCapo(capo - 1) }, enabled = capo > 0) { Icon(AppIcons.Remove, stringResource(R.string.editor_capo_down)) }
        // sem capo: 0 apagado (um "–" parecia um terceiro botão de menos)
        Text(capo.toString(), style = MaterialTheme.typography.titleMedium, color = if (capo == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = { onCapo(capo + 1) }, enabled = capo < 12) { Icon(AppIcons.Add, stringResource(R.string.editor_capo_up)) }
    }
}

/** A música como fica no palco, com a aparência escolhida nos ajustes (só para conferir). */
@Composable
private fun EditorPreview(text: String, look: Look) {
    val names = rememberSectionNames(look.translateSections)
    val chordLook = ChordLook(look.chords, look.chordScale, Color(look.chordColor))
    val lines = remember(text, look, names) { stageLines(Chords.fromChordsOverLyrics(text), names, Color(look.sectionColor), look.showSections, chordLook).map { it.text } }
    val font = minOf(look.fontSp, 28f)
    val style = TextStyle(
        color = Color(look.fg),
        fontSize = font.sp,
        lineHeight = (font * 1.25f).sp,
        fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = look.font.family,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    Surface(color = Color(look.bg), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        ChordText(
            joinLines(lines),
            style = style,
            chordStyle = style.copy(color = Color(look.chordColor), fontSize = (font * look.chordScale).sp, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified),
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

/** Escrever um acorde novo; só aceita o que tem forma de acorde. */
@Composable
private fun ChordDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    val ok = Chords.isChord(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_chord_new)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.trim().take(12) },
                singleLine = true,
                isError = value.isNotEmpty() && !ok,
                supportingText = { Text(stringResource(R.string.editor_chord_hint)) },
                shape = MaterialTheme.shapes.small,
            )
        },
        confirmButton = { TextButton(onClick = { onDone(value) }, enabled = ok) { Text(stringResource(R.string.editor_chord_insert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
