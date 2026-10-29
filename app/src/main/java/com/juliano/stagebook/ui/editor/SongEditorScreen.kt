package com.juliano.stagebook.ui.editor

import com.juliano.stagebook.domain.LyricsView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
        onTempo = viewModel::setTempo,
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
    look: Look = Look(),
    onTempo: (String) -> Unit = {},
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var previewing by rememberSaveable { mutableStateOf(false) }   // a música como fica no palco
    var newChord by rememberSaveable { mutableStateOf(false) }
    // os dados da música (título, artista, ano, tom, capo) ficam recolhidos numa linha; música nova começa com eles
    // abertos (o título é obrigatório)
    var dataOpen by rememberSaveable(uiState.loading) { mutableStateOf(!uiState.loading && cursor < 0 && uiState.mode == EditorMode.NEW && uiState.title.isEmpty()) }
    // salvar sem título (ou com título repetido): abre os dados, onde está o erro
    LaunchedEffect(uiState.error) { if (uiState.error != null) dataOpen = true }
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
            // tom e capo ficam nos dados da música: o campo da letra não mostra as linhas {key}/{capo} (pedido do usuário,
            // 29/10); ao editar, elas voltam do texto guardado
            val fieldText = remember(uiState.text, look.chords) { Chords.hideMeta(if (look.chords) uiState.text else LyricsView.view(uiState.text)) }
            // o cursor vindo do palco é no texto guardado: desconta as linhas escondidas antes dele
            val fieldCursor = remember { if (cursor < 0) cursor else Chords.hideMeta(uiState.text.take(cursor.coerceAtMost(uiState.text.length))).length }
            var lyrics by remember { mutableStateOf(TextFieldValue(fieldText, TextRange(lineEnd(fieldText, fieldCursor)))) }
            // o texto vem sempre do estado; a seleção e o cursor ficam aqui
            val shown = if (lyrics.text == fieldText) lyrics else lyrics.copy(text = fieldText)
            fun edit(v: TextFieldValue) {
                lyrics = v
                if (v.text != fieldText) onText(Chords.withMeta(if (look.chords) v.text else LyricsView.merge(uiState.text, v.text), uiState.text))
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
                    // dados da música recolhidos numa linha (pedido do usuário, 29/10): tocar abre os campos ali mesmo
                    SongDataHeader(
                        uiState = uiState,
                        expanded = dataOpen,
                        onToggle = { dataOpen = !dataOpen },
                        chords = look.chords,
                    ) {
                        SongDataFields(uiState, onTitle, onAuthor, onYear, onCollapse = { dataOpen = false }, onTempo = onTempo)
                        if (look.chords) KeyCapoRow(uiState.text, onTranspose, onCapo)
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
                            minLines = 20,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 480.dp).focusRequester(focus).onFocusChanged { editingLyrics = it.isFocused },
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
/** Linha fina com o resumo da música (título · artista · tom · capo) e a seta; aberta, mostra os campos embaixo. */
@Composable
private fun SongDataHeader(uiState: SongEditorUiState, expanded: Boolean, onToggle: () -> Unit, chords: Boolean, fields: @Composable () -> Unit) {
    val key = remember(uiState.text) { Chords.key(uiState.text) }
    val capo = remember(uiState.text) { Chords.capo(uiState.text) }
    val summary = listOfNotNull(
        uiState.title.ifBlank { null } ?: stringResource(R.string.editor_data_untitled),
        uiState.author.ifBlank { null },
        key.ifBlank { null }?.takeIf { chords },
        capo.takeIf { chords && it > 0 }?.let { stringResource(R.string.editor_data_capo, it) },
        remember(uiState.text) { Chords.tempo(uiState.text) }.takeIf { it > 0 }?.let { stringResource(R.string.editor_data_tempo, it) },
    ).joinToString(" · ")
    Column(Modifier.padding(horizontal = Spacing.m)) {
        // como um campo de escolha (pedido do usuário, 29/10): a caixa com borda e o rótulo, o resumo dentro e o ▼; tocar
        // abre os campos embaixo. O campo é só de leitura: o toque chega pelo interactionSource
        val taps = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val toggle by rememberUpdatedState(onToggle)
        LaunchedEffect(taps) {
            taps.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) toggle() }
        }
        // aberto, o resumo some (repetiria o título logo embaixo) e o ▲ para recolher fica no campo Título
        if (!expanded) OutlinedTextField(
            value = summary,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.editor_data_label)) },
            isError = uiState.error != null,
            trailingIcon = {
                Icon(
                    if (expanded) AppIcons.DropUp else AppIcons.DropDown,
                    stringResource(if (expanded) R.string.editor_data_close else R.string.editor_data_open),
                )
            },
            interactionSource = taps,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) { fields() }
        }
    }
}

