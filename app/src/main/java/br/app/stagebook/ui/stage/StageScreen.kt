package br.app.stagebook.ui.stage

import br.app.stagebook.ui.components.AppMenu
import androidx.compose.foundation.clickable
import br.app.stagebook.domain.forSong
import br.app.stagebook.ui.components.QuestionDialog
import br.app.stagebook.ui.components.LocalAppMessage
import androidx.compose.material3.AlertDialog
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
import android.annotation.SuppressLint
import android.Manifest
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import br.app.stagebook.data.TrackStore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.unit.IntOffset
import br.app.stagebook.data.model.ChordListPlace
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import br.app.stagebook.domain.Chords
import androidx.compose.ui.unit.TextUnit
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.R
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.domain.PageSlots
import br.app.stagebook.domain.Pagination
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.StageFit
import br.app.stagebook.ui.PedalHandler
import br.app.stagebook.ui.components.EmptyState
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.wideLists
import br.app.stagebook.ui.theme.StagebookTheme
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing
import java.util.Date
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animate
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import br.app.stagebook.ui.components.ConfirmDialog

/** Page "-1" = the song's last page (going back from a song lands at the end of the previous one). */
private const val LAST_PAGE = -1

private val SPACES = Regex("\\s+")

/** With the fingers still for this long, the pinch rebuilds the pages (before that, it only scales the text). */
private const val PINCH_REFLOW_MS = 120L
private const val PAGE_SLOTS = 7      // dots in the footer (7 × 28dp fit on phones with the clock)
private const val PAGE_IN_MS = 220   // the new page coming in (and the dragged one going back with it)
private const val EDIT_OFFER_MS = 4_000L   // the edit button goes away by itself

@Composable
fun StageRoute(
    onEditSong: (songId: String, cursor: Int) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    viewModel: StageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerUi by viewModel.playerUi.collectAsStateWithLifecycle()
    val usbReady by viewModel.usbReady.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(viewModel.start) }
    // pick the song's track (OGG, MP3 or WAV): copied into the app when coming back from the Android picker
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val message = LocalAppMessage.current
    val trackBad = stringResource(R.string.stage_track_bad)
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pickingFor
        pickingFor = null
        if (uri != null && id != null) scope.launch {
            if (!viewModel.attachTrack(id, uri)) message.show(trackBad, long = true)
        }
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    KeepScreenOn()
    // the song on screen tells the player which backing track to play; the output notice shows once
    val shownTrack = uiState.songs.getOrNull(index.coerceIn(0, (uiState.songs.size - 1).coerceAtLeast(0)))?.track
    LaunchedEffect(shownTrack, uiState.loading) { if (!uiState.loading) viewModel.showSong(shownTrack.orEmpty()) }
    // the position on screen (clamped): a setlist that shrank must not send the band a song past its end
    LaunchedEffect(index, uiState.loading) { if (!uiState.loading) viewModel.songShown(index.coerceIn(0, (uiState.songs.size - 1).coerceAtLeast(0))) }
    // first play: warns and asks to skip battery saving (once) and, on Android 13+, the player notification
    var askBattery by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.togglePlay() }
    fun onPlay() {
        val starting = !(playerUi.playing || playerUi.waiting)
        when {
            starting && viewModel.needsBatteryAsk() -> askBattery = true
            starting && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> viewModel.togglePlay()
        }
    }
    if (askBattery) {
        fun answer(allow: Boolean) {
            askBattery = false
            viewModel.batteryAsked()
            if (allow) {
                @SuppressLint("BatteryLife")   // a stage app: playing with the screen off is the point, with a notice first
                val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
                runCatching { context.startActivity(ask) }
            }
            onPlay()
        }
        QuestionDialog(
            stringResource(R.string.player_battery_title), stringResource(R.string.player_battery_text),
            stringResource(R.string.player_battery_ok), { answer(true) },
            stringResource(R.string.player_battery_later), { answer(false) },
        )
    }
    LaunchedEffect(playerUi.message) {
        if (playerUi.message.isNotEmpty()) message.show(playerUi.message, long = true)
    }
    if (!uiState.loading && uiState.songs.isNotEmpty()) {
        StageScreen(
            uiState = uiState,
            index = index.coerceIn(0, uiState.songs.lastIndex),
            page = page,
            onPosition = { i, p -> index = i; page = p },
            onZoom = viewModel::setZoom,
            onEdit = onEditSong,
            onHighlightWords = viewModel::highlightWords,
            onBack = onBack,
            onSaveShape = viewModel::saveShape,
            playing = playerUi.playing || playerUi.waiting,
            onTogglePlay = ::onPlay,
            onPickTrack = { id -> pickingFor = id; picker.launch(TrackStore.MIME) },
            onRemoveTrack = viewModel::removeTrack,
            onHiddenChords = viewModel::setHiddenChords,
            onTranspose = viewModel::transpose,
            onCapo = viewModel::setCapo,
            onAutoScroll = viewModel::setAutoScroll,
            onMetronomeSound = viewModel::metronomeSound,
            onTempo = viewModel::setTempo,
            metroUsbReady = usbReady,
            onPlayChord = viewModel::playChord,
            onKeyboardHintSeen = viewModel::keyboardCapoHintSeen,
            onSongMix = viewModel::setSongMix,
            onGlobalMix = viewModel::setGlobalMix,
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color(uiState.look.bg))) {
            // a deleted (or empty) setlist doesn't open a black stage with no explanation
            if (!uiState.loading) {
                EmptyState(AppIcons.QueueMusic, stringResource(R.string.stage_empty_title), stringResource(R.string.stage_empty_message))
            }
        }
    }
}

/**
 * Stage: one song at a time, full screen and lit, in pages (no scrolling: the pedal turns the whole page, so you don't
 * lose track of what was sung). At the end of a song it goes to the next in the setlist. A tap on the right half goes
 * forward, on the left goes back. Holding a line offers to edit the lyrics there; the top pencil opens the editor on
 * the page on screen.
 */
