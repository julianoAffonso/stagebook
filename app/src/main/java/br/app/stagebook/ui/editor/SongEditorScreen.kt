package br.app.stagebook.ui.editor

import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.data.TrackStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import br.app.stagebook.domain.LyricsView
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
import br.app.stagebook.ui.stage.family
import br.app.stagebook.ui.stage.rememberSectionNames
import br.app.stagebook.ui.stage.stageLines
import br.app.stagebook.ui.stage.joinLines
import br.app.stagebook.ui.stage.ChordText
import br.app.stagebook.ui.stage.ChordLook
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.Chords
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
import br.app.stagebook.R
import br.app.stagebook.domain.Sections
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.KeyCapoControls
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import br.app.stagebook.ui.components.CenteredContent
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing

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
    val look = viewModel.look.collectAsStateWithLifecycle().value
    val message = LocalAppMessage.current
    val trackBad = stringResource(R.string.stage_track_bad)
    val scope = rememberCoroutineScope()
    // the backing track (OGG, MP3 or WAV) is copied into the app when coming back from the Android picker
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { if (!viewModel.attachTrack(uri)) message.show(trackBad, long = true) }
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
        look = look,
        track = viewModel.track.takeIf { look.playerMode },
        onPickTrack = { picker.launch(TrackStore.MIME) },
        onRemoveTrack = viewModel::removeTrack,
        allTags = remember { viewModel.allTags() },
        onTags = viewModel::onTags,
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
    track: String? = null,   // the song's backing track file ("" = none); null = can't pick here (new song or player off)
    onPickTrack: () -> Unit = {},
    onRemoveTrack: () -> Unit = {},
    allTags: List<String> = emptyList(),
    onTags: (add: List<String>, remove: List<String>) -> Unit = { _, _ -> },
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var tagging by rememberSaveable { mutableStateOf(false) }
    var previewing by rememberSaveable { mutableStateOf(false) }   // the song as it looks on stage
    var newChord by rememberSaveable { mutableStateOf(false) }
    // the song details (title, artist, year, key, capo) stay collapsed on one line; a new song starts with them open
    // (the title is required)
    var dataOpen by rememberSaveable(uiState.loading) { mutableStateOf(!uiState.loading && cursor < 0 && uiState.mode == EditorMode.NEW && uiState.title.isEmpty()) }
    // saving without a title (or with a repeated one): opens the details, where the error is
    LaunchedEffect(uiState.error) { if (uiState.error != null) dataOpen = true }
    // Cancel sits right next to Save and the system back is easy to hit by accident
    var discarding by rememberSaveable { mutableStateOf(false) }
    // the same function on every key: the Save/Cancel buttons don't redraw while typing
    val unsaved by rememberUpdatedState(uiState.unsaved)
    val cancel = remember(onCancel) { { if (unsaved) discarding = true else onCancel() } }
    BackHandler(enabled = !uiState.loading && uiState.unsaved) { discarding = true }
    Scaffold(contentWindowInsets = ScreenInsets, modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { inner ->
        Crossfade(uiState.loading, Modifier.padding(inner).imePadding(), label = "editor") { loading ->
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Crossfade
            }
            // opened from the stage: cursor at the end of the held line, keyboard already up. Lyrics only profile: the
            // field shows the lyrics without chords, and each change goes back into the song with the chords in place
            // (LyricsView): someone who only sings doesn't delete anyone's chords. Key and capo live in the song
            // details: the lyrics field doesn't show the {key}/{capo} lines (user's request, 29/10); on edit they come
            // back from the saved text
            val fieldText = remember(uiState.text, look.chords) { Chords.hideMeta(if (look.chords) uiState.text else LyricsView.view(uiState.text)) }
            // the cursor coming from the stage is in the saved text: subtract the hidden lines before it
            val fieldCursor = remember { if (cursor < 0) cursor else Chords.hideMeta(uiState.text.take(cursor.coerceAtMost(uiState.text.length))).length }
            var lyrics by remember { mutableStateOf(TextFieldValue(fieldText, TextRange(lineEnd(fieldText, fieldCursor)))) }
            // the text always comes from the state; the selection and cursor live here
            val shown = if (lyrics.text == fieldText) lyrics else lyrics.copy(text = fieldText)
            fun edit(v: TextFieldValue) {
                lyrics = v
                if (v.text != fieldText) onText(Chords.withMeta(if (look.chords) v.text else LyricsView.merge(uiState.text, v.text), uiState.text))
            }
            var editingLyrics by remember { mutableStateOf(false) }
            // new chord: goes where the lyrics cursor was
            if (newChord) {
                ChordDialog(
                    onDone = { chord -> newChord = false; edit(insertChord(shown, chord)) },
                    onDismiss = { newChord = false },
                )
            }
            CenteredContent(Modifier.fillMaxSize()) {
                Column {
                    // fixed header with Save: at hand anywhere in the lyrics (back is cancel)
                    ScreenHeader(
                        // the title is short (it was cut on phones); the import note goes in the subtitle
                        title = stringResource(if (uiState.mode == EditorMode.EDIT || uiState.mode == EditorMode.IMPORTED_UPDATE) R.string.editor_edit else R.string.editor_new),
                        subtitle = when (uiState.mode) {
                            EditorMode.IMPORTED_NEW -> stringResource(R.string.editor_imported_new)
                            EditorMode.IMPORTED_UPDATE -> stringResource(R.string.editor_imported_update)
                            else -> ""
                        },
                        onBack = cancel,
                        // 16dp below: the details field's label sits on its top border and reached up into the subtitle
                        modifier = Modifier.padding(start = Spacing.s, end = Spacing.m, top = Spacing.s, bottom = Spacing.m),
                    ) {
                        // stage preview (the "present" icon; while previewing it becomes the pencil, back to the text)
                        IconButton(onClick = { previewing = !previewing }) {
                            Icon(
                                if (previewing) AppIcons.Edit else AppIcons.Slideshow,
                                stringResource(if (previewing) R.string.editor_back_to_text else R.string.editor_preview),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // save in view; delete (existing song) in the ⋮, away from save (Google's pattern)
                        IconButton(onClick = onSave) {
                            Icon(AppIcons.Save, stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (uiState.canDelete) {
                            OverflowMenu(listOf(MenuAction(stringResource(R.string.editor_delete), AppIcons.Delete, { deleting = true }, danger = true)))
                        }
                    }
                    // song details collapsed on one line (user's request, 29/10): tapping opens the fields right there
                    SongDataHeader(
                        uiState = uiState,
                        expanded = dataOpen,
                        onToggle = { dataOpen = !dataOpen },
                        chords = look.chords,
                    ) {
                        SongDataFields(uiState, onTitle, onAuthor, onYear, onCollapse = { dataOpen = false }, onTempo = onTempo)
                        // in the details, for new songs too (user, 30/10); only with the player on
                        if (track != null) TrackRow(track.isNotEmpty(), onPickTrack, onRemoveTrack)
                        TagsRow(uiState.tags) { tagging = true }
                        if (look.chords) KeyCapoControls(uiState.text, onTranspose, onCapo, showCapo = look.instrument != br.app.stagebook.data.model.Instrument.KEYBOARD)
                    }
                    // section shortcuts at the top while editing the lyrics: they stay in view with the keyboard up
                    AnimatedVisibility(editingLyrics && !previewing) {
                        Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            SectionBar(
                                onPick = { kind -> edit(insertSection(shown, kind)) },
                                onHighlight = { edit(toggleHighlight(shown)) },
                            )
                            // lyrics only profile: no chords in the editor (the saved lyrics don't change)
                            if (look.chords) {
                                ChordBar(
                                    chords = remember(uiState.text) { usedChords(uiState.text) },
                                    onPick = { chord -> edit(insertChord(shown, chord)) },
                                    onNew = { newChord = true },
                                )
                            }
                        }
                    }
                    // the lyrics take all the room left under the details (bug 31/10: they stopped short of the bottom);
                    // only the preview scrolls, the text field is sized by the space instead
                    Column(
                        Modifier
                            .weight(1f)
                            .then(if (previewing) Modifier.verticalScroll(rememberScrollState()) else Modifier)
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
                            modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus).onFocusChanged { editingLyrics = it.isFocused },
                        )
                    }
                }
            }
        }
    }
    if (tagging) {
        val national = stringResource(R.string.tags_national)
        val international = stringResource(R.string.tags_international)
        val options = (listOf(national, international) + allTags + uiState.tags).distinctBy { it.lowercase() }
        val initial = options.associateWith { t ->
            if (uiState.tags.any { it.equals(t, true) }) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
        }
        br.app.stagebook.ui.components.TagsDialog(options, initial, onTags) { tagging = false }
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

/** The song's tags in the details, and the button that opens the tags window. */
@Composable
private fun TagsRow(tags: List<String>, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            if (tags.isEmpty()) stringResource(R.string.tags_none) else stringResource(R.string.tags_list, tags.joinToString(", ")),
            style = MaterialTheme.typography.bodyLarge,
            color = if (tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onEdit) { Text(stringResource(R.string.tags_edit)) }
    }
}

/** End of the line at position `at` (outside the text = start). */
internal fun lineEnd(text: String, at: Int): Int {
    if (at !in 0..text.length) return 0
    return text.indexOf('\n', at).let { if (it < 0) text.length else it }
}

/**
 * Section shortcuts: each tap puts the section title in the lyrics at the cursor. Loose chips, aligned with the fields
 * (Material 3 Expressive, no background strip).
 */
/** Thin line with the song summary (title · artist · key · capo) and the arrow; when open, shows the fields below. */
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
        // like a choice field (user's request, 29/10): the outlined box and label, the summary inside and the ▼;
        // tapping opens the fields below. The field is read only: the tap arrives through the interactionSource
        val taps = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val toggle by rememberUpdatedState(onToggle)
        LaunchedEffect(taps) {
            taps.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) toggle() }
        }
        // when open, the summary goes away (it would repeat the title right below) and the ▲ to collapse sits in the
        // Title field
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

