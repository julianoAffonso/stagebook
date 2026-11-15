package br.app.stagebook.ui.editor

import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.LocalAppMessage
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.text
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.components.Choice
import br.app.stagebook.ui.components.SegmentedChoice
import br.app.stagebook.ui.components.QuestionDialog
import br.app.stagebook.ui.components.TipIcon
import androidx.compose.runtime.mutableIntStateOf
import br.app.stagebook.ui.components.ScreenInsets
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Spacing
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.components.wideLists

@Composable
fun SongEditorRoute(
    onDone: () -> Unit,
    viewModel: SongEditorViewModel = hiltViewModel(),
    onNextImport: () -> Unit = onDone,
    onSetlistReady: (String) -> Unit = { onDone() },
    onSaved: (String) -> Unit = { onDone() },
    onMoved: (String) -> Unit = {},
) {
    LaunchedEffect(viewModel) {
        viewModel.onDone.collect { e ->
            when (e) {
                EditorExit.Close -> onDone()
                is EditorExit.Saved -> onSaved(e.songId)
                EditorExit.NextImport -> onNextImport()
                is EditorExit.SetlistReady -> onSetlistReady(e.setId)
                is EditorExit.Moved -> onMoved(e.songId)
            }
        }
    }
    val look = viewModel.look.collectAsStateWithLifecycle().value
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasMidi = remember(context) { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MIDI) }
    val message = LocalAppMessage.current
    val trackBad = stringResource(R.string.stage_track_bad)
    val scope = rememberCoroutineScope()
    // the backing track (OGG, MP3 or WAV) is copied into the app when coming back from the Android picker
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { if (!viewModel.attachTrack(uri)) message.show(trackBad, long = true) }
    }
    // the song's PDF (copied into the app and checked when coming back from the picker; a refused one says why)
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            (viewModel.attachPdf(uri) as? br.app.stagebook.data.PdfAttach.Refused)?.let { message.show(it.problem.text(context), long = true) }
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
        look = look,
        track = viewModel.track.takeIf { look.playerMode },
        trackName = viewModel.trackName,
        onPickTrack = { picker.launch(TrackStore.MIME) },
        onRemoveTrack = viewModel::removeTrack,
        pdf = viewModel.pdfInfo,
        onPdfInvert = viewModel::setPdfInvert,
        onPickPdf = { pdfPicker.launch(br.app.stagebook.data.PdfStore.MIME) },
        onRemovePdf = viewModel::removePdf,
        allTags = remember { viewModel.allTags() },
        onTags = viewModel::onTags,
        midiOn = look.extrasOn && hasMidi,
        onMidi = viewModel::onMidi,
        position = viewModel.position.collectAsStateWithLifecycle().value,
        onGo = viewModel::go,
        onSaveGo = viewModel::saveAndGo,
    )
}

/** Windows lower than this (dp) are "compact": the two screens' selector goes up to the title's bar to leave the height to the field. */
const val COMPACT_HEIGHT_DP = 480