@Composable
fun StageScreen(
    uiState: StageUiState,
    index: Int,
    page: Int,
    onPosition: (song: Int, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    onZoom: (songId: String, zoom: Float) -> Unit = { _, _ -> },
    onEdit: (songId: String, cursor: Int) -> Unit = { _, _ -> },
    onHighlightWords: (songId: String, line: Int, shown: String, from: Int, to: Int) -> Unit = { _, _, _, _, _ -> },
    onBack: (() -> Unit)? = null,
    onSaveShape: (songId: String, chord: String, define: String) -> Unit = { _, _, _ -> },
    onHiddenChords: (songId: String, hidden: Set<String>) -> Unit = { _, _ -> },
    onTranspose: (songId: String, semitones: Int) -> Unit = { _, _ -> },
    onCapo: (songId: String, capo: Int) -> Unit = { _, _ -> },
    playing: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onPickTrack: (songId: String) -> Unit = {},
    onRemoveTrack: (songId: String) -> Unit = {},
    onAutoScroll: (level: Int) -> Unit = {},
    onMetronomeSound: (bpm: Int?) -> Unit = {},
    onTempo: (songId: String, bpm: Int) -> Unit = { _, _ -> },
    metroUsbReady: Boolean = false,   // the player reached the USB interface: the metronome restarts there, in step
    onPlayChord: (notes: List<Int>, keyboard: Boolean) -> Unit = { _, _ -> },
    onKeyboardHintSeen: () -> Unit = {},
    onSongMix: (songId: String, mixer: br.app.stagebook.domain.MixerConfig?) -> Unit = { _, _ -> },
    onGlobalMix: (br.app.stagebook.domain.MixerConfig) -> Unit = {},
) {
    // the mixer over the stage, with the song playing (user, 31/10)
    var mixerOpen by remember { mutableStateOf(false) }
    // × on a chord in the list: asks first (on stage an accidental tap is easy)
    var hidingChord by remember { mutableStateOf<String?>(null) }
    val look = uiState.look
    // chord tapped on stage: its diagram (only in the lyrics and chords profile)
    var shownChord by remember { mutableStateOf<String?>(null) }
    val fg = Color(look.fg)
    // leaving the stage (arrow or back gesture) asks first: an accidental tap during the show doesn't take the lyrics
    // off the screen (user's request, 23/10)
    var askExit by rememberSaveable { mutableStateOf(false) }
    if (onBack != null) {
        BackHandler { askExit = true }
        if (askExit) {
            ConfirmDialog(
                stringResource(R.string.stage_exit_confirm),
                stringResource(R.string.stage_exit),
                onConfirm = onBack,
                onDismiss = { askExit = false },
                destructive = false,
            )
        }
    }
    val song = uiState.songs[index]
    // keyboard doesn't use capo: chords show in the sounding key (chart in G with capo on fret 2 → A), without changing
    // the saved song (user's request, 29/10)
    val keyboard = look.instrument == br.app.stagebook.data.model.Instrument.KEYBOARD
    val keyboardCapo = remember(song.text, keyboard, look.chords) { if (keyboard && look.chords) Chords.capo(song.text) else 0 }
    val shownText = remember(song.text, keyboardCapo) { if (keyboardCapo > 0) Chords.transpose(song.text, keyboardCapo) else song.text }
    // the first time, a short message explains (it goes away by itself)
    var keyboardHint by remember { mutableIntStateOf(0) }
    LaunchedEffect(keyboardCapo) {
        if (keyboardCapo > 0 && !look.keyboardCapoHintSeen) {
            keyboardHint = keyboardCapo
            onKeyboardHintSeen()
            delay(KEYBOARD_HINT_MS)
            keyboardHint = 0
        }
    }
    hidingChord?.let { chord ->
        ConfirmDialog(
            stringResource(R.string.stage_chord_hide_confirm, chord),
            stringResource(R.string.stage_chord_hide_ok),
            onConfirm = { onHiddenChords(song.id, Chords.hiddenChords(song.text) + chord) },
            onDismiss = { hidingChord = null },
            destructive = false,
        )
    }
    shownChord?.let { chord ->
        ChordShapePopup(
            chord = chord,
            instrument = look.instrument,
            songText = song.text,
            onSave = { define -> onSaveShape(song.id, chord, define) },
            onDismiss = { shownChord = null },
            onPlay = { notes -> onPlayChord(notes, look.instrument == br.app.stagebook.data.model.Instrument.KEYBOARD) },
            extras = look.extrasOn,
        )
    }
    // live zoom during the pinch; saved to the song when the hand stops
    val minZoom = Look.MIN_FONT / look.fontSp
    val maxZoom = Look.MAX_STAGE_FONT / look.fontSp
    // the state lives while the song is open (it isn't recreated when the saved zoom comes back from the repository:
    // the gesture would keep writing to the old state and only the first pinch worked)
    val zoomState = remember(index) { mutableFloatStateOf(song.zoom.coerceIn(minZoom, maxZoom)) }
    var zoom by zoomState
    val unsavedZoom = remember(index) { mutableStateOf(false) }   // pinch on this song not saved yet
    var zoomTouched by remember(index) { mutableStateOf(false) }
    LaunchedEffect(song.zoom) { if (!zoomTouched) zoom = song.zoom.coerceIn(minZoom, maxZoom) }
    // the live zoom is only read inside effects, the layer and the badge: reading it in composition recomposed the
    // whole stage on every pinch frame
    val songNow by rememberUpdatedState(song)
    LaunchedEffect(index, zoomTouched) {
        if (!zoomTouched) return@LaunchedEffect
        snapshotFlow { zoomState.floatValue }.collectLatest { z ->
            unsavedZoom.value = z != songNow.zoom
            delay(700)
            if (z != songNow.zoom) onZoom(songNow.id, z)
            unsavedZoom.value = false
            delay(2_500)
            zoomTouched = false
        }
    }
    // the pedal changed songs before the pinch was saved: save on leaving it (with this song's state, kept on entry: on
    // exit the screen is already building the next one)
    DisposableEffect(index) {
        val id = song.id
        onDispose { if (unsavedZoom.value) onZoom(id, zoomState.floatValue) }
    }
    // measuring and paginating text is expensive on the Fire: during the pinch the text only scales, and the pages are
    // rebuilt when the fingers stop
    var layoutZoom by remember(index) { mutableFloatStateOf(zoomState.floatValue) }
    LaunchedEffect(index) {
        snapshotFlow { zoomState.floatValue }.collectLatest { z ->
            if (z == layoutZoom) return@collectLatest
            delay(PINCH_REFLOW_MS)
            layoutZoom = z
        }
    }
    // the largest zoom at which the longest word still fits the width (computed with the real width, below)
    var fitZoom by remember(index) { mutableFloatStateOf(Float.MAX_VALUE) }
    val sectionNames = rememberSectionNames(look.translateSections)
    val chordLook = remember(look.chords, look.chordScale, look.chordColor) { ChordLook(look.chords, look.chordScale, Color(look.chordColor)) }
    // keyed on what it uses, not the whole look (auto-scroll speed or a hint flag would redo the lyrics)
    val sourced = remember(shownText, sectionNames, look.sectionColor, look.showSections, chordLook, look.sectionScale, look.sectionFont) {
        stageLines(shownText, sectionNames, Color(look.sectionColor), look.showSections, chordLook, look.sectionScale, Color(look.highlightColor), look.sectionFont.family)
    }
    val lines = remember(sourced) { sourced.map { it.text } }
    // holding a line shows a button to edit it (it doesn't open right away: during a show a resting thumb on the screen
    // can't take the lyrics away); it goes away by itself and on page turn
    val hit = remember(index) { PageHit() }
    var editOffer by remember(index) { mutableStateOf<EditOffer?>(null) }
    // highlighter (user, 02/11): the finger marks words instead of turning pages, until "Done"; off on another song
    var marking by remember(index) { mutableStateOf(false) }
    LaunchedEffect(editOffer) { if (editOffer != null) { delay(EDIT_OFFER_MS); editOffer = null } }
    val haptics = LocalHapticFeedback.current
    // where the track button is (a long press on it doesn't offer to edit the line)
    var trackButton by remember { mutableStateOf<Rect?>(null) }
    // the page's lines as they are on screen (a wrapping line with chords becomes pieces): the tap index is in these
    var pageLines by remember(sourced) { mutableStateOf(sourced) }
    fun editAt(line: Int?) {
        if (song.missing) return
        // the chord list (source -1) isn't a lyrics line: opens at the start
        onEdit(song.id, line?.let { pageLines.getOrNull(it)?.source }?.takeIf { it >= 0 }?.let { lineStart(song.text, it) } ?: -1)
    }
    // the song's line under the finger (null: the chord list or a line the stage made, nothing to highlight)
    fun sourceLine(line: Int?): Int? = line?.let { pageLines.getOrNull(it)?.source }?.takeIf { it >= 0 }
    fun styleFor(z: Float) = TextStyle(
        color = fg,
        fontSize = (look.fontSp * z).sp,
        lineHeight = (look.fontSp * z * 1.25f).sp,
        fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = look.font.family,
        // without cutting the breathing room of the first/last line: a line measured alone has the same height as
        // inside the text, and the sum of the lines is the page's real height
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        hyphens = Hyphens.None,
    )
    // no cache: what it measures is kept in the stage's own remember (the default kept 8 whole-song layouts; audit 01/11)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    var pageCount by remember { mutableIntStateOf(1) }
    var pageCountFor by remember { mutableIntStateOf(-1) }   // which song the pageCount belongs to (measured on screen)
    val current = if (page == LAST_PAGE) pageCount - 1 else page.coerceIn(0, pageCount - 1)

    fun nextTarget(): Pair<Int, Int>? = when {
        current < pageCount - 1 -> index to current + 1
        index < uiState.songs.lastIndex -> index + 1 to 0
        else -> null
    }

    fun previousTarget(): Pair<Int, Int>? = when {
        current > 0 -> index to current - 1
        index > 0 -> index - 1 to LAST_PAGE
        else -> null
    }

    // fast foot: a press that arrives before the requested position shows up with the right song's page count would use
    // stale values (it skipped a song or got lost). It waits in the queue and is applied as soon as the screen settles.
    // Keyed by song order, not by the objects: saving the zoom brings the list again and must not clear the queue.
    val presses = remember(uiState.songs.map { it.id }) { PressQueue() }
    // auto-scroll (T15, scroll mode): any tap or pedal press only pauses
    var autoOn by remember(index) { mutableStateOf(false) }
    var autoAvailable by remember { mutableStateOf(false) }   // scroll mode and the song doesn't fit on one screen
    // metronome (T33c): starts at the song's BPM (no BPM, 100); stops when changing songs. The sound came in T33d
    var metroOn by remember(index) { mutableStateOf(false) }
    val songText = uiState.songs.getOrNull(index)?.text
    val songTempo = remember(songText) { songText?.let(Chords::tempo) ?: 0 }
    var metroBpm by remember(index, songTempo) { mutableIntStateOf(songTempo.takeIf { it > 0 } ?: Chords.DEFAULT_TEMPO) }
    // the metronome's BPM is the song's (user, 02/11): a change made here is saved in the song, a moment after the last
    // press; only after a press, so opening a song without a BPM doesn't write the default into it
    var bpmTouched by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, metroBpm, bpmTouched) {
        if (!bpmTouched || metroBpm == songTempo) return@LaunchedEffect
        delay(BPM_SAVE_MS)
        uiState.songs.getOrNull(index)?.id?.takeIf { it.isNotEmpty() }?.let { onTempo(it, metroBpm) }
    }
    var metroBeat by remember(index) { mutableIntStateOf(-1) }   // current beat since play (0 = first)
    // the sound (T33d) and the blink start together; the blink aims at the moment of each beat since the start (it
    // doesn't drift)
    // the USB interface becoming ready restarts it only when the metronome sounds there: with the device output, play on
    // the track (the player reaching the interface) restarted the click from beat 1 mid-song (audit 02/11)
    val usbRestart = metroUsbReady && look.playerMode && look.metroUsbOut >= 0
    LaunchedEffect(metroOn, metroBpm, look.metroSound, usbRestart) {
        onMetronomeSound(if (metroOn && look.metroSound) metroBpm else null)
        if (!metroOn) { metroBeat = -1; return@LaunchedEffect }
        val periodNanos = 60_000_000_000L / metroBpm
        // the time of the next frame, but never waiting for it longer than a beat: once on the TCL the beats stopped
        // (icon dim, no glow) while the metronome stayed on, as if no frame came; without a frame the count goes on
        // from the time waited, so the beats keep coming (device test, 02/11)
        suspend fun frameOrNull() = kotlinx.coroutines.withTimeoutOrNull(periodNanos / 1_000_000) { androidx.compose.runtime.withFrameNanos { it } }
        val firstFrame = frameOrNull()
        val start = firstFrame ?: 0L
        var now = start
        while (true) {
            // the beat from the clock, not counted: back from the screen off, it jumps to the current beat instead of
            // blinking every missed one in a row (audit 01/11)
            val beat = metroBeatAt(now - start, periodNanos)
            metroBeat = beat
            val wait = ((start + (beat + 1) * periodNanos - now) / 1_000_000).coerceAtLeast(0)
            delay(wait)
            // a frame's time only when the count started on one (both on the same clock)
            now = (if (firstFrame != null) frameOrNull() else null) ?: (now + wait * 1_000_000)
        }
    }
    DisposableEffect(Unit) { onDispose { onMetronomeSound(null) } }
    // the beat blink: on the icon and on the screen edge (user's request, 29/10), stronger on beat 1 of the bar
    val metroPulse = remember { androidx.compose.animation.core.Animatable(0f) }
    // the beat is read here and in the draw phase only: reading it in composition recomposed the whole stage on every
    // beat, for the whole show (audit 30/10). Keyed by the song: the beat counter is a new one for each song, and this
    // kept watching the first song's, so after changing songs the metronome stopped blinking (device test, 02/11)
    LaunchedEffect(index) {
        androidx.compose.runtime.snapshotFlow { metroBeat }.collectLatest { beat ->
            if (beat < 0) { metroPulse.snapTo(0f); return@collectLatest }
            metroPulse.snapTo(if (beat % 4 == 0) 1f else 0.6f)
            // at full speed whatever Android's animation scale: at 0 (animations off, seen on a Chuwi tablet) the
            // blink ended before being drawn and the glow never showed
            kotlinx.coroutines.withContext(FullSpeedMotion) { metroPulse.animateTo(0f, tween(METRO_FLASH_MS)) }
        }
    }
    val countIn by remember(index) { androidx.compose.runtime.derivedStateOf { if (metroBeat in 0 until METRO_COUNT) metroBeat + 1 else 0 } }
    val metroColor = Color(look.metroGlowColor)
    fun go(forward: Boolean) {
        if (autoOn) { autoOn = false; return }
        if (presses.from != null) { presses.steps += if (forward) 1 else -1; return }
        val target = (if (forward) nextTarget() else previousTarget()) ?: return
        presses.from = index to page
        onPosition(target.first, target.second)
    }
    fun next() = go(true)
    fun previous() = go(false)
    // pedal set to "next song"/"previous song": always at its start; with a page turn on the way, it waits
    fun goSong(forward: Boolean) {
        if (presses.from != null) return
        val target = index + if (forward) 1 else -1
        if (target !in uiState.songs.indices) return
        presses.from = index to page
        onPosition(target, 0)
    }
    val settled = pageCountFor == index
    SideEffect {
        if (presses.from != null && settled && presses.from != index to page) {
            presses.from = null
            when {
                presses.steps > 0 -> { presses.steps--; next() }
                presses.steps < 0 -> { presses.steps++; previous() }
            }
        }
    }

    LaunchedEffect(current) { editOffer = null }

    val latest by rememberUpdatedState(
        GestureTargets(
            next = ::next,
            previous = ::previous,
            minZoom = minZoom,
            maxZoom = maxZoom,
            hold = { p ->
                // a long press on the track button belongs to it (change/remove), not "edit this line"
                if (trackButton?.contains(p) == true) return@GestureTargets
                val line = hit.lineAt(p)
                if (!song.missing && line != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    editOffer = EditOffer(p, line)
                }
            },
            dismiss = { (editOffer != null).also { editOffer = null } },
            chord = { p -> look.chords && look.chordTap && hit.chordAt(p)?.let { shownChord = it; true } == true },
            marking = marking,
            mark = { from, to ->
                val start = hit.charAt(from)
                val source = start?.let { sourceLine(it.first) }
                if (start != null && source != null) {
                    // lifted on another line: up to the end (or start) of the first one, highlights never cross lines
                    val end = hit.charAt(to)?.takeIf { it.first == start.first }?.third ?: if (to.y > from.y) start.second.length - 1 else 0
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onHighlightWords(song.id, source, start.second, start.third, end)
                }
            },
        ),
    )

    PedalHandler { p ->
        val (action, runs) = PedalAction.hit(p, look.pedalKeys)
        if (action == PedalAction.NONE) return@PedalHandler false
        // a foot resting on the pedal repeats the key: only the first one turns the page
        if (runs) {
            when (action) {
                PedalAction.NEXT -> next()
                PedalAction.PREVIOUS -> previous()
                PedalAction.NEXT_SONG -> goSong(true)
                PedalAction.PREVIOUS_SONG -> goSong(false)
                PedalAction.PLAY_PAUSE -> if (look.playerMode && !song.missing && song.track.isNotEmpty()) onTogglePlay()
                PedalAction.NONE -> Unit
            }
        }
        true   // the "release" is consumed too (some pedals send only that for media keys)
    }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(look.bg))
            .padding(horizontal = Spacing.l),
    ) {
        // lean header: song, artist and the page dots. On phones the artist and year drop to their own line, full width
        // (sharing the line with the title, they got cut)
        val metaInline = wideLists()
        Row(Modifier.fillMaxWidth().padding(top = Spacing.m, bottom = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        // a long title comes first and pushes the artist right, up to the key and capo badges (user, 31/10); the
        // artist keeps a little room so it doesn't vanish
        BoxWithConstraints(Modifier.weight(1f)) {
        val titleMax = if (metaInline && song.meta.isNotEmpty()) maxWidth * 0.8f else maxWidth
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            // leave the stage in one tap, like Google's full-screen viewers: Android's bars stay hidden and the edge
            // gesture was a fight (user's request, 23/10). Icon only.
            if (onBack != null) {
                IconButton(onClick = { askExit = true }) {
                    Icon(AppIcons.Back, stringResource(R.string.action_back), tint = fg.copy(alpha = 0.6f))
                }
            }
            // position in the setlist as "1 of 2" (user's choice, 30/10): "1/2" next to the page dots read as a page number
            if (uiState.songs.size > 1) {
                Text(
                    stringResource(R.string.stage_position, index + 1, uiState.songs.size),
                    style = MaterialTheme.typography.titleMedium, color = fg.copy(alpha = 0.6f),
                )
            }
            Text(
                if (song.missing) stringResource(R.string.set_item_missing) else song.title,
                // a bit bigger than the rest of the header (user's request, 23/10): only the song name
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = titleMax),
            )
            if (metaInline && song.meta.isNotEmpty()) {
                Text(
                    song.meta,
                    style = MaterialTheme.typography.titleMedium,
                    color = fg.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        }
            // after the artist (user's request, 29/10): capo lit in the accent color, then the key; on phones they go
            // to the artist line, otherwise the title gets shorter. No backing track badge: the play only shows when
            // there is one (T29)
            if (!song.missing && metaInline && look.chords) SongKeyBadges(shownText, fg, showCapo = !keyboard, keyboardCapo = keyboardCapo, onTranspose = { onTranspose(song.id, it) }, onCapo = { onCapo(song.id, it) })
            if (!song.missing) {
                IconButton(onClick = { editAt(hit.firstLine()) }) {
                    Icon(AppIcons.Edit, stringResource(R.string.stage_edit), tint = fg.copy(alpha = 0.6f))
                }
            }
        }
        // key and capo belong to chords: they don't show in the lyrics only profile
        val hasKey = look.chords && remember(shownText) { Chords.key(shownText).isNotEmpty() || Chords.capo(shownText) > 0 }
        if (!metaInline && (song.meta.isNotEmpty() || (hasKey && !song.missing))) {
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.meta,
                    style = MaterialTheme.typography.titleSmall,
                    color = fg.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!song.missing && hasKey) SongKeyBadges(shownText, fg, showCapo = !keyboard, keyboardCapo = keyboardCapo, onTranspose = { onTranspose(song.id, it) }, onCapo = { onCapo(song.id, it) })
            }
        }

        // horizontal drag: the page follows the finger (offset only, without measuring text again) and, released past
        // the threshold, turns toward the drag (user's request, 23/10)
        var dragX by remember { mutableFloatStateOf(0f) }
        val dragScope = rememberCoroutineScope()
        var settle by remember { mutableStateOf<Job?>(null) }   // the page going back into place after release
        val swipeMin = with(LocalDensity.current) { Sizes.swipe.toPx() }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(index) {
                    // a single gesture: two fingers = zoom; one finger tapping without dragging = turn the page; one
                    // finger dragging horizontally = turn toward the drag; one finger still for ~0.5 s = offer to edit
                    // the line
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // highlighter: the finger marks from where it touched to where it lifts (a tap: one word)
                        if (latest.marking) {
                            var end = down.position
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                end = change.position
                                change.consume()
                            }
                            latest.mark(down.position, end)
                            return@awaitEachGesture
                        }
                        // a new finger before the page goes back: the return stops and the finger takes over (otherwise
                        // both fought)
                        settle?.cancel()
                        var drag = Offset.Zero
                        val holdAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                        var last = down.uptimeMillis
                        var pinched = false
                        var moved = false
                        var held = false
                        var taken = false   // a button on top (edit, zoom) already used the tap
                        while (true) {
                            val event = if (pinched || moved || held) awaitPointerEvent()
                            else withTimeoutOrNull(maxOf(holdAt - last, 1L)) { awaitPointerEvent() }
                            if (event == null) {
                                held = true
                                latest.hold(down.position)
                                continue
                            }
                            last = event.changes.first().uptimeMillis
                            if (event.changes.any { it.isConsumed }) taken = true
                            if (event.changes.count { it.pressed } >= 2) {
                                val change = event.calculateZoom()
                                if (change != 1f) {
                                    zoom = (minOf(zoom, fitZoom) * change).coerceIn(minOf(latest.minZoom, fitZoom), minOf(latest.maxZoom, fitZoom))
                                    zoomTouched = true
                                }
                                pinched = true
                                event.changes.forEach { it.consume() }
                            } else if (event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) {
                                moved = true
                            }
                            if (!pinched && !held && !taken) {
                                event.changes.firstOrNull { it.id == down.id }?.let { drag = it.position - down.position }
                                // only horizontal movement moves the page; up to a third of the width, so it doesn't
                                // leave the screen, minus the stretch before the drag counts, so the page doesn't jump
                                // when it starts
                                if (moved && abs(drag.x) > abs(drag.y)) {
                                    val follow = (abs(drag.x) - viewConfiguration.touchSlop).coerceAtLeast(0f) * sign(drag.x)
                                    dragX = follow.coerceIn(-size.width / 3f, size.width / 3f)
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                        val swiped = !pinched && !held && !taken && moved && abs(drag.x) > swipeMin && abs(drag.x) > abs(drag.y)
                        if (swiped && !latest.dismiss()) {
                            // the new page comes in from the side the finger came from. The page doesn't snap back all
                            // at once (that was the stutter, 23/10): it returns along with the turn, continuing from
                            // where the finger let go
                            if (drag.x < 0) latest.next() else latest.previous()
                            val from = dragX
                            settle = dragScope.launch { animate(from, 0f, animationSpec = tween(PAGE_IN_MS)) { v, _ -> dragX = v } }
                        } else if (dragX != 0f) {
                            val from = dragX
                            settle = dragScope.launch { animate(from, 0f, animationSpec = tween(180)) { v, _ -> dragX = v } }
                        }
                        // with the edit button open, tapping outside only closes it (no page turn)
                        if (!pinched && !moved && !held && !taken && !latest.dismiss() && !latest.chord(down.position)) {
                            if (down.position.x > size.width / 2f) latest.next() else latest.previous()
                        }
                    }
                },
        ) {
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val gutter = with(LocalDensity.current) { Spacing.xxl.roundToPx() }
            // never break a word in the middle: the lyrics don't go over the size at which the longest word fits
            val widest = remember(lines, look.fontSp, look.bold, look.font) {
                val words = lines.asSequence()
                    .flatMap { it.text.split(SPACES).asSequence() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
                widths(measurer, words.map(::AnnotatedString), styleFor(1f)).maxOrNull() ?: 0
            }
            val fitOne = StageFit.zoomToFit(widest, width)
            val fitTwo = StageFit.zoomToFit(widest, (width - gutter) / 2)
            val halfWidth = (width - gutter) / 2
            fun measureLines(style: TextStyle, w: Int) = lineHeights(measurer, lines, style, w)
            // automatic: decides by this song's lines at the size it shows (1 column)
            val styleOne = styleFor(minOf(layoutZoom, fitOne))
            // heights in 1 column: automatic needs them to decide and, when it picks 1 column, they're the same as the
            // pagination's (measuring text is the expensive part on the Fire: only once)
            val oneLines = remember(lines, styleOne, width) { lazy { measureLines(styleOne, width) } }
            // scroll (T13): always one column
            val scrollMode = look.stageMode == br.app.stagebook.data.model.StageMode.SCROLL
            val columns = remember(lines, styleOne, width, height, look.columns, layoutZoom, fitTwo, scrollMode) {
                if (scrollMode) 1
                else if (look.columns != StageColumns.AUTO) StageFit.columns(look.columns, false, emptyList(), halfWidth, true)
                else StageFit.columns(
                    StageColumns.AUTO,
                    fitsOnePageInOneColumn = Pagination.paginate(oneLines.value, height).size <= 1,
                    lineWidthsPx = widths(measurer, lines.filter { it.isNotEmpty() && it.spanStyles.isEmpty() }, styleOne),
                    columnWidthPx = halfWidth,
                    zoomFitsTwoColumns = layoutZoom <= fitTwo,
                )
            }
            val fit = if (columns == 2) fitTwo else fitOne
            // in automatic the pinch can grow up to the 1 column limit (past half the screen, it becomes 1 column)
            SideEffect { fitZoom = if (look.columns == StageColumns.AUTO) fitOne else fit }
            val style = styleFor(minOf(layoutZoom, fit))
            val columnWidth = if (columns == 2) halfWidth else width
            // a line with chords that wraps at this width: one piece per screen line, and only the ones with a chord
            // keep the space above (otherwise the 2nd part got a gap with no chord at all). The song's chord list at
            // the top or end (only in the lyrics and chords profile): a space reserved in the text, as tall as the rows
            // of diagrams at this width, so the pagination counts it.
            val density = LocalDensity.current
            val stripChordCount = remember(shownText) { stripChords(shownText).size }
            val stripPlace = if (look.chords && stripChordCount > 0) look.chordList else ChordListPlace.NONE
            val stripEm = with(density) {
                val perRow = maxOf(1, columnWidth / STRIP_ITEM_WIDTH.roundToPx())
                val rows = (stripChordCount + perRow - 1) / perRow
                (rows * STRIP_ITEM_HEIGHT.toPx() + Spacing.m.toPx()) / style.fontSize.toPx()
            }
            val fitted = remember(sourced, style, columnWidth, stripPlace, stripEm) {
                val split = fitTabs(measurer, splitWrappedChordLines(measurer, sourced, style, columnWidth), style, columnWidth)
                val strip = StageLine(chordListLine(stripEm), -1)
                when (stripPlace) {
                    ChordListPlace.TOP -> listOf(strip) + split
                    ChordListPlace.BOTTOM -> split + strip
                    ChordListPlace.NONE -> split
                }
            }
            val shownLines = remember(fitted) { if (fitted === sourced) lines else fitted.map { it.text } }
            SideEffect { pageLines = fitted }
            val measured = remember(shownLines, style, columnWidth) {
                if (shownLines === lines && style == styleOne && columnWidth == width) oneLines.value else lineHeights(measurer, shownLines, style, columnWidth)
            }
            val sheets = remember(measured, height, columns) { Pagination.sheets(measured, height, columns) }
            // scroll: the "steps" play the role of pages (pedal, tap queue and dots stay the same). Where auto-scroll
            // paused (the next steps start from there); reset when changing songs.
            var anchor by remember(index) { mutableIntStateOf(0) }
            val steps = remember(measured, height, scrollMode, anchor) { if (scrollMode) br.app.stagebook.domain.ScrollSteps.steps(measured, height, anchor) else emptyList() }
            val stops = if (scrollMode) steps.size else sheets.size
            SideEffect { pageCount = stops; pageCountFor = index }
            val stopAt = if (page == LAST_PAGE) stops - 1 else page.coerceIn(0, stops - 1)
            val shownAt = if (scrollMode) 0 else stopAt
            // each animation page carries its own text: the one leaving can't be built with the new song's lines (that
            // was the crash when changing songs)
            // remembered: built on every recomposition, it joined the whole song again (scroll mode) and, being a new
            // object, reset the hit layouts and the text's own remember (audit 01/11)
            val shown = remember(index, shownAt, sheets, shownLines, style, columns, chordLook, look.chordFont, scrollMode) { PageContent(
                index,
                shownAt,
                if (scrollMode) listOf(joinLines(shownLines))
                else sheets[shownAt].map { range -> joinLines(if (range.isEmpty()) emptyList() else shownLines.subList(range.first, range.last + 1)) },
                if (scrollMode) listOf(0) else sheets[shownAt].map { range -> if (range.isEmpty()) -1 else range.first },
                style,
                columns,
                // chord: bold, in its color and size, following the lyrics zoom
                style.copy(color = chordLook.color, fontSize = style.fontSize * chordLook.scale, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified),
            ) }
            val chordPx = with(LocalDensity.current) { shown.chordStyle.fontSize.toPx() }
            // scroll: the lyrics move up to the current step; when the step changes (within the song), the line where
            // you continue lights up
            val scrollY = remember(index) { androidx.compose.animation.core.Animatable(0f) }
            // taps on the page find the line under the finger in the scrolled text, not the top of the song (audit 01/11)
            SideEffect { hit.show(shown, columnWidth, gutter, chordPx, if (scrollMode) ({ scrollY.value }) else ({ 0f })) }
            val syncing = remember(index) { BooleanArray(1) }   // an auto-scroll pause sets the step without scrolling
            LaunchedEffect(index, stopAt, steps) {
                if (syncing[0]) { syncing[0] = false; return@LaunchedEffect }
                val target = steps.getOrNull(stopAt)?.offset?.toFloat() ?: 0f
                if (page == LAST_PAGE) scrollY.snapTo(target) else scrollY.animateTo(target, tween(PAGE_IN_MS * 2))
            }
            // auto-scroll: moves up by itself at the speed from settings until the end; when paused, the step becomes
            // the current position (the next tap continues from there, without going back)
            val autoRan = remember(index) { BooleanArray(1) }
            val maxScroll = steps.lastOrNull()?.offset ?: 0
            val autoPx = with(density) { (look.autoScroll * br.app.stagebook.data.model.Look.AUTO_SCROLL_DP_PER_LEVEL).dp.toPx() }
            LaunchedEffect(autoOn, maxScroll, autoPx) {
                if (!autoOn) {
                    if (autoRan[0]) {
                        autoRan[0] = false
                        // paused: the real position becomes a step, and the next pedal press continues exactly from
                        // there
                        val at = scrollY.value.roundToInt()
                        anchor = at
                        val k = br.app.stagebook.domain.ScrollSteps.steps(measured, height, at).indexOfFirst { it.offset >= at }.coerceAtLeast(0)
                        if (k != stopAt) { syncing[0] = true; onPosition(index, k) }
                    }
                    return@LaunchedEffect
                }
                autoRan[0] = true
                var last = androidx.compose.runtime.withFrameNanos { it }
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    // a long gap between frames (screen off, a stall) doesn't jump the text ahead (audit 01/11)
                    val v = (scrollY.value + autoPx * autoScrollNanos(now - last) / 1_000_000_000f).coerceAtMost(maxScroll.toFloat())
                    last = now
                    scrollY.snapTo(v)
                    if (v >= maxScroll) { autoOn = false; break }
                }
            }
            var flash by remember(index) { mutableStateOf<IntRange?>(null) }   // y..y+height of the lit line, on screen
            LaunchedEffect(index, stopAt) {
                if (stopAt <= 0) { flash = null; return@LaunchedEffect }
                flash = if (scrollMode) steps.getOrNull(stopAt)?.let { it.markY until it.markY + it.markHeight }
                else sheets.getOrNull(stopAt)?.firstOrNull()?.let { range ->
                    // turning pages (T14): the first line with text on the new page
                    val i = range.firstOrNull { !measured[it].blank } ?: return@let null
                    val top = (range.first until i).sumOf { measured[it].height }
                    top until top + measured[i].height
                }
                delay(FLASH_MS)
                flash = null
            }
            AnimatedContent(
                modifier = Modifier.graphicsLayer {
                    // while the pinch hasn't stopped, the current page only scales (from the top corner)
                    val pinchScale = minOf(zoomState.floatValue, fit) / minOf(layoutZoom, fit)
                    scaleX = pinchScale
                    scaleY = pinchScale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = dragX
                },
                targetState = shown,
                // text rebuilt by zoom isn't a page turn: swap without animating
                contentKey = { it.song to it.page },
                transitionSpec = {
                    // turn the page: the new one comes in from the side of the movement
                    val forward = targetState.song > initialState.song ||
                        (targetState.song == initialState.song && targetState.page > initialState.page)
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(PAGE_IN_MS)) { it / 6 * dir } + fadeIn(tween(PAGE_IN_MS))) togetherWith
                        (slideOutHorizontally(tween(160)) { -it / 6 * dir } + fadeOut(tween(160)))
                },
                label = "page",
            ) { content ->
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(with(LocalDensity.current) { gutter.toDp() })) {
                    content.columns.forEachIndexed { column, text ->
                        val strip = remember(text) { text.getStringAnnotations(CHORD_LIST_TAG, 0, text.length).firstOrNull() }
                        var laid by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
                        Box(Modifier.weight(1f).fillMaxHeight().then(if (scrollMode) Modifier.clipToBounds() else Modifier)) {
                            ChordText(
                                text,
                                style = content.style,
                                chordStyle = content.chordStyle,
                                modifier = if (!scrollMode) Modifier.fillMaxSize()
                                // the scroll moves the text's own layer: the song is drawn once and only slides (offset
                                // redrew the whole song, every chord included, on every frame of auto-scroll; audit 30/10)
                                else Modifier.fillMaxWidth().clipToBounds().wrapContentHeight(Alignment.Top, unbounded = true)
                                    .graphicsLayer { translationY = -scrollY.value },
                                onTextLayout = { laid = it; hit.laidOut(content, column, it) },
                            )
                            // the chord list, over the space reserved for it
                            val l = laid
                            if (strip != null && l != null && strip.start < l.layoutInput.text.length) {
                                val top = l.getLineTop(l.getLineForOffset(strip.start)).roundToInt()
                                ChordStrip(
                                    shownText,
                                    look.instrument,
                                    Color(look.chordColor),
                                    // in scroll mode the list moves with the lyrics (it sits outside the text's moved layer)
                                    Modifier.offset { IntOffset(0, top - if (scrollMode) scrollY.value.roundToInt() else 0) },
                                    onHide = { hidingChord = it },
                                    onRestore = { onHiddenChords(song.id, emptySet()) },
                                )
                            }
                        }
                    }
                    // last sheet with only the left column: the right one stays empty, it doesn't stretch the other
                    repeat(content.sheetColumns - content.columns.size) { Box(Modifier.weight(1f)) }
                }
            }
            // metronome count-in (1 2 3 4) very big in the middle, before starting
            if (metroOn && look.metroCount && countIn > 0) {
                Text(
                    countIn.toString(),
                    // big enough for the band to see from across the stage
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 200.sp, lineHeight = 200.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // the line where you continue, lit for a moment (T14), at the left column's width
            androidx.compose.animation.AnimatedVisibility(
                visible = flash != null,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(600)),
            ) {
                val band = flash ?: IntRange(0, 0)
                Box(
                    Modifier
                        .offset { IntOffset(0, band.first) }
                        .width(with(density) { columnWidth.toDp() })
                        .height(with(density) { (band.last - band.first + 1).coerceAtLeast(0).toDp() })
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = FLASH_ALPHA), MaterialTheme.shapes.small),
                )
            }
            SideEffect { autoAvailable = scrollMode && !song.missing && maxScroll > 0 }
            // player (player mode only): transparent play/pause in the bottom right corner, over the lyrics
            // only songs with a backing track show the button (T29, user's decision); the track is picked in the editor.
            // A track playing goes on when the song changes, so the button stays (to stop it) on any song meanwhile
            if (look.playerMode && !song.missing && (song.track.isNotEmpty() || playing)) {
                TrackButton(
                    hasTrack = song.track.isNotEmpty() || playing,
                    playing = playing,
                    stops = look.trackStops,
                    fg = fg,
                    onClick = { if (playing || song.track.isNotEmpty()) onTogglePlay() else onPickTrack(song.id) },
                    onChange = { onPickTrack(song.id) },
                    onRemove = { onRemoveTrack(song.id) },
                    // the mixer lives in play's long press now, adjusting while the song plays (user, 01/11: one button
                    // less over the lyrics)
                    onMixer = { mixerOpen = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s).onGloballyPositioned { trackButton = it.boundsInParent() },
                )
            }
            // the song's own mix over the global one, or the global mixer itself (user, 31/10 and 01/11)
            if (mixerOpen) StageMixerSheet(
                look.mixer.forSong(song.mix),
                own = song.mix != null,
                onMixer = { if (song.mix != null) onSongMix(song.id, it) else onGlobalMix(it) },
                onOwn = { onSongMix(song.id, look.mixer) },
                onGeneral = { onSongMix(song.id, null) },
                onDismiss = { mixerOpen = false },
            )
            if (marking) MarkerBar({ marking = false }, Modifier.align(Alignment.BottomCenter))
            ZoomBadge(
                visible = zoomTouched,
                percent = { (minOf(zoomState.floatValue, fit) * 100).roundToInt() },
                onReset = { zoom = 1f.coerceIn(minZoom, maxZoom); zoomTouched = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            editOffer?.let { offer ->
                // holding a line: edit it, and highlight it (or take the highlight off) right here (user, 01/11, B-28)
                val source = sourceLine(offer.line)
                EditLineButtons(
                    onEdit = { editOffer = null; editAt(offer.line) },
                    canMark = source != null,
                    onMarker = { editOffer = null; marking = true },
                    modifier = Modifier.layout { measurable, c ->
                        // above the finger (below if it doesn't fit), without leaving the screen
                        val p = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                        val gap = Spacing.l.roundToPx()
                        val above = offer.at.y.roundToInt() - gap - p.height
                        layout(p.width, p.height) {
                            p.place(
                                (offer.at.x.roundToInt() - p.width / 2).coerceIn(0, maxOf(width - p.width, 0)),
                                if (above >= 0) above else offer.at.y.roundToInt() + gap,
                            )
                        }
                    },
                )
            }
        }

        // footer: time on the left; on the right the page dots and, on the last page, the next song (or the end of the
        // setlist) in their place. The dots came from the header and replace "Continues →", which repeated what they
        // already show (user's request, 23/10)
        val nextTitle = uiState.songs.getOrNull(index + 1)?.let { if (it.missing) stringResource(R.string.set_item_missing) else it.title }
            ?: uiState.followNext
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageClock(fg.copy(alpha = 0.7f))
            // scroll: auto-scroll ▶/⏸ and speed (−/+), on the clock line (outside the lyrics). A single icon (lit while
            // scrolling); the small window has play and speed (user's request, 29/10). Metronome: while playing, the
            // icon becomes stop and blinks on the beat (stronger on 1); when stopped, it opens the small window
            if (look.extrasOn && look.metroShown) {   // essential mode (T19) or hidden in settings: no metronome
                var metroDialog by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { if (metroOn) metroOn = false else metroDialog = true }) {
                        // the blink in the layer (draw phase): the icon doesn't recompose on every frame of the flash
                        if (metroOn) Icon(
                            AppIcons.Stop, stringResource(R.string.metro_stop),
                            Modifier.graphicsLayer { alpha = 0.35f + 0.65f * metroPulse.value },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        else Icon(AppIcons.Metronome, stringResource(R.string.metro_title), tint = fg.copy(alpha = 0.7f))
                    }
                    if (metroDialog) MetronomeDialog(
                        bpm = metroBpm,
                        onBpm = { metroBpm = it.coerceIn(Chords.MIN_TEMPO, Chords.MAX_TEMPO); bpmTouched = true },
                        onPlay = { metroOn = true; metroDialog = false },
                        onDismiss = { metroDialog = false },
                    )
                }
            }
            if (autoAvailable) {
                var autoDialog by remember { mutableStateOf(false) }
                // the small window opens right above the icon (user's request, 29/10)
                Box {
                    // while scrolling: the icon becomes pause and stops right away (user's request, 29/10); when
                    // stopped, it opens the small window
                    IconButton(onClick = { if (autoOn) autoOn = false else autoDialog = true }) {
                        if (autoOn) Icon(AppIcons.PauseRounded, stringResource(R.string.stage_auto_pause), tint = MaterialTheme.colorScheme.primary)
                        else Icon(AppIcons.AutoScroll, stringResource(R.string.stage_auto_title), tint = fg.copy(alpha = 0.7f))
                    }
                    if (autoDialog) AutoScrollDialog(autoOn, look.autoScroll, { autoOn = !autoOn; autoDialog = false }, onAutoScroll) { autoDialog = false }
                }
            }
            if (current < pageCount - 1) {
                PageDots(current, pageCount, fg, Modifier.weight(1f))
            } else {
                Text(
                    when {
                        nextTitle != null -> stringResource(R.string.stage_next, nextTitle)
                        uiState.songs.size > 1 || uiState.followEnd -> stringResource(R.string.stage_end)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = fg.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (keyboardHint > 0) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.xl).widthIn(max = 560.dp),
        ) {
            Text(stringResource(R.string.stage_keyboard_capo_hint, keyboardHint), Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyLarge)
        }
    }
    // metronome glow on the edge, fading inward, on its own layer on top: each blink redraws only it (the lyrics stay
    // cached as they are), and nothing is drawn outside the blinks
    Spacer(
        Modifier
            .matchParentSize()
            .graphicsLayer()
            .drawBehind {
                val a = metroPulse.value
                if (a <= 0f || !look.metroGlow) return@drawBehind
                val c = metroColor.copy(alpha = a * METRO_GLOW_ALPHA)
                val clear = metroColor.copy(alpha = 0f)
                val accent = metroBeat >= 0 && metroBeat % 4 == 0   // beat 1 of the bar: wider glow
                val g = (if (accent) METRO_GLOW_ACCENT else METRO_GLOW).toPx().coerceAtMost(size.minDimension / 2)
                drawRect(Brush.verticalGradient(listOf(c, clear), 0f, g), size = androidx.compose.ui.geometry.Size(size.width, g))
                drawRect(Brush.verticalGradient(listOf(clear, c), size.height - g, size.height), topLeft = Offset(0f, size.height - g), size = androidx.compose.ui.geometry.Size(size.width, g))
                drawRect(Brush.horizontalGradient(listOf(c, clear), 0f, g), size = androidx.compose.ui.geometry.Size(g, size.height))
                drawRect(Brush.horizontalGradient(listOf(clear, c), size.width - g, size.width), topLeft = Offset(size.width - g, 0f), size = androidx.compose.ui.geometry.Size(g, size.height))
            },
    )
    }
}