/** The song details: title (required, it's the name in the setlist), artist and year. */
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
        // ▲ collapses the song details (brings back the summary field)
        trailingIcon = { IconButton(onClick = onCollapse) { Icon(AppIcons.DropUp, stringResource(R.string.editor_data_close)) } },
        isError = uiState.error != null,
        supportingText = uiState.error?.let { e ->
            {
                Text(
                    stringResource(
                        when (e) {
                            EditorError.TITLE_EMPTY -> R.string.editor_error_title_empty
                            EditorError.TITLE_TAKEN -> R.string.editor_error_title_taken
                            EditorError.NOT_SONG -> R.string.not_song
                        },
                    ),
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
        // song BPM (`{tempo}`): the metronome starts at it (no BPM, at 100)
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

/** Backing track of the song (T29, user's decision): picked here, so the stage shows the play button only on songs with one. */
@Composable
private fun TrackRow(has: Boolean, onPick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(AppIcons.QueueMusic, null, tint = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.stage_backing_track), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (has) R.string.editor_track_set else R.string.editor_track_none),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (has) IconButton(onClick = onRemove) { Icon(AppIcons.Delete, stringResource(R.string.stage_track_remove)) }
        ActionButton(stringResource(if (has) R.string.editor_track_change else R.string.editor_track_pick), onPick, tone = ButtonTone.Tonal)
    }
}

@Composable
private fun SectionBar(onPick: (Sections.Kind) -> Unit, onHighlight: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // highlight the selected phrase (or the word at the cursor) on stage; tapping inside a highlight takes it off
        SuggestionChip(onClick = onHighlight, label = { Text(stringResource(R.string.editor_highlight), style = MaterialTheme.typography.titleSmall) })
        // a short label says what the row is (an icon alone said nothing, user 30/10)
        Text(stringResource(R.string.editor_sections), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val names = rememberSectionNames(true)
        SECTION_SHORTCUTS.forEach { kind ->
            SuggestionChip(onClick = { onPick(kind) }, label = { Text(names.label(Sections.Header(kind, "", "", kind.pt)), style = MaterialTheme.typography.titleSmall) })
        }
    }
}

/** Chord shortcuts: the ones the song already uses and "+" for a new one; each tap puts `[chord]` at the cursor. */
@Composable
private fun ChordBar(chords: List<String>, onPick: (String) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.editor_chord_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        chords.forEach { c -> SuggestionChip(onClick = { onPick(c) }, label = { Text(c, style = MaterialTheme.typography.titleSmall) }) }
        SuggestionChip(onClick = onNew, icon = { Icon(AppIcons.Add, null) }, label = { Text(stringResource(R.string.editor_chord_new), style = MaterialTheme.typography.titleSmall) })
    }
}

/** The song as it looks on stage, with the look chosen in settings (just to check). */
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

/** Type a new chord; only accepts what looks like a chord. */
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