/** Just after the editor opens from the stage the field ignores touches and selections that did not come from the person. */
const val OPEN_GUARD_MS = 400L

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
    trackName: String = "",   // the track's own name ("" = unknown)
    pdf: EditorPdf? = null,   // the song is a PDF: a card takes the place of the lyrics field; null = a song of text
    onPickPdf: () -> Unit = {},
    onRemovePdf: () -> Unit = {},
    onPdfInvert: (Boolean) -> Unit = {},
    allTags: List<String> = emptyList(),
    onTags: (add: List<String>, remove: List<String>) -> Unit = { _, _ -> },
    midiOn: Boolean = false,   // the song's MIDI: a stage extra (essential mode hides it, T19) on a device with MIDI
    onMidi: (br.app.stagebook.domain.SongMidi) -> Unit = {},
    position: EditorPosition? = null,   // where the song is in the list the editor came from; null = no previous/next
    onGo: (step: Int) -> Unit = {},   // previous (−1) or next (+1) without saving
    onSaveGo: (step: Int) -> Unit = {},   // save this song, then previous or next
) {
    var midiOpen by rememberSaveable { mutableStateOf(false) }
    if (midiOpen) MidiDialog(uiState.midi, { onMidi(it); midiOpen = false }) { midiOpen = false }
    var deleting by rememberSaveable { mutableStateOf(false) }
    // an import over a song already in the library: saving replaces its text, so it asks first
    var replacing by rememberSaveable { mutableStateOf(false) }
    var tagging by rememberSaveable { mutableStateOf(false) }
    // a song that already has lyrics asks before taking a PDF (the lyrics stay saved), and removing the PDF asks too
    var askingPdf by rememberSaveable { mutableStateOf(false) }
    var removingPdf by rememberSaveable { mutableStateOf(false) }
    val hasLyrics = remember(uiState.text) { Chords.hideMeta(uiState.text).isNotBlank() }
    val attachPdf = { if (pdf == null && hasLyrics) askingPdf = true else onPickPdf() }
    var previewing by rememberSaveable { mutableStateOf(false) }   // the song as it looks on stage
    var newChord by rememberSaveable { mutableStateOf(false) }
    // two screens in the same editor: the details and the lyrics. A song that exists opens on the lyrics, a new one on the
    // details (the name is required)
    var tab by rememberSaveable(uiState.loading) { mutableStateOf(if (uiState.canDelete) EditorTab.LYRICS else EditorTab.DATA) }
    // saving without a title (or with a repeated one): shows the details, where the error is
    LaunchedEffect(uiState.error) { if (uiState.error != null) tab = EditorTab.DATA }
    // Cancel sits right next to Save and the system back is easy to hit by accident
    var discarding by rememberSaveable { mutableStateOf(false) }
    // previous/next with something unsaved: asks (−1 or +1 while the question is open, 0 = no question)
    var moving by rememberSaveable { mutableIntStateOf(0) }
    // the same function on every key: the Save/Cancel buttons don't redraw while typing
    val unsaved by rememberUpdatedState(uiState.unsaved)
    val step = remember(onGo) { { by: Int -> if (unsaved) moving = by else onGo(by) } }
    // "Go" on the band's strip leaves the editor like Cancel does (asking first when something wasn't saved) and then opens
    // the song the band is on; with the text unsaved it used to open over it, without asking
    var afterLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val leave = remember(onCancel) { { onCancel(); afterLeave?.invoke(); afterLeave = null } }
    val cancel = remember(leave) { { if (unsaved) discarding = true else leave() } }
    val gate = br.app.stagebook.ui.components.LocalFollowGate.current
    androidx.compose.runtime.DisposableEffect(gate, cancel) {
        val guard: (() -> Unit) -> Unit = { go -> afterLeave = go; cancel() }
        gate.leaveGuard = guard
        onDispose { if (gate.leaveGuard === guard) gate.leaveGuard = null }
    }
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
            // (lyrics only profile: the chord lines and the chords inside the lines are not in the field either, so the same
            // view is applied to the text before the cursor; otherwise the cursor fell past the end and went to the top)
            val fieldCursor = remember {
                if (cursor < 0) cursor
                else uiState.text.take(cursor.coerceAtMost(uiState.text.length)).let { Chords.hideMeta(if (look.chords) it else LyricsView.view(it)).length }
            }
            // saveable: the Activity is recreated by the dark theme, the language, the font size or a killed process, and the
            // selection came back to the stage's line (or the top)
            var lyrics by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(fieldText, TextRange(lineEnd(fieldText, fieldCursor)))) }
            // the text always comes from the state; the selection and cursor live here (so they survive the other screen)
            val shown = if (lyrics.text == fieldText) lyrics else lyrics.copy(text = fieldText)
            val appMessage = br.app.stagebook.ui.components.LocalAppMessage.current
            val highlightHint = stringResource(R.string.editor_highlight_hint)
            // opened from the stage (cursor >= 0): for a moment the route's cursor wins over a touch that came through from the
            // long press and over an IME that restarts the selection (cursor on another line, now and then, 14/11)
            val fromStage = cursor >= 0
            val openedAt = remember { android.os.SystemClock.uptimeMillis() }
            val initialSel = remember { lyrics.selection }
            // saveable: after a recreation the 400 ms guard does not shut the touches out again
            var guarding by rememberSaveable { mutableStateOf(fromStage) }
            val logContext = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                if (fromStage) {
                    EditorOpenLog.opened(logContext, cursor, fieldCursor, uiState.text.length)
                    // after the focus and the first layout: the route's selection once more (the IME may have answered by now)
                    androidx.compose.runtime.withFrameNanos { }
                    androidx.compose.runtime.withFrameNanos { }
                    if (guarding && lyrics.selection != initialSel) lyrics = lyrics.copy(selection = initialSel)
                    kotlinx.coroutines.delay(OPEN_GUARD_MS)
                    // and when the guard ends, if the person did nothing yet
                    if (guarding && lyrics.selection != initialSel) lyrics = lyrics.copy(selection = initialSel)
                    guarding = false
                }
            }
            fun edit(v: TextFieldValue) {
                // the text as it was when this event came, not the last composed one: two events in the same frame (the IME
                // batching a typed and a deleted character) both saw the old one
                val before = lyrics.text
                val onlySelection = v.text == shown.text && v.selection != shown.selection
                if (fromStage) {
                    val since = android.os.SystemClock.uptimeMillis() - openedAt
                    if (onlySelection) EditorOpenLog.selection(logContext, since, if (guarding) "ime" else "touch", v.selection.start, v.selection.end, kept = guarding)
                    if (guarding && onlySelection) { lyrics = shown.copy(selection = initialSel); return }
                    if (guarding) guarding = false   // typing: the person took over
                }
                lyrics = v
                if (v.text != before) onText(Chords.withMeta(if (look.chords) v.text else LyricsView.merge(uiState.text, v.text), uiState.meta))
            }
            // the tools' lambdas are the same on every key (they read the live text and `edit` through states): with new ones
            // each time, the whole strip (sections and ~15 chord chips) was drawn again on every typed character
            val shownNow by rememberUpdatedState(shown)
            val editNow by rememberUpdatedState<(TextFieldValue) -> Unit>(::edit)
            val stripSection = remember { { kind: Sections.Kind -> editNow(insertSection(shownNow, kind)) } }
            // nothing to highlight under the cursor (no selection, empty line): says how (B-28)
            val stripHighlight = remember(appMessage, highlightHint) { { toggleHighlight(shownNow).let { if (it == shownNow) appMessage.show(highlightHint) else editNow(it) } } }
            val stripChord = remember { { chord: String -> editNow(insertChord(shownNow, chord)) } }
            // new chord: goes where the lyrics cursor was
            if (newChord) {
                ChordDialog(
                    onDone = { chord -> newChord = false; edit(insertChord(shown, chord)) },
                    onDismiss = { newChord = false },
                )
            }
            // a low window (a phone on its side): the top (name, steps, the two screens, the tools) took the whole height with
            // the keyboard up and the field was left with none (test on the OnePlus, 14/11). Here the selector of the two
            // screens goes up to the title's bar, in its middle, with the steps, save and delete on the right; the tools stay
            // right under it and the field gets the height that is left
            val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
            // the steps go in the bar only where there is room for them next to the selector
            val wide = wideLists() || (compact && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600)
            // a tap on a neutral area (not on a button or the field: those take the touch first) takes the focus off and hides
            // the keyboard; the cursor and selection live in `lyrics`, so they are still there when the field is tapped again
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            Column(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { if (!guarding) { focusManager.clearFocus(); keyboard?.hide() } }
            }) {
                // top: back, the song's name with the artist under it, previous/next, save and delete (on every screen)
                // by value: reading uiState inside would rebuild the bar on every key
                val canDelete = uiState.canDelete
                val headerActions: @Composable RowScope.() -> Unit = {
                    // tablets have the room on this line; on a phone it goes on the line below
                    if (wide && position != null) EditorSteps(position, step)
                    TipIcon(stringResource(R.string.action_save), AppIcons.Save, { if (uiState.mode == EditorMode.IMPORTED_UPDATE) replacing = true else onSave() }, tint = MaterialTheme.colorScheme.primary)
                    // delete straight in the bar, in red, after save (user, 01/11: no ⋮ for a single option); only for a saved song
                    if (canDelete) TipIcon(stringResource(R.string.editor_delete), AppIcons.Delete, { deleting = true }, tint = MaterialTheme.colorScheme.error)
                }
                val tabChoice: @Composable (Modifier) -> Unit = { m ->
                    SegmentedChoice(
                        listOf(Choice(EditorTab.DATA, R.string.editor_tab_data), Choice(EditorTab.LYRICS, if (look.chords) R.string.editor_lyrics_chords else R.string.editor_lyrics)),
                        tab, { tab = it }, m,
                    )
                }
                if (compact) {
                    CompactBar(
                        title = uiState.title.ifBlank { stringResource(R.string.editor_new) },
                        subtitle = uiState.author,
                        onBack = cancel,
                        // 40 dp tall like the segments are drawn (the 48 dp touch area would make the bar taller than its icons)
                        center = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) { tabChoice(Modifier.widthIn(max = BAR_SELECTOR_WIDTH)) } },
                        right = headerActions,
                        modifier = Modifier.padding(horizontal = Spacing.m),
                    )
                } else ScreenHeader(
                    title = uiState.title.ifBlank { stringResource(R.string.editor_new) },
                    subtitle = uiState.author,
                    onBack = cancel,
                    // 16dp at the start, like the fields: the back arrow draws on the margin (at 8dp it sat off the
                    // fields and its touch area went past the screen's edge; audit 03/11)
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Spacing.s),
                    actions = headerActions,
                )
                if (!wide && position != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), horizontalArrangement = Arrangement.End) { EditorSteps(position, step) }
                }
                // the import note (it used to be the header's subtitle)
                val note = when (uiState.mode) {
                    EditorMode.IMPORTED_NEW -> R.string.editor_imported_new
                    EditorMode.IMPORTED_UPDATE -> R.string.editor_imported_update
                    else -> null
                }
                if (note != null) {
                    Text(
                        stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s),
                    )
                }
                // the two screens, one at a time, the same on every size; the second is named for what the profile holds
                if (!compact) Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), contentAlignment = Alignment.TopCenter) { tabChoice(Modifier.widthIn(max = DATA_MAX_WIDTH)) }
                Crossfade(tab, Modifier.weight(1f).fillMaxWidth(), label = "editor tab") { current ->
                    when (current) {
                        EditorTab.DATA -> DataPane(
                            uiState, look, onTitle, onAuthor, onYear, onTempo, onTranspose, onCapo,
                            track, trackName, onPickTrack, onRemoveTrack, midiOn, { midiOpen = true }, { tagging = true },
                            pdf, attachPdf, { removingPdf = true },
                        )
                        // a PDF song has no lyrics field: the card says what it is and offers to replace or remove it
                        EditorTab.LYRICS -> if (pdf != null) Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m), contentAlignment = Alignment.TopCenter) {
                            PdfSongCard(pdf, onReplace = onPickPdf, onRemove = { removingPdf = true }, modifier = Modifier.widthIn(max = DATA_MAX_WIDTH), onInvert = onPdfInvert)
                        } else Column(Modifier.fillMaxSize()) {
                            // one line of one-tap buttons: the highlighter, the sections and the chords (the lyrics have the rest)
                            ToolStrip(
                                previewing = previewing,
                                onPreview = { previewing = !previewing },
                                chords = look.chords,
                                usedChords = androidx.compose.runtime.produceState(remember { usedChords(uiState.text) }, uiState.text) {
                                    // read a moment after typing stops, not on every keystroke (audit 01/11)
                                    kotlinx.coroutines.delay(CHORDS_AFTER_TYPING_MS)
                                    value = usedChords(uiState.text)
                                }.value,
                                onSection = stripSection,
                                onHighlight = stripHighlight,
                                onChord = stripChord,
                                onNewChord = { newChord = true },
                            )
                            // the lyrics take all the room left (bug 31/10: they stopped short of the bottom); only the
                            // preview scrolls, the text field is sized by the space instead
                            Column(
                                Modifier
                                    .weight(1f)
                                    .then(if (previewing) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                    .padding(horizontal = Spacing.m, vertical = if (compact) 0.dp else Spacing.s),
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
                                    // "Lyrics" in the lyrics only profile, "Lyrics and chords" with chords (user, 01/11)
                                    label = { Text(stringResource(if (look.chords) R.string.editor_lyrics_chords else R.string.editor_lyrics)) },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    textStyle = MaterialTheme.typography.bodyLarge,
                                    // the highlighted phrases show with the marker, as on stage (B-28)
                                    visualTransformation = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).let { faded ->
                                        remember(faded, look.highlightColor) { HighlightLook(androidx.compose.ui.graphics.Color(look.highlightColor), faded) }
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus)
                                        // the first moments after opening from the stage: touches that came through are ignored
                                        .then(if (guarding) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } } else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (tagging) {
        val options = br.app.stagebook.ui.components.tagOptions(allTags + uiState.tags)
        val initial = br.app.stagebook.ui.components.tagStates(options, listOf(uiState.tags))
        br.app.stagebook.ui.components.TagsDialog(options, initial, onTags) { tagging = false }
    }
    if (askingPdf) {
        ConfirmDialog(stringResource(R.string.editor_pdf_ask_lyrics), stringResource(R.string.editor_pdf_attach), { onPickPdf() }, { askingPdf = false }, destructive = false)
    }
    if (removingPdf) {
        ConfirmDialog(stringResource(R.string.editor_pdf_remove_confirm), stringResource(R.string.editor_pdf_remove), onRemovePdf, { removingPdf = false })
    }
    if (discarding) {
        ConfirmDialog(
            stringResource(R.string.editor_discard_confirm),
            stringResource(R.string.editor_discard),
            { discarding = false; leave() },
            { discarding = false; afterLeave = null },
        )
    }
    if (moving != 0) {
        val by = moving
        // the same kind of window as "Leave without saving?", with the third answer: stay
        QuestionDialog(
            title = stringResource(R.string.editor_move_title),
            message = stringResource(R.string.editor_move_message),
            yesLabel = stringResource(R.string.editor_move_save),
            onYes = { moving = 0; onSaveGo(by) },
            noLabel = stringResource(R.string.editor_move_discard),
            onNo = { moving = 0; onGo(by) },
            onOutside = { moving = 0 },
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = { moving = 0 },
        )
    }
    if (replacing) {
        ConfirmDialog(
            stringResource(R.string.editor_replace_confirm, br.app.stagebook.ui.songs.shortName(uiState.title)),
            stringResource(R.string.editor_replace),
            { replacing = false; onSave() },
            { replacing = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            stringResource(R.string.songs_delete_confirm, br.app.stagebook.ui.songs.shortName(uiState.title)),
            stringResource(R.string.action_delete),
            onDelete,
            { deleting = false },
        )
    }
}

/** The editor's two screens. */
private enum class EditorTab { DATA, LYRICS }

/** The details screen's column is as wide as a phone's, centred on a tablet (the fields don't stretch). */
private val DATA_MAX_WIDTH = 560.dp
/** The selector of the two screens in the title bar of a low window: narrower, so the name and the right side fit around it. */
private val BAR_SELECTOR_WIDTH = 280.dp

/** "‹ 3 / 12 ›": the song's place in the list the editor came from, and the arrows to step through it. */
@Composable
private fun EditorSteps(position: EditorPosition, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TipIcon(stringResource(R.string.editor_previous), AppIcons.ChevronLeft, { onStep(-1) }, enabled = position.hasPrev)
        Text(stringResource(R.string.editor_position, position.number, position.total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TipIcon(stringResource(R.string.editor_next), AppIcons.ChevronRight, { onStep(1) }, enabled = position.hasNext)
    }
}

/** The "Data" screen: everything about the song except its text, one column with room (two to a line where they fit). */
@Composable
private fun DataPane(
    uiState: SongEditorUiState,
    look: Look,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onYear: (String) -> Unit,
    onTempo: (String) -> Unit,
    onTranspose: (Int) -> Unit,
    onCapo: (Int) -> Unit,
    track: String?,
    trackName: String,
    onPickTrack: () -> Unit,
    onRemoveTrack: () -> Unit,
    midiOn: Boolean,
    onMidi: () -> Unit,
    onTags: () -> Unit,
    pdf: EditorPdf?,
    onPickPdf: () -> Unit,
    onRemovePdf: () -> Unit,
) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.m), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = DATA_MAX_WIDTH).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            TitleField(uiState, onTitle, Modifier.fillMaxWidth())
            AuthorField(uiState, onAuthor, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                YearField(uiState, onYear, Modifier.weight(1f))
                TempoField(uiState, onTempo, Modifier.weight(1f))
            }
            // key and capo (chords profile; keyboard has no capo, as before)
            if (look.chords) {
                KeyCapoControls(uiState.text, onTranspose, onCapo, showCapo = look.instrument != br.app.stagebook.data.model.Instrument.KEYBOARD, meta = uiState.meta)
            }
            // tags and backing track side by side, as fields like the others; the track only with the player on (user, 30/10)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TagsField(uiState.tags, Modifier.weight(1f), onTags)
                if (track != null) TrackField(track.isNotEmpty(), trackName, onPickTrack, onRemoveTrack, Modifier.weight(1f))
            }
            PdfField(pdf, onPickPdf, onRemovePdf, Modifier.fillMaxWidth())
            if (midiOn) MidiField(uiState.midi.isEmpty(), onMidi, Modifier.fillMaxWidth())
            Text(stringResource(R.string.editor_auto_meta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolDivider() = androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = Spacing.xs))