/** "Zoom for this song: 120%" while adjusting, with a shortcut back to the default size. */
@Composable
private fun ZoomBadge(visible: Boolean, percent: () -> Int, onReset: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        // read in here: during the pinch only the badge recomposes
        val percent = percent()
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
            Row(
                Modifier.padding(start = Spacing.m, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(stringResource(R.string.stage_zoom, percent), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (percent != 100) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.stage_zoom_reset)) }
                } else {
                    Box(Modifier.size(Sizes.touch))
                }
            }
        }
    }
}

/**
 * Width (px) of each text on a single line, all measured at once: on the Fire, hundreds of separate measurements when
 * changing songs caused a visible stutter.
 */
/**
 * Height of each lyrics line at width [width], in a single measurement of the whole text (measuring line by line built
 * a layout per line: dozens when changing songs, the expensive part on the Fire). A line's height is that of the drawn
 * lines it takes (wraps included); it matches the separate measurement because the line height is fixed
 * (LineHeightStyle.Trim.None). An empty line measures as a space.
 */
internal fun lineHeights(measurer: TextMeasurer, lines: List<AnnotatedString>, style: TextStyle, width: Int): List<Pagination.Line> {
    if (lines.isEmpty()) return emptyList()
    val starts = IntArray(lines.size)
    val all = joinLines(lines, starts)
    val r = measurer.measure(all, style, constraints = Constraints(maxWidth = width))
    return lines.mapIndexed { i, l ->
        val end = starts[i] + maxOf(l.length, 1) - 1
        val first = r.getLineForOffset(starts[i])
        val last = r.getLineForOffset(end)
        Pagination.Line(ceil(r.getLineBottom(last) - r.getLineTop(first)).toInt(), l.isEmpty(), l.hasStringAnnotations(SECTION_TAG, 0, l.length))
    }
}