/** Os dados da música: título (obrigatório, é o nome no setlist), artista e ano. */
@Composable
private fun SongDataFields(
    uiState: SongEditorUiState,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onYear: (String) -> Unit,
    onCollapse: () -> Unit,
    onTempo: (String) -> Unit = {},
) {
    OutlinedTextField(
        value = uiState.title,
        onValueChange = onTitle,
        label = { Text(stringResource(R.string.editor_title)) },
        // ▲ recolhe os dados da música (volta o campo com o resumo)
        trailingIcon = { IconButton(onClick = onCollapse) { Icon(AppIcons.DropUp, stringResource(R.string.editor_data_close)) } },
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
        // BPM da música (`{tempo}`): o metrônomo começa nele (sem BPM, em 100)
        val tempo = remember(uiState.text) { Chords.directive(uiState.text, "tempo").orEmpty() }
        OutlinedTextField(
            value = tempo,
            onValueChange = onTempo,
            label = { Text(stringResource(R.string.editor_tempo)) },
            singleLine = true,
            isError = tempo.isNotEmpty() && Chords.tempo(uiState.text) == 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f),
        )
    }
    Text(stringResource(R.string.editor_auto_meta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

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
 * Tom e capotraste da música, os dois do mesmo jeito (pedido do usuário, 29/10): − e + do tom transpõem meio tom
 * (reescrevem os acordes e o tom guardado); − e + do capo mudam a casa (os desenhos dos acordes ficam iguais). Sem tom
 * guardado, mostra o calculado pelos acordes, apagado.
 */
@Composable
private fun KeyCapoRow(text: String, onTranspose: (Int) -> Unit, onCapo: (Int) -> Unit) {
    val hasChords = remember(text) { Chords.hasChords(text) || Chords.fromChordsOverLyrics(text).let(Chords::hasChords) }
    val saved = remember(text) { Chords.key(text) }
    val key = saved.ifEmpty { remember(text) { Chords.detectKey(text) } }
    val capo = remember(text) { Chords.capo(text) }
    Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // ícone e nome à vista (pedido do usuário, 29/10)
        Icon(AppIcons.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.editor_key), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
        IconButton(onClick = { onTranspose(-1) }, enabled = hasChords) { Icon(AppIcons.Remove, stringResource(R.string.editor_transpose_down)) }
        Text(
            key.ifEmpty { "–" },
            style = MaterialTheme.typography.titleMedium,
            color = if (saved.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onTranspose(1) }, enabled = hasChords) { Icon(AppIcons.Add, stringResource(R.string.editor_transpose_up)) }
        Spacer(Modifier.weight(1f))
        Icon(AppIcons.Capo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.editor_capo), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
        IconButton(onClick = { onCapo(capo - 1) }, enabled = capo > 0) { Icon(AppIcons.Remove, stringResource(R.string.editor_capo_down)) }
        // sem capo: 0 apagado (um "–" parecia um terceiro botão de menos)
        Text(capo.toString(), style = MaterialTheme.typography.titleMedium, color = if (capo == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = { onCapo(capo + 1) }, enabled = capo < 12) { Icon(AppIcons.Add, stringResource(R.string.editor_capo_up)) }
    }
    // com capo: os desenhos continuam os da cifra, mas o som sobe (pedido do usuário, 29/10)
    if (capo > 0) {
        val validKey = key.isNotEmpty() && Chords.isChord(key)
        Text(
            if (validKey) stringResource(R.string.editor_capo_sounds, key, capo, Chords.soundingKey(key, capo))
            else stringResource(R.string.editor_capo_sounds_nokey, capo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    }
}

/** A música como fica no palco, com a aparência escolhida nos ajustes (só para conferir). */
@Composable
private fun EditorPreview(text: String, look: Look) {
    val names = rememberSectionNames(look.translateSections)
    val chordLook = ChordLook(look.chords, look.chordScale, Color(look.chordColor))
    val lines = remember(text, look, names) { stageLines(Chords.fromChordsOverLyrics(text), names, Color(look.sectionColor), look.showSections, chordLook, look.sectionScale).map { it.text } }
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