/** The song's MIDI (on entering and leaving the stage) as a field like the others: "None" in grey or "On" in the accent. */
@Composable
private fun MidiField(empty: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val taps = br.app.stagebook.ui.components.rememberTapSource(onOpen)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedTextField(
        value = stringResource(if (empty) R.string.editor_track_empty else R.string.editor_midi_on),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.song_midi_title)) },
        leadingIcon = { Icon(AppIcons.Piano, null, tint = if (empty) muted else MaterialTheme.colorScheme.primary) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (empty) muted else MaterialTheme.colorScheme.onSurface),
        interactionSource = taps,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

/**
 * The backing track as a field like the others (user, 01/11): "Backing track" on top and the track's name, or
 * "None" in grey; tapping picks or swaps it, × takes it off. A song whose audio didn't come with it (Android's own copy, a
 * file whose track was lost) keeps the name as an alert that says the audio has to be attached again.
 */
@Composable
private fun TrackField(has: Boolean, name: String, onPick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val taps = br.app.stagebook.ui.components.rememberTapSource(onPick)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val missing = !has && name.isNotEmpty()
    OutlinedTextField(
        value = if (has) name.ifEmpty { stringResource(R.string.editor_track_unnamed) } else name.ifEmpty { stringResource(R.string.editor_track_empty) },
        supportingText = if (missing) {
            { Text(stringResource(R.string.editor_track_missing)) }
        } else null,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.stage_backing_track)) },
        isError = missing,
        leadingIcon = {
            if (missing) Icon(AppIcons.Warning, null, tint = MaterialTheme.colorScheme.error)
            else Icon(br.app.stagebook.ui.components.TrackMarkIcon, null, tint = if (has) MaterialTheme.colorScheme.primary else muted)
        },
        trailingIcon = if (has) {
            { IconButton(onClick = onRemove) { Icon(AppIcons.Close, stringResource(R.string.stage_track_remove)) } }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (has) MaterialTheme.colorScheme.onSurface else muted),
        interactionSource = taps,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