/**
 * Lines with chords that wrap at width [width] become one piece per screen line (measured in a single pass, like the
 * rest). A piece without a chord loses the space above and becomes a plain line. Without wrapping, returns the same
 * list (the stage reuses the measurement already done).
 */
internal fun splitWrappedChordLines(measurer: TextMeasurer, lines: List<StageLine>, style: TextStyle, width: Int): List<StageLine> {
    val chordAt = lines.indices.filter { lines[it].text.isChordLine() }
    if (chordAt.isEmpty()) return lines
    val subset = chordAt.map { lines[it].text }
    val starts = IntArray(subset.size)
    val r = measurer.measure(joinLines(subset, starts), style, constraints = Constraints(maxWidth = width))
    val pieces = HashMap<Int, List<AnnotatedString>>()
    subset.forEachIndexed { k, l ->
        val s = starts[k]
        val e = s + l.length
        val first = r.getLineForOffset(s)
        val last = r.getLineForOffset(maxOf(e - 1, s))
        if (first == last) return@forEachIndexed
        pieces[chordAt[k]] = (first..last).map { ln ->
            val p = l.subSequence(maxOf(r.getLineStart(ln), s) - s, minOf(r.getLineEnd(ln), e) - s)
            if (p.getStringAnnotations(CHORD_TAG, 0, p.length).isEmpty()) AnnotatedString(p.text.trimEnd()) else p
        }
    }
    if (pieces.isEmpty()) return lines
    return lines.flatMapIndexed { i, l -> pieces[i]?.map { StageLine(it, l.source) } ?: listOf(l) }
}

/** Tablature too wide for the column: the monospace shrinks until it fits (never wraps, or the columns get lost). */
internal fun fitTabs(measurer: TextMeasurer, lines: List<StageLine>, style: TextStyle, width: Int): List<StageLine> {
    if (lines.none { it.text.hasStringAnnotations(TAB_TAG, 0, it.text.length) }) return lines
    // the whole block stays the same size (that of the widest line), so the strings stay aligned with each other
    val widest = lines.filter { it.text.hasStringAnnotations(TAB_TAG, 0, it.text.length) }
        .maxOf { measurer.measure(it.text, style, softWrap = false).size.width }
    if (widest <= width) return lines
    val em = TAB_EM * width / widest * 0.98f
    return lines.map { l -> if (l.text.hasStringAnnotations(TAB_TAG, 0, l.text.length)) StageLine(tabLine(l.text.text, em), l.source) else l }
}

internal fun widths(measurer: TextMeasurer, texts: List<AnnotatedString>, style: TextStyle): List<Int> {
    if (texts.isEmpty()) return emptyList()
    val all = buildAnnotatedString {
        texts.forEachIndexed { i, t -> if (i > 0) append('\n'); append(t) }
    }
    val r = measurer.measure(all, style, softWrap = false)
    return List(r.lineCount) { ceil(r.getLineRight(it) - r.getLineLeft(it)).toInt() }
}