/** The song's PDF as a field like the others: "None" in grey (tap to attach) or its name (tap to replace, × to remove). */
@Composable
private fun PdfField(pdf: EditorPdf?, onPick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val taps = br.app.stagebook.ui.components.rememberTapSource(onPick)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val has = pdf != null
    OutlinedTextField(
        value = pdf?.name?.ifEmpty { "PDF" } ?: stringResource(R.string.editor_pdf_empty),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.editor_pdf_label)) },
        isError = pdf?.lost == true,
        supportingText = if (pdf?.lost == true) { { Text(stringResource(R.string.editor_pdf_missing)) } } else null,
        leadingIcon = { Icon(PdfFileIcon, null, tint = if (pdf?.lost == true) MaterialTheme.colorScheme.error else if (has) MaterialTheme.colorScheme.primary else muted) },
        trailingIcon = if (has) {
            { IconButton(onClick = onRemove) { Icon(AppIcons.Close, stringResource(R.string.editor_pdf_remove)) } }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (has) MaterialTheme.colorScheme.onSurface else muted),
        interactionSource = taps,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

/** The song's tags as a field like the others: "Tags" on top and the list, or "None" in grey; tapping opens the window. */
@Composable
private fun TagsField(tags: List<String>, modifier: Modifier = Modifier, onEdit: () -> Unit) {
    val taps = br.app.stagebook.ui.components.rememberTapSource(onEdit)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedTextField(
        value = if (tags.isEmpty()) stringResource(R.string.tags_empty) else tags.joinToString(", "),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.tags_title)) },
        leadingIcon = { Icon(AppIcons.Label, null, tint = if (tags.isEmpty()) muted else MaterialTheme.colorScheme.primary) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (tags.isEmpty()) muted else MaterialTheme.colorScheme.onSurface),
        interactionSource = taps,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