/** What the gesture detector must always read up to date (it doesn't restart on every page). */
private class GestureTargets(
    val next: () -> Unit,
    val previous: () -> Unit,
    val minZoom: Float,
    val maxZoom: Float,
    val hold: (Offset) -> Unit,
    val dismiss: () -> Boolean,   // closes the edit button; true if it was open
    val chord: (Offset) -> Boolean,   // tapped a chord: opens the diagram; true if it opened
    val marking: Boolean = false,      // highlighter on: one finger marks words, no page turn
    val mark: (from: Offset, to: Offset) -> Unit = { _, _ -> },
)

/** `starts`: index in `lines` of the first line of each column (-1 = empty column). */
// also carries the sheet's style and columns: the page leaving in the animation isn't rebuilt (or resized) with the new
// song's
private const val FLASH_MS = 1500L
private const val METRO_FLASH_MS = 180

/** Animation speed that ignores Android's animation scale (the metronome blink is information, not decoration). */
private object FullSpeedMotion : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 1f }
private const val KEYBOARD_HINT_MS = 6_000L
private val METRO_GLOW = 20.dp          // edge glow, fading inward (it doesn't reach the lyrics)
private val METRO_GLOW_ACCENT = 26.dp   // a bit wider on beat 1 of the bar
private const val METRO_GLOW_ALPHA = 0.8f
private const val METRO_COUNT = 4   // the count-in is one bar of 4
private const val FLASH_ALPHA = 0.22f

private data class PageContent(
    val song: Int,
    val page: Int,
    val columns: List<AnnotatedString>,
    val starts: List<Int>,
    val style: TextStyle,
    val sheetColumns: Int,
    val chordStyle: TextStyle,
)

/** The metronome beat (0 = first) [sinceStart] nanoseconds after it started; a frame up to 20 ms early counts. */
internal fun metroBeatAt(sinceStart: Long, periodNanos: Long): Int = ((sinceStart + METRO_EARLY_NANOS) / periodNanos).toInt()

/** The time auto-scroll moves the text for, between two frames: at most [MAX_SCROLL_FRAME_NANOS]. */
internal fun autoScrollNanos(sinceLast: Long): Long = sinceLast.coerceIn(0, MAX_SCROLL_FRAME_NANOS)

private const val METRO_EARLY_NANOS = 20_000_000L
private const val MAX_SCROLL_FRAME_NANOS = 100_000_000L

/** "Edit this line" button offered where the finger held; `line` is the index in `lines`. */
private class EditOffer(val at: Offset, val line: Int)

/** Finds the line under the finger through the text already drawn on the page (line wraps included). */
private class PageHit {
    private var content: PageContent? = null
    private var columnWidth = 0
    private var gutter = 0
    private val layouts = arrayOfNulls<TextLayoutResult>(2)

    private var chordPx = 0f
    private var scroll: () -> Float = { 0f }

    /** [scroll]: how far the text is moved up (scroll mode), so a point on screen maps to the text below it. */
    fun show(c: PageContent, columnWidth: Int, gutter: Int, chordPx: Float, scroll: () -> Float = { 0f }) {
        if (c != content) { content = c; layouts.fill(null) }
        this.columnWidth = columnWidth
        this.gutter = gutter
        this.chordPx = chordPx
        this.scroll = scroll
    }

    fun laidOut(c: PageContent, column: Int, r: TextLayoutResult) { if (c == content && column < layouts.size) layouts[column] = r }

    /** Index in `lines` of the first line of the page on screen. */
    fun firstLine(): Int? = content?.starts?.firstOrNull { it >= 0 }

    /**
     * The chord at point `p`, or null: the ones drawn above the lyrics (marked with [CHORD_TAG], in the line's top
     * band) and the ones on chords-only lines (the tapped word, if it's a chord).
     */
    fun chordAt(p: Offset): String? {
        val c = content ?: return null
        val column = if (c.columns.size > 1 && p.x >= columnWidth + gutter / 2f) 1 else 0
        val layout = layouts[column] ?: return null
        val x = p.x - if (column == 1) columnWidth + gutter else 0
        val y = p.y + scroll()
        if (y < 0 || y > layout.size.height) return null
        val line = layout.getLineForVerticalPosition(y)
        // below the song's last line the layout still answers with that line: a tap there (to go back a page) opened
        // the last chords-only line's chord (device test on the TCL, end of a song, 02/11)
        if (y > layout.getLineBottom(line)) return null
        val text = layout.layoutInput.text
        val top = layout.getLineTop(line)
        val band = chordPx * 1.5f
        if (y <= top + band) {
            val tolerance = chordPx * 0.3f
            text.getStringAnnotations(CHORD_TAG, layout.getLineStart(line), layout.getLineEnd(line)).forEach { a ->
                val x0 = layout.getHorizontalPosition(a.start, true)
                if (x >= x0 - tolerance && x <= x0 + a.item.length * chordPx * 0.7f + tolerance) return a.item
            }
        }
        // chords-only line: the word under the finger
        val lineText = text.text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        if (Chords.isChordLine(lineText)) {
            val o = layout.getOffsetForPosition(Offset(x, y))
            val s = text.text
            var a = o.coerceIn(0, s.length)
            var b = a
            while (a > 0 && !s[a - 1].isWhitespace()) a--
            while (b < s.length && !s[b].isWhitespace()) b++
            // only when the finger is over the word: past the end of the line the offset is the line's end, which
            // picked the last chord and a tap on the side opened it instead of turning the page (device test B-12)
            val tolerance = chordPx * 0.3f
            val over = a < b && x >= layout.getHorizontalPosition(a, true) - tolerance &&
                x <= layout.getHorizontalPosition(b, true) + tolerance
            if (over) s.substring(a, b).takeIf(Chords::isChord)?.let { return it }
        }
        return null
    }

    /**
     * The character at point `p`: index in `lines` of its line, that line's text as shown, and the column in it. Null
     * outside the text.
     */
    fun charAt(p: Offset): Triple<Int, String, Int>? {
        val c = content ?: return null
        val column = if (c.columns.size > 1 && p.x >= columnWidth + gutter / 2f) 1 else 0
        val layout = layouts[column] ?: return null
        val start = c.starts[column]
        val x = p.x - if (column == 1) columnWidth + gutter else 0
        val y = p.y + scroll()
        val text = layout.layoutInput.text
        if (start < 0 || text.isEmpty()) return null
        val o = layout.getOffsetForPosition(Offset(x, y.coerceIn(0f, layout.size.height.toFloat()))).coerceIn(0, text.length - 1)
        val a = text.getStringAnnotations(LINE_TAG, o, o + 1).firstOrNull() ?: return null
        return Triple(start + a.item.toInt(), text.substring(a.start, a.end), o - a.start)
    }

    /** Index in `lines` of the line at point `p`, or null outside the text. */
    fun lineAt(p: Offset): Int? {
        val c = content ?: return null
        val column = if (c.columns.size > 1 && p.x >= columnWidth + gutter / 2f) 1 else 0
        val layout = layouts[column] ?: return null
        val start = c.starts[column]
        val y = p.y + scroll()
        if (start < 0 || y < 0 || y > layout.size.height) return null
        // only the height matters: the whole line is the same at any point in it
        val offset = layout.getLineStart(layout.getLineForVerticalPosition(y))
        val text = layout.layoutInput.text
        val line = text.getStringAnnotations(LINE_TAG, offset, minOf(offset + 1, text.length)).firstOrNull()?.item?.toInt() ?: return null
        return start + line
    }
}

/**
 * Track play/pause: a translucent circle, so it doesn't hide the lyrics. Without a track, the tap picks the file; with
 * a track, a long press opens the song's mixer, or changes or removes the track.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackButton(
    hasTrack: Boolean,
    playing: Boolean,
    stops: Boolean,
    fg: Color,
    onClick: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onMixer: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    // removing asks first: a slip of the finger mid-show would take the track away (user, 01/11)
    var removing by remember { mutableStateOf(false) }
    if (removing) {
        br.app.stagebook.ui.components.ConfirmDialog(
            stringResource(R.string.stage_track_remove_confirm), stringResource(R.string.stage_track_remove_ok),
            onRemove, { removing = false },
        )
    }
    Box(modifier) {
        Box(
            Modifier
                .size(Sizes.touch + Spacing.m)
                .clip(CircleShape)
                .background(fg.copy(alpha = 0.12f))
                .combinedClickable(onClick = onClick, onLongClick = { if (hasTrack) menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                // playing: stop (back to the start, the default) or pause, as chosen in Settings → Player
                when { !hasTrack -> AppIcons.QueueMusic; playing && stops -> AppIcons.Stop; playing -> AppIcons.Pause; else -> AppIcons.Play },
                stringResource(when { !hasTrack -> R.string.stage_track_pick; playing && stops -> R.string.stage_stop; playing -> R.string.stage_pause; else -> R.string.stage_play }),
                tint = fg.copy(alpha = if (hasTrack) 0.85f else 0.5f),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppMenu.color, shadowElevation = AppMenu.shadow, border = AppMenu.border) {
            DropdownMenuItem(text = { Text(stringResource(R.string.stage_mixer_open)) }, leadingIcon = { Icon(AppIcons.Tune, null) }, onClick = { menu = false; onMixer() })
            DropdownMenuItem(text = { Text(stringResource(R.string.stage_track_change)) }, leadingIcon = { Icon(AppIcons.QueueMusic, null) }, onClick = { menu = false; onChange() })
            DropdownMenuItem(text = { Text(stringResource(R.string.stage_track_remove)) }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = { menu = false; removing = true })
        }
    }
}

/** The song's key ({key}) and capo ({capo}) at the top of the stage; nothing if the song has neither. */
/**
 * Metronome (small window above the icon): BPM with − and + and play, which closes the window; the rest is in Settings.
 */
@Composable
private fun MetronomeDialog(
    bpm: Int,
    onBpm: (Int) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gap = with(LocalDensity.current) { Spacing.s.roundToPx() }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = AboveAnchor(gap),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Column(Modifier.padding(Spacing.l).width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.metro_title), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.action_close)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    IconButton(onClick = { onBpm(bpm - 1) }) { Icon(AppIcons.Remove, stringResource(R.string.metro_slower)) }
                    Text(stringResource(R.string.metro_bpm, bpm), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { onBpm(bpm + 1) }) { Icon(AppIcons.Add, stringResource(R.string.metro_faster)) }
                }
                br.app.stagebook.ui.components.SettingsLinkText(
                    stringResource(R.string.metro_in_settings), stringResource(R.string.metro_settings_link),
                    br.app.stagebook.ui.settings.SettingsSection.METRONOME, style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = onPlay, modifier = Modifier.size(Sizes.touch + Spacing.m)) {
                    Icon(AppIcons.PlayRounded, stringResource(R.string.metro_play), Modifier.size(Sizes.touch), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Places the small window above the icon, aligned to its left, without leaving the screen. */
private class AboveAnchor(private val gap: Int) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset = IntOffset(
        anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0),
    )
}