/** End of the line at position `at` (outside the text = start). */
internal fun lineEnd(text: String, at: Int): Int {
    if (at !in 0..text.length) return 0
    return text.indexOf('\n', at).let { if (it < 0) text.length else it }
}

@Composable
private fun TitleField(uiState: SongEditorUiState, onTitle: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = uiState.title,
        onValueChange = onTitle,
        label = { Text(stringResource(R.string.editor_title)) },
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
        modifier = modifier,
    )
}

@Composable
private fun AuthorField(uiState: SongEditorUiState, onAuthor: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = uiState.author,
        onValueChange = onAuthor,
        label = { Text(stringResource(R.string.editor_author)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

@Composable
private fun YearField(uiState: SongEditorUiState, onYear: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = uiState.year,
        onValueChange = onYear,
        label = { Text(stringResource(R.string.editor_year)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

/** Song BPM (`{tempo}`): the metronome starts at it (no BPM, at 100). */
@Composable
private fun TempoField(uiState: SongEditorUiState, onTempo: (String) -> Unit, modifier: Modifier) {
    val tempo = uiState.meta.tempoText
    OutlinedTextField(
        value = tempo,
        onValueChange = onTempo,
        label = { Text(stringResource(R.string.editor_tempo)) },
        singleLine = true,
        isError = tempo.isNotEmpty() && uiState.meta.tempo == 0,
        // out of range, the field says which range: a red outline alone didn't say why the BPM was ignored (B-07)
        supportingText = if (tempo.isNotEmpty() && uiState.meta.tempo == 0) {
            { Text(stringResource(R.string.editor_tempo_range, br.app.stagebook.domain.Chords.MIN_TEMPO, br.app.stagebook.domain.Chords.MAX_TEMPO), maxLines = 1) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

internal object ToolStripProbe { @Volatile var drawn = 0 }   // tests: how many times the strip was composed

/**
 * The one line of tools over the lyrics: preview, highlighter, section shortcuts and chord shortcuts, each a single tap and
 * the strip scrolls sideways. Always in view on this screen, the keyboard up or not.
 */
@Composable
private fun ToolStrip(
    previewing: Boolean,
    onPreview: () -> Unit,
    chords: Boolean,
    usedChords: List<String>,
    onSection: (Sections.Kind) -> Unit,
    onHighlight: () -> Unit,
    onChord: (String) -> Unit,
    onNewChord: () -> Unit,
) {
    androidx.compose.runtime.SideEffect { ToolStripProbe.drawn++ }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the song as it looks on stage (while previewing it becomes the pencil, back to the text)
        TipIcon(
            stringResource(if (previewing) R.string.editor_back_to_text else R.string.editor_preview),
            if (previewing) AppIcons.Edit else AppIcons.Slideshow, onPreview,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!previewing) {
            // highlight the selected phrase (or the word at the cursor) on stage; tapping inside a highlight takes it off. Its
            // own button with the marker icon, apart from the shortcuts (the user couldn't find it among them, B-28)
            TipIcon(stringResource(R.string.editor_highlight), AppIcons.Highlighter, onHighlight, tint = MaterialTheme.colorScheme.primary)
            // chords before the sections (user's order); lyrics only profile: no chords in the editor
            if (chords) {
                ToolDivider()
                ChordBar(usedChords, onChord, onNewChord)
            }
            ToolDivider()
            SectionBar(onSection)
        }
    }
}

/** Section shortcuts: each tap puts the section title in the lyrics at the cursor. */
@Composable
private fun RowScope.SectionBar(onPick: (Sections.Kind) -> Unit) {
    val names = rememberSectionNames(true)
    SECTION_SHORTCUTS.forEach { kind ->
        SuggestionChip(onClick = { onPick(kind) }, label = { Text(names.label(Sections.Header(kind, "", "", kind.pt)), style = MaterialTheme.typography.titleSmall) })
    }
}

/**
 * Chord shortcuts: "+" for a new chord first (so it is in view without scrolling) and then the ones the song already
 * uses; each tap puts `[chord]` at the cursor.
 */
@Composable
private fun RowScope.ChordBar(chords: List<String>, onPick: (String) -> Unit, onNew: () -> Unit) {
    TipIcon(stringResource(R.string.editor_chord_new), AppIcons.Add, onNew, tint = MaterialTheme.colorScheme.primary)
    chords.forEach { c -> SuggestionChip(onClick = { onPick(c) }, label = { Text(c, style = MaterialTheme.typography.titleSmall) }) }
}

/** The song as it looks on stage, with the look chosen in settings (just to check). */
@Composable
private fun EditorPreview(text: String, look: Look) {
    val names = rememberSectionNames(look.translateSections)
    val chordLook = ChordLook(look.chords, look.chordScale, Color(look.chordColor))
    val lines = remember(text, look, names) { stageLines(Chords.fromChordsOverLyrics(text), names, Color(look.sectionColor), look.showSections, chordLook, look.sectionScale, Color(look.highlightColor), look.sectionFont.family).map { it.text } }
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
    br.app.stagebook.ui.components.FollowBusyWhile()
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

private const val CHORDS_AFTER_TYPING_MS = 300L

/**
 * The title's bar in a low window: back and the song's name on the left, the selector of the two screens in the middle of
 * the bar, the steps, save and delete on the right. The middle one keeps the bar's center unless the right side needs the
 * room; the name takes what is left of the left side and ends in an ellipsis.
 */
@Composable
private fun CompactBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    center: @Composable () -> Unit,
    right: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.layout.Layout(
        content = {
            Box(Modifier.layoutId("back")) { br.app.stagebook.ui.components.BackButton(onBack) }
            Column(Modifier.layoutId("name")) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Box(Modifier.layoutId("center")) { center() }
            Row(Modifier.layoutId("right"), verticalAlignment = Alignment.CenterVertically, content = right)
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = Spacing.s.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        fun find(id: String) = measurables.first { it.layoutId == id }
        val r = find("right").measure(loose)
        val b = find("back").measure(loose)
        // the name keeps at least a few letters (and an ellipsis); the selector takes what is left, never under the back
        val minName = 56.dp.roundToPx()
        val left = b.width + gap + minName + gap
        val c = find("center").measure(loose.copy(maxWidth = (width - r.width - gap - left).coerceAtLeast(0)))
        val centerX = ((width - c.width) / 2).coerceAtMost(width - r.width - gap - c.width).coerceAtLeast(left.coerceAtMost(width - c.width).coerceAtLeast(0))
        val n = find("name").measure(loose.copy(maxWidth = (centerX - gap - b.width - gap).coerceAtLeast(0)))
        val h = maxOf(b.height, n.height, c.height, r.height)
        layout(width, h) {
            b.placeRelative(0, (h - b.height) / 2)
            n.placeRelative(b.width + gap, (h - n.height) / 2)
            c.placeRelative(centerX, (h - c.height) / 2)
            r.placeRelative(width - r.width, (h - r.height) / 2)
        }
    }
}