/** Auto-scroll (small window right above the icon): speed from 1 to 10 and play/pause, which closes the window. */
@Composable
private fun AutoScrollDialog(on: Boolean, level: Int, onToggle: () -> Unit, onLevel: (Int) -> Unit, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { Spacing.s.roundToPx() }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = AboveAnchor(gap),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Column(Modifier.padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                // title and the × to close (play also closes)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.stage_auto_title), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.action_close)) }
                }
                Text(stringResource(R.string.stage_auto_speed, level), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    IconButton(onClick = { onLevel(level - 1) }, enabled = level > 1) { Icon(AppIcons.Remove, stringResource(R.string.stage_auto_slower)) }
                    // rounded, big play in the accent color with no background (user's request, 29/10); the touch
                    // target stays big
                    IconButton(onClick = onToggle, modifier = Modifier.size(Sizes.touch + Spacing.m)) {
                        Icon(
                            if (on) AppIcons.PauseRounded else AppIcons.PlayRounded,
                            stringResource(if (on) R.string.stage_auto_pause else R.string.stage_auto_play),
                            Modifier.size(Sizes.touch),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onLevel(level + 1) }, enabled = level < br.app.stagebook.data.model.Look.MAX_AUTO_SCROLL) {
                        Icon(AppIcons.Add, stringResource(R.string.stage_auto_faster))
                    }
                }
                Text(stringResource(R.string.stage_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Key and capo (small window below "Key G", in the same style as the metronome and auto-scroll ones): − value + for
 * each; every change is saved in the song right away. On keyboard only the key (it doesn't use a capo).
 */
@Composable
private fun KeyCapoDialog(text: String, showCapo: Boolean, onTranspose: (Int) -> Unit, onCapo: (Int) -> Unit, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { Spacing.s.roundToPx() }
    val hasChords = remember(text) { Chords.hasChords(text) || Chords.fromChordsOverLyrics(text).let(Chords::hasChords) }
    val key = remember(text) { Chords.key(text).ifEmpty { Chords.detectKey(text) } }
    val capo = remember(text) { Chords.capo(text) }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = BelowAnchor(gap),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Column(Modifier.padding(Spacing.l).width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(if (showCapo) R.string.stage_key_capo_title else R.string.editor_key), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.action_close)) }
                }
                if (showCapo) Text(stringResource(R.string.editor_key), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    IconButton(onClick = { onTranspose(-1) }, enabled = hasChords) { Icon(AppIcons.Remove, stringResource(R.string.editor_transpose_down)) }
                    Text(key.ifEmpty { "–" }, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { onTranspose(1) }, enabled = hasChords) { Icon(AppIcons.Add, stringResource(R.string.editor_transpose_up)) }
                }
                if (showCapo) {
                    Text(stringResource(R.string.editor_capo), style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        IconButton(onClick = { onCapo(capo - 1) }, enabled = capo > 0) { Icon(AppIcons.Remove, stringResource(R.string.editor_capo_down)) }
                        Text(if (capo == 0) "0" else stringResource(R.string.editor_capo_fret, capo), style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { onCapo(capo + 1) }, enabled = capo < 12) { Icon(AppIcons.Add, stringResource(R.string.editor_capo_up)) }
                    }
                }
                Text(stringResource(R.string.stage_key_capo_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Places the small window below the badges, aligned to their right, without leaving the screen. */
private class BelowAnchor(private val gap: Int) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset = IntOffset(
        (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

@Composable
private fun SongKeyBadges(
    text: String,
    fg: Color,
    showCapo: Boolean = true,
    keyboardCapo: Int = 0,
    onTranspose: (Int) -> Unit = {},
    onCapo: (Int) -> Unit = {},
) {
    val key = remember(text) { Chords.key(text) }
    // keyboard doesn't use a capo (user's request, 29/10): on its stage the capo doesn't show
    val capo = remember(text, showCapo) { if (showCapo) Chords.capo(text) else 0 }
    if (key.isEmpty() && capo == 0 && keyboardCapo == 0) return
    val sounds = Chords.soundingKey(key, capo)
    val keyDesc = stringResource(R.string.stage_key_desc, key)
    val capoDesc = if (key.isNotEmpty()) stringResource(R.string.stage_capo_sounds_desc, capo, sounds) else stringResource(R.string.stage_capo_desc, capo)
    // a tap on the capo or the key opens − and + for both, without leaving the stage; each change is saved right away
    var adjusting by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = stringResource(R.string.stage_key_adjust)) { adjusting = true },
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the capo comes before the key and lit: it changes what you play, it can't go unnoticed (user's request,
            // 29/10)
            if (capo > 0) {
                val accent = MaterialTheme.colorScheme.primary
                Row(Modifier.clearAndSetSemantics { contentDescription = capoDesc }, verticalAlignment = Alignment.CenterVertically) {
                    // the fretboard icon (the editor's) with the number: no word, as on the rest of the stage (31/10)
                    Icon(AppIcons.Capo, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("$capo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accent)
                    // the key that sounds with the capo, in view (the diagrams stay the chart's)
                    if (key.isNotEmpty()) Text(" " + stringResource(R.string.stage_capo_sounds_short, sounds), style = MaterialTheme.typography.titleSmall, color = accent)
                }
            }
            if (key.isNotEmpty()) {
                Row(Modifier.clearAndSetSemantics { contentDescription = keyDesc }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.MusicNote, null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(key, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg.copy(alpha = 0.8f))
                }
            }
            // keyboard with the chart transposed by the capo: says why the key is different
            if (keyboardCapo > 0) {
                Text(stringResource(R.string.stage_keyboard_capo, keyboardCapo), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (adjusting) KeyCapoDialog(text, showCapo, onTranspose, onCapo, onDismiss = { adjusting = false })
    }
}


/** "Edit this line" and, for a line of the song, "Highlight this line" / "Remove the highlight" side by side. */
@Composable
private fun EditLineButtons(onEdit: () -> Unit, canMark: Boolean, onMarker: () -> Unit, modifier: Modifier = Modifier) {
    // side by side; on a narrow phone the second goes under the first
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        EditOfferButton(AppIcons.Edit, stringResource(R.string.stage_edit_line), onEdit)
        if (canMark) EditOfferButton(AppIcons.Highlighter, stringResource(R.string.stage_marker), onMarker)
    }
}

/**
 * While the highlighter is on: what to do and the way out, fixed over the lyrics (a message that goes away would leave
 * the stage not turning pages with nothing saying why).
 */
@Composable
private fun MarkerBar(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.medium, shadowElevation = 6.dp, modifier = modifier.padding(Spacing.m)) {
        Row(Modifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(AppIcons.Highlighter, null)
            Text(stringResource(R.string.stage_marker_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            androidx.compose.material3.TextButton(onDone) { Text(stringResource(R.string.stage_marker_done)) }
        }
    }
}

@Composable
private fun EditOfferButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
        Row(
            Modifier.heightIn(min = Sizes.touch).padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        }
    }
}

/**
 * One dot per page, with its number (user's request, 23/10): the current one filled and lit, the others with just a dim
 * number. Hidden when the song fits on one page. More than [PAGE_SLOTS] pages: Google's pagination ([PageSlots]),
 * first, current with its neighbors, "…" and last; the same on phones and tablets (user's request, 23/10).
 */
@Composable
private fun PageDots(current: Int, count: Int, color: Color, modifier: Modifier = Modifier) {
    if (count <= 1) return
    // as many dots as the width takes (at most PAGE_SLOTS): on a phone the row went past the edge and the last number was
    // cut (device test B-18)
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterEnd) {
        val fits = ((maxWidth + Spacing.xs) / (Sizes.pageDot + Spacing.xs)).toInt().coerceIn(1, PAGE_SLOTS)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            PageSlots.of(current, count, fits).forEach { i ->
                val on = i == current
                Box(
                    Modifier
                        .size(Sizes.pageDot)
                        .background(if (on) color.copy(alpha = 0.95f) else if (i == null) Color.Transparent else color.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (i == null) "…" else "${i + 1}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (on) MaterialTheme.colorScheme.background else color.copy(alpha = 0.6f),
                        maxLines = 1,
                        softWrap = false,   // "12" in the small dot broke into "1" over "2"
                    )
                }
            }
        }
    }
}

/** Current time in the device's format, updated at the turn of each minute. */
@Composable
private fun StageClock(color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = Date()
        }
    }
    Text(format.format(now), style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1, modifier = modifier)
}

/**
 * The stage always stays lit (the app already hides the system bars on every screen). Counted per view: following the
 * leader, the next song's stage comes in before the old one leaves, and the old one switching it off left the screen
 * free to dim mid-song (audit 30/10).
 */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        ScreenOnCount.add(view)
        onDispose { ScreenOnCount.remove(view) }
    }
}

internal object ScreenOnCount {
    private val count = java.util.WeakHashMap<android.view.View, Int>()

    fun add(view: android.view.View) = synchronized(count) {
        count[view] = (count[view] ?: 0) + 1
        view.keepScreenOn = true
    }

    fun remove(view: android.view.View) = synchronized(count) {
        val left = (count[view] ?: 1) - 1
        if (left > 0) count[view] = left else { count.remove(view); view.keepScreenOn = false }
    }
}

private val previewState = StageUiState(
    loading = false,
    songs = listOf(
        StageSong("Hazard", "Richard Marx · 1991", "[Verse 1]\nPrimeira linha da letra\nSegunda linha\n\n[Chorus]\nO refrão aparece aqui", false),
        StageSong("Malandragem", "Cássia Eller · 1994", "Texto", false),
    ),
)

@LightPreview
@Composable
private fun StageLightPreview() = StagebookTheme(darkTheme = false) {
    StageScreen(previewState.copy(look = previewState.look.copy(fg = 0xFF000000.toInt(), bg = 0xFFFFFFFF.toInt())), 0, 0, { _, _ -> })
}

@DarkPreview
@Composable
private fun StageDarkPreview() = StagebookTheme(darkTheme = true) { StageScreen(previewState, 0, 0, { _, _ -> }) }

/** Pedal/tap presses waiting for the requested position to show (see "fast foot" in StageScreen). */
private class PressQueue {
    var from: Pair<Int, Int>? = null   // position the press left from, not settled yet
    var steps = 0                      // presses that arrived in the meantime (+ forward, - back)
}

private const val BPM_SAVE_MS = 600L   // the song's BPM is saved this long after the last + or − on the metronome
