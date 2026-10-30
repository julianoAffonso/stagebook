package com.juliano.stagebook.ui.stage

import com.juliano.stagebook.ui.components.QuestionDialog
import com.juliano.stagebook.ui.components.LocalAppMessage
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
import com.juliano.stagebook.data.TrackStore
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
import com.juliano.stagebook.data.model.ChordListPlace
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.juliano.stagebook.domain.Chords
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
import com.juliano.stagebook.R
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.StageColumns
import com.juliano.stagebook.domain.PageSlots
import com.juliano.stagebook.domain.Pagination
import com.juliano.stagebook.domain.PedalAction
import com.juliano.stagebook.domain.StageFit
import com.juliano.stagebook.ui.PedalHandler
import com.juliano.stagebook.ui.components.EmptyState
import com.juliano.stagebook.ui.components.AppIcons
import com.juliano.stagebook.ui.components.DarkPreview
import com.juliano.stagebook.ui.components.LightPreview
import com.juliano.stagebook.ui.components.wideLists
import com.juliano.stagebook.ui.theme.StagebookTheme
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing
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
import com.juliano.stagebook.ui.components.ConfirmDialog

/** Página "-1" = a última da música (ao voltar de uma música, cai no fim da anterior). */
private const val LAST_PAGE = -1

private val SPACES = Regex("\\s+")

/** Com os dedos parados por esse tempo, a pinça refaz as páginas (antes disso, só escala o texto). */
private const val PINCH_REFLOW_MS = 120L
private const val PAGE_SLOTS = 7      // bolinhas no rodapé (7 × 28dp cabem no celular com o relógio)
private const val PAGE_IN_MS = 220   // a página nova entrando (e a arrastada voltando ao lugar junto)
private const val EDIT_OFFER_MS = 4_000L   // o botão de editar some sozinho

@Composable
fun StageRoute(
    onEditSong: (songId: String, cursor: Int) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    viewModel: StageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerUi by viewModel.playerUi.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(viewModel.start) }
    // escolher a faixa da música (OGG, MP3 ou WAV): copiada para o app ao voltar do seletor do Android
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
    // a música na tela diz ao player qual backing track tocar; o aviso da saída aparece uma vez
    val shownTrack = uiState.songs.getOrNull(index.coerceIn(0, (uiState.songs.size - 1).coerceAtLeast(0)))?.track
    LaunchedEffect(shownTrack, uiState.loading) { if (!uiState.loading) viewModel.showSong(shownTrack.orEmpty()) }
    LaunchedEffect(index, uiState.loading) { if (!uiState.loading) viewModel.songShown(index) }
    // 1º play: avisa e pede para sair da economia de bateria (uma vez) e, no Android 13+, a notificação do player
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
                @SuppressLint("BatteryLife")   // app de palco: tocar com a tela apagada é a função, com aviso antes
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
            onBack = onBack,
            onSaveShape = viewModel::saveShape,
            playing = playerUi.playing || playerUi.waiting,
            onTogglePlay = ::onPlay,
            onPickTrack = { id -> pickingFor = id; picker.launch(TrackStore.MIME) },
            onRemoveTrack = viewModel::removeTrack,
            onHiddenChords = viewModel::setHiddenChords,
            onAutoScroll = viewModel::setAutoScroll,
            onMetronomeSound = viewModel::metronomeSound,
            onPlayChord = viewModel::playChord,
            onKeyboardHintSeen = viewModel::keyboardCapoHintSeen,
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color(uiState.look.bg))) {
            // setlist apagado (ou vazio) não abre um palco preto sem explicação
            if (!uiState.loading) {
                EmptyState(AppIcons.QueueMusic, stringResource(R.string.stage_empty_title), stringResource(R.string.stage_empty_message))
            }
        }
    }
}

/**
 * Palco: uma música por vez, tela cheia e acesa, em páginas (sem rolagem: o pedal vira a página
 * inteira, para não se perder no que já foi cantado). No fim da música vai para a próxima do setlist.
 * Toque na metade direita avança, na esquerda volta. Segurar uma linha oferece editar a letra nela;
 * o lápis do topo abre o editor na página que está na tela.
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
    onBack: (() -> Unit)? = null,
    onSaveShape: (songId: String, chord: String, define: String) -> Unit = { _, _, _ -> },
    onHiddenChords: (songId: String, hidden: Set<String>) -> Unit = { _, _ -> },
    playing: Boolean = false,
    onTogglePlay: () -> Unit = {},
    onPickTrack: (songId: String) -> Unit = {},
    onRemoveTrack: (songId: String) -> Unit = {},
    onAutoScroll: (level: Int) -> Unit = {},
    onMetronomeSound: (bpm: Int?) -> Unit = {},
    onPlayChord: (notes: List<Int>, keyboard: Boolean) -> Unit = { _, _ -> },
    onKeyboardHintSeen: () -> Unit = {},
) {
    // × num acorde da lista: pergunta antes (no palco um toque sem querer é fácil)
    var hidingChord by remember { mutableStateOf<String?>(null) }
    val look = uiState.look
    // acorde tocado no palco: o desenho dele (só no perfil letra e cifra)
    var shownChord by remember { mutableStateOf<String?>(null) }
    val fg = Color(look.fg)
    // sair do palco (seta ou gesto de voltar) pergunta antes: um toque sem querer no show não tira a letra
    // da tela (pedido do usuário, 23/10)
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
    // teclado não usa capo: os acordes aparecem no tom que soa (cifra em G com capo na 2ª casa → A), sem mudar a música
    // guardada (pedido do usuário, 29/10)
    val keyboard = look.instrument == com.juliano.stagebook.data.model.Instrument.TECLADO
    val keyboardCapo = remember(song.text, keyboard, look.chords) { if (keyboard && look.chords) Chords.capo(song.text) else 0 }
    val shownText = remember(song.text, keyboardCapo) { if (keyboardCapo > 0) Chords.transpose(song.text, keyboardCapo) else song.text }
    // na primeira vez, uma mensagem curta explica (some sozinha)
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
            onPlay = { notes -> onPlayChord(notes, look.instrument == com.juliano.stagebook.data.model.Instrument.TECLADO) },
        )
    }
    // zoom ao vivo durante a pinça; grava na música quando a mão para
    val minZoom = Look.MIN_FONT / look.fontSp
    val maxZoom = Look.MAX_STAGE_FONT / look.fontSp
    // o estado vive enquanto a música está aberta (não é recriado quando o zoom gravado volta do
    // repositório: o gesto continuaria escrevendo no estado antigo e só a 1ª pinça funcionava)
    val zoomState = remember(index) { mutableFloatStateOf(song.zoom.coerceIn(minZoom, maxZoom)) }
    var zoom by zoomState
    val unsavedZoom = remember(index) { mutableStateOf(false) }   // pinça desta música ainda não gravada
    var zoomTouched by remember(index) { mutableStateOf(false) }
    LaunchedEffect(song.zoom) { if (!zoomTouched) zoom = song.zoom.coerceIn(minZoom, maxZoom) }
    // o zoom ao vivo só é lido dentro de efeitos, da camada e do selo: ler na composição recompunha o
    // palco inteiro a cada quadro da pinça
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
    // o pedal trocou de música antes de a pinça ser gravada: grava ao sair dela (com o estado desta
    // música, guardado ao entrar: na saída a tela já está montando a próxima)
    DisposableEffect(index) {
        val id = song.id
        onDispose { if (unsavedZoom.value) onZoom(id, zoomState.floatValue) }
    }
    // medir e paginar o texto é caro no Fire: durante a pinça o texto só escala, e as páginas são
    // refeitas quando os dedos param
    var layoutZoom by remember(index) { mutableFloatStateOf(zoomState.floatValue) }
    LaunchedEffect(index) {
        snapshotFlow { zoomState.floatValue }.collectLatest { z ->
            if (z == layoutZoom) return@collectLatest
            delay(PINCH_REFLOW_MS)
            layoutZoom = z
        }
    }
    // maior zoom em que a palavra mais longa ainda cabe na largura (calculado com a largura real, abaixo)
    var fitZoom by remember(index) { mutableFloatStateOf(Float.MAX_VALUE) }
    val sectionNames = rememberSectionNames(look.translateSections)
    val chordLook = remember(look.chords, look.chordScale, look.chordColor) { ChordLook(look.chords, look.chordScale, Color(look.chordColor)) }
    val sourced = remember(shownText, look, sectionNames) { stageLines(shownText, sectionNames, Color(look.sectionColor), look.showSections, chordLook, look.sectionScale) }
    val lines = remember(sourced) { sourced.map { it.text } }
    // segurar uma linha mostra um botão para editá-la (não abre direto: no show o polegar apoiado
    // na tela não pode tirar a letra da frente); some sozinho e ao virar a página
    val hit = remember(index) { PageHit() }
    var editOffer by remember(index) { mutableStateOf<EditOffer?>(null) }
    LaunchedEffect(editOffer) { if (editOffer != null) { delay(EDIT_OFFER_MS); editOffer = null } }
    val haptics = LocalHapticFeedback.current
    // onde está o botão da faixa (o toque longo nele não oferece editar a linha)
    var trackButton by remember { mutableStateOf<Rect?>(null) }
    // as linhas da página como estão na tela (linha com acordes que quebra vira pedaços): o índice do toque é nelas
    var pageLines by remember(sourced) { mutableStateOf(sourced) }
    fun editAt(line: Int?) {
        if (song.missing) return
        // a lista de acordes (fonte -1) não é linha da letra: abre no começo
        onEdit(song.id, line?.let { pageLines.getOrNull(it)?.source }?.takeIf { it >= 0 }?.let { lineStart(song.text, it) } ?: -1)
    }
    fun styleFor(z: Float) = TextStyle(
        color = fg,
        fontSize = (look.fontSp * z).sp,
        lineHeight = (look.fontSp * z * 1.25f).sp,
        fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = look.font.family,
        // sem cortar o respiro da primeira/última linha: a linha medida sozinha tem a mesma altura
        // que dentro do texto, e a soma das linhas é a altura real da página
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        hyphens = Hyphens.None,
    )
    val measurer = rememberTextMeasurer()
    var pageCount by remember { mutableIntStateOf(1) }
    var pageCountFor by remember { mutableIntStateOf(-1) }   // de qual música é o pageCount (medido na tela)
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

    // pé rápido: um aperto que chega antes de a posição pedida aparecer com a contagem de páginas da
    // música certa usaria valores velhos (pulava uma música ou se perdia). Ele fica na fila e é
    // aplicado assim que a tela assenta.
    // pela ordem das músicas, não pelos objetos: gravar o zoom traz a lista de novo e não pode zerar a fila
    val presses = remember(uiState.songs.map { it.id }) { PressQueue() }
    // rolagem automática (T15, modo rolar): qualquer toque ou pedal só pausa
    var autoOn by remember(index) { mutableStateOf(false) }
    var autoAvailable by remember { mutableStateOf(false) }   // modo rolar e a música não cabe numa tela
    // metrônomo (T33c): começa no BPM da música (sem BPM, 100); para ao trocar de música. O som vem na T33d
    var metroOn by remember(index) { mutableStateOf(false) }
    val songTempo = remember(index, uiState.songs) { uiState.songs.getOrNull(index)?.text?.let(Chords::tempo) ?: 0 }
    var metroBpm by remember(index, songTempo) { mutableIntStateOf(songTempo.takeIf { it > 0 } ?: Chords.DEFAULT_TEMPO) }
    var metroBeat by remember(index) { mutableIntStateOf(-1) }   // tempo atual desde o play (0 = primeiro)
    // o som (T33d) e o piscar começam juntos; o piscar mira o instante de cada tempo desde o começo (não escorrega)
    LaunchedEffect(metroOn, metroBpm, look.metroSound) {
        onMetronomeSound(if (metroOn && look.metroSound) metroBpm else null)
        if (!metroOn) { metroBeat = -1; return@LaunchedEffect }
        val periodNanos = 60_000_000_000L / metroBpm
        val start = androidx.compose.runtime.withFrameNanos { it }
        var beat = 0
        while (true) {
            metroBeat = beat
            beat++
            val now = androidx.compose.runtime.withFrameNanos { it }
            delay(((start + beat * periodNanos - now) / 1_000_000).coerceAtLeast(0))
        }
    }
    DisposableEffect(Unit) { onDispose { onMetronomeSound(null) } }
    // o piscar do tempo: no ícone e na borda da tela (pedido do usuário, 29/10), mais forte no 1 do compasso
    val metroPulse = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(metroBeat) {
        if (metroBeat < 0) { metroPulse.snapTo(0f); return@LaunchedEffect }
        metroPulse.snapTo(if (metroBeat % 4 == 0) 1f else 0.6f)
        metroPulse.animateTo(0f, tween(METRO_FLASH_MS))
    }
    val metroAccent = metroBeat >= 0 && metroBeat % 4 == 0
    val metroColor = Color(look.metroGlowColor)
    val glowPx = with(LocalDensity.current) { (if (metroAccent) METRO_GLOW_ACCENT else METRO_GLOW).toPx() }
    fun go(forward: Boolean) {
        if (autoOn) { autoOn = false; return }
        if (presses.from != null) { presses.steps += if (forward) 1 else -1; return }
        val target = (if (forward) nextTarget() else previousTarget()) ?: return
        presses.from = index to page
        onPosition(target.first, target.second)
    }
    fun next() = go(true)
    fun previous() = go(false)
    // pedal ligado a "próxima música"/"música anterior": sempre no começo dela; com uma virada a caminho, espera
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
                // toque longo no botão da faixa é dele (trocar/tirar), não "editar esta linha"
                if (trackButton?.contains(p) == true) return@GestureTargets
                val line = hit.lineAt(p)
                if (!song.missing && line != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    editOffer = EditOffer(p, line)
                }
            },
            dismiss = { (editOffer != null).also { editOffer = null } },
            chord = { p -> look.chords && look.chordTap && hit.chordAt(p)?.let { shownChord = it; true } == true },
        ),
    )

    PedalHandler { p ->
        val action = PedalAction.of(p.code, look.pedalKeys)
        if (action == PedalAction.NONE) return@PedalHandler false
        // pé apoiado no pedal repete a tecla: só a primeira vira a página
        if (p.down && p.repeat == 0) {
            when (action) {
                PedalAction.NEXT -> next()
                PedalAction.PREVIOUS -> previous()
                PedalAction.NEXT_SONG -> goSong(true)
                PedalAction.PREVIOUS_SONG -> goSong(false)
                PedalAction.PLAY_PAUSE -> if (look.playerMode && !song.missing && song.track.isNotEmpty()) onTogglePlay()
                PedalAction.NONE -> Unit
            }
        }
        true   // o "soltar" também é consumido (alguns pedais mandam só ele para teclas de mídia)
    }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(look.bg))
            .padding(horizontal = Spacing.l),
    ) {
        // topo enxuto: música, artista e as bolinhas das páginas. No celular o artista e o ano descem para uma
        // linha própria, na largura toda (dividindo a linha com o título, eles ficavam cortados)
        val metaInline = wideLists()
        Row(Modifier.fillMaxWidth().padding(top = Spacing.m, bottom = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            // sair do palco num toque, como nos visualizadores de tela cheia do Google: as barras do Android
            // ficam sempre ocultas e o gesto da borda era uma luta (pedido do usuário, 23/10). Só o ícone.
            if (onBack != null) {
                IconButton(onClick = { askExit = true }) {
                    Icon(AppIcons.Back, stringResource(R.string.action_back), tint = fg.copy(alpha = 0.6f))
                }
            }
            if (uiState.songs.size > 1) {
                Text("${index + 1}/${uiState.songs.size}", style = MaterialTheme.typography.titleMedium, color = fg.copy(alpha = 0.6f))
            }
            Text(
                if (song.missing) stringResource(R.string.set_item_missing) else song.title,
                // um pouco maior que o resto do topo (pedido do usuário, 23/10): só o nome da música
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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
            // depois do artista, nesta ordem (pedido do usuário, 29/10): backing track e capo acesos na cor de destaque, depois
            // o tom; no celular vão para a linha do artista, senão o título encurta
            if (metaInline && look.playerMode && song.track.isNotEmpty()) TrackBadge()
            if (!song.missing && metaInline && look.chords) SongKeyBadges(shownText, fg, showCapo = !keyboard, keyboardCapo = keyboardCapo)
            if (!song.missing) {
                IconButton(onClick = { editAt(hit.firstLine()) }) {
                    Icon(AppIcons.Edit, stringResource(R.string.stage_edit), tint = fg.copy(alpha = 0.6f))
                }
            }
        }
        // tom e capo são de cifra: no perfil só letra não aparecem
        val hasKey = look.chords && remember(shownText) { Chords.key(shownText).isNotEmpty() || Chords.capo(shownText) > 0 }
        val hasTrack = look.playerMode && song.track.isNotEmpty()
        if (!metaInline && (song.meta.isNotEmpty() || ((hasKey || hasTrack) && !song.missing))) {
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.meta,
                    style = MaterialTheme.typography.titleSmall,
                    color = fg.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasTrack) TrackBadge(Modifier.padding(end = Spacing.m))
                if (!song.missing && hasKey) SongKeyBadges(shownText, fg, showCapo = !keyboard, keyboardCapo = keyboardCapo)
            }
        }

        // arrastar na horizontal: a página acompanha o dedo (só deslocamento, sem medir texto de novo) e, ao
        // soltar além do limite, vira para o lado do arraste (pedido do usuário, 23/10)
        var dragX by remember { mutableFloatStateOf(0f) }
        val dragScope = rememberCoroutineScope()
        var settle by remember { mutableStateOf<Job?>(null) }   // a página voltando ao lugar depois de soltar
        val swipeMin = with(LocalDensity.current) { Sizes.swipe.toPx() }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(index) {
                    // um gesto só: dois dedos = zoom; um dedo que toca sem arrastar = vira a página;
                    // um dedo que arrasta na horizontal = vira para o lado do arraste;
                    // um dedo parado por ~0,5 s = oferece editar a linha
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // dedo novo antes de a página voltar: a volta para e o dedo assume (senão os dois brigavam)
                        settle?.cancel()
                        var drag = Offset.Zero
                        val holdAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                        var last = down.uptimeMillis
                        var pinched = false
                        var moved = false
                        var held = false
                        var taken = false   // um botão por cima (editar, zoom) já usou o toque
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
                                // só a horizontal mexe a página; até o terço da largura, para não sumir da tela
                                // descontado o trecho até o arraste valer, para a página não dar um pulo ao começar
                                if (moved && abs(drag.x) > abs(drag.y)) {
                                    val follow = (abs(drag.x) - viewConfiguration.touchSlop).coerceAtLeast(0f) * sign(drag.x)
                                    dragX = follow.coerceIn(-size.width / 3f, size.width / 3f)
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                        val swiped = !pinched && !held && !taken && moved && abs(drag.x) > swipeMin && abs(drag.x) > abs(drag.y)
                        if (swiped && !latest.dismiss()) {
                            // a nova página entra pelo lado de onde o dedo veio. A página não volta ao lugar de uma
                            // vez (era o engasgo, 23/10): volta junto com a virada, continuando de onde o dedo soltou
                            if (drag.x < 0) latest.next() else latest.previous()
                            val from = dragX
                            settle = dragScope.launch { animate(from, 0f, animationSpec = tween(PAGE_IN_MS)) { v, _ -> dragX = v } }
                        } else if (dragX != 0f) {
                            val from = dragX
                            settle = dragScope.launch { animate(from, 0f, animationSpec = tween(180)) { v, _ -> dragX = v } }
                        }
                        // com o botão de editar aberto, tocar fora só o fecha (não vira a página)
                        if (!pinched && !moved && !held && !taken && !latest.dismiss() && !latest.chord(down.position)) {
                            if (down.position.x > size.width / 2f) latest.next() else latest.previous()
                        }
                    }
                },
        ) {
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val gutter = with(LocalDensity.current) { Spacing.xxl.roundToPx() }
            // nunca quebrar palavra no meio: a letra não passa do tamanho em que a maior palavra cabe
            val widest = remember(lines, look.fontSp, look.bold) {
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
            // automático: decide pelas frases desta música no tamanho em que ela aparece (1 coluna)
            val styleOne = styleFor(minOf(layoutZoom, fitOne))
            // alturas em 1 coluna: o automático precisa delas para decidir e, dando 1 coluna, são as mesmas
            // da paginação (medir texto é o caro no Fire: uma vez só)
            val oneLines = remember(lines, styleOne, width) { lazy { measureLines(styleOne, width) } }
            // rolar (T13): sempre uma coluna
            val scrollMode = look.stageMode == com.juliano.stagebook.data.model.StageMode.SCROLL
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
            // no automático a pinça pode crescer até o limite de 1 coluna (passando da meia tela, vira 1 coluna)
            SideEffect { fitZoom = if (look.columns == StageColumns.AUTO) fitOne else fit }
            val style = styleFor(minOf(layoutZoom, fit))
            val columnWidth = if (columns == 2) halfWidth else width
            // linha com acordes que quebra nesta largura: um pedaço por linha da tela, e só os com acorde
            // guardam o espaço de cima (senão a 2ª parte ganhava um vão sem acorde nenhum)
            // lista de acordes da música no topo ou no fim (só no perfil letra e cifra): um espaço reservado no texto,
            // com a altura das fileiras de desenhos nesta largura, para a paginação contar com ela
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
            // rolar: os "passos" fazem o papel das páginas (pedal, fila de toques e bolinhas continuam iguais)
            // onde a rolagem automática pausou (os passos seguintes partem dali); zera ao trocar de música
            var anchor by remember(index) { mutableIntStateOf(0) }
            val steps = remember(measured, height, scrollMode, anchor) { if (scrollMode) com.juliano.stagebook.domain.ScrollSteps.steps(measured, height, anchor) else emptyList() }
            val stops = if (scrollMode) steps.size else sheets.size
            SideEffect { pageCount = stops; pageCountFor = index }
            val stopAt = if (page == LAST_PAGE) stops - 1 else page.coerceIn(0, stops - 1)
            val shownAt = if (scrollMode) 0 else stopAt
            // cada página da animação leva o próprio texto: a que está saindo não pode ser montada com
            // as linhas da música nova (era o crash ao trocar de música)
            val shown = PageContent(
                index,
                shownAt,
                if (scrollMode) listOf(joinLines(shownLines))
                else sheets[shownAt].map { range -> joinLines(if (range.isEmpty()) emptyList() else shownLines.subList(range.first, range.last + 1)) },
                if (scrollMode) listOf(0) else sheets[shownAt].map { range -> if (range.isEmpty()) -1 else range.first },
                style,
                columns,
                // acorde: negrito, na cor e no tamanho dele, acompanhando o zoom da letra
                style.copy(color = chordLook.color, fontSize = style.fontSize * chordLook.scale, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified),
            )
            val chordPx = with(LocalDensity.current) { shown.chordStyle.fontSize.toPx() }
            SideEffect { hit.show(shown, columnWidth, gutter, chordPx) }
            // rolar: a letra sobe até o passo atual; mudou o passo (dentro da música), a linha onde se continua acende
            val scrollY = remember(index) { androidx.compose.animation.core.Animatable(0f) }
            val syncing = remember(index) { BooleanArray(1) }   // a pausa da rolagem automática acerta o passo sem rolar
            LaunchedEffect(index, stopAt, steps) {
                if (syncing[0]) { syncing[0] = false; return@LaunchedEffect }
                val target = steps.getOrNull(stopAt)?.offset?.toFloat() ?: 0f
                if (page == LAST_PAGE) scrollY.snapTo(target) else scrollY.animateTo(target, tween(PAGE_IN_MS * 2))
            }
            // rolagem automática: sobe sozinha na velocidade dos ajustes até o fim; pausada, o passo vira o da posição atual
            // (o próximo toque continua dali, sem voltar)
            val autoRan = remember(index) { BooleanArray(1) }
            val maxScroll = steps.lastOrNull()?.offset ?: 0
            val autoPx = with(density) { (look.autoScroll * com.juliano.stagebook.data.model.Look.AUTO_SCROLL_DP_PER_LEVEL).dp.toPx() }
            LaunchedEffect(autoOn, maxScroll, autoPx) {
                if (!autoOn) {
                    if (autoRan[0]) {
                        autoRan[0] = false
                        // pausou: a posição real vira um passo, e o próximo pedal continua exatamente dali
                        val at = scrollY.value.roundToInt()
                        anchor = at
                        val k = com.juliano.stagebook.domain.ScrollSteps.steps(measured, height, at).indexOfFirst { it.offset >= at }.coerceAtLeast(0)
                        if (k != stopAt) { syncing[0] = true; onPosition(index, k) }
                    }
                    return@LaunchedEffect
                }
                autoRan[0] = true
                var last = androidx.compose.runtime.withFrameNanos { it }
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    val v = (scrollY.value + autoPx * (now - last) / 1_000_000_000f).coerceAtMost(maxScroll.toFloat())
                    last = now
                    scrollY.snapTo(v)
                    if (v >= maxScroll) { autoOn = false; break }
                }
            }
            var flash by remember(index) { mutableStateOf<IntRange?>(null) }   // y..y+altura da linha acesa, na tela
            LaunchedEffect(index, stopAt) {
                if (stopAt <= 0) { flash = null; return@LaunchedEffect }
                flash = if (scrollMode) steps.getOrNull(stopAt)?.let { it.markY until it.markY + it.markHeight }
                else sheets.getOrNull(stopAt)?.firstOrNull()?.let { range ->
                    // virando página (T14): a primeira linha com texto da página nova
                    val i = range.firstOrNull { !measured[it].blank } ?: return@let null
                    val top = (range.first until i).sumOf { measured[it].height }
                    top until top + measured[i].height
                }
                delay(FLASH_MS)
                flash = null
            }
            AnimatedContent(
                modifier = Modifier.graphicsLayer {
                    // enquanto a pinça não para, a página atual só escala (a partir do canto de cima)
                    val pinchScale = minOf(zoomState.floatValue, fit) / minOf(layoutZoom, fit)
                    scaleX = pinchScale
                    scaleY = pinchScale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = dragX
                },
                targetState = shown,
                // texto refeito pelo zoom não é virada de página: troca sem animar
                contentKey = { it.song to it.page },
                transitionSpec = {
                    // vira a página: a nova entra pelo lado do movimento
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
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            ChordText(
                                text,
                                style = content.style,
                                chordStyle = content.chordStyle,
                                modifier = if (!scrollMode) Modifier.fillMaxSize()
                                else Modifier.fillMaxWidth().clipToBounds().wrapContentHeight(Alignment.Top, unbounded = true)
                                    .offset { IntOffset(0, -scrollY.value.roundToInt()) },
                                onTextLayout = { laid = it; hit.laidOut(content, column, it) },
                            )
                            // a lista de acordes, por cima do espaço reservado para ela
                            val l = laid
                            if (strip != null && l != null && strip.start < l.layoutInput.text.length) {
                                val top = l.getLineTop(l.getLineForOffset(strip.start)).roundToInt()
                                ChordStrip(
                                    shownText,
                                    look.instrument,
                                    Color(look.chordColor),
                                    Modifier.offset { IntOffset(0, top) },
                                    onHide = { hidingChord = it },
                                    onRestore = { onHiddenChords(song.id, emptySet()) },
                                )
                            }
                        }
                    }
                    // última folha com só a coluna da esquerda: a da direita fica vazia, não estica a outra
                    repeat(content.sheetColumns - content.columns.size) { Box(Modifier.weight(1f)) }
                }
            }
            // contagem do metrônomo (1 2 3 4) bem grande no meio, antes de começar
            if (metroOn && look.metroCount && metroBeat in 0 until METRO_COUNT) {
                Text(
                    (metroBeat + 1).toString(),
                    // grande o bastante para a banda ver do outro lado do palco
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 200.sp, lineHeight = 200.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // a linha onde se continua, acesa por um instante (T14), na largura da coluna da esquerda
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
            // player (só no modo player): play/pause transparente no canto de baixo à direita, por cima da letra
            if (look.playerMode && !song.missing) {
                TrackButton(
                    hasTrack = song.track.isNotEmpty(),
                    playing = playing,
                    fg = fg,
                    onClick = { if (song.track.isEmpty()) onPickTrack(song.id) else onTogglePlay() },
                    onChange = { onPickTrack(song.id) },
                    onRemove = { onRemoveTrack(song.id) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s).onGloballyPositioned { trackButton = it.boundsInParent() },
                )
            }
            ZoomBadge(
                visible = zoomTouched,
                percent = { (minOf(zoomState.floatValue, fit) * 100).roundToInt() },
                onReset = { zoom = 1f.coerceIn(minZoom, maxZoom); zoomTouched = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            editOffer?.let { offer ->
                EditLineButton(
                    onClick = { editOffer = null; editAt(offer.line) },
                    modifier = Modifier.layout { measurable, c ->
                        // em cima do dedo (embaixo, se não couber), sem sair da tela
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

        // rodapé: hora à esquerda; à direita as bolinhas das páginas e, na última página, a próxima música
        // (ou o fim do setlist) no lugar delas. As bolinhas vieram do topo e substituem o "Continua →", que
        // repetia o que elas já mostram (pedido do usuário, 23/10)
        val nextTitle = uiState.songs.getOrNull(index + 1)?.let { if (it.missing) stringResource(R.string.set_item_missing) else it.title }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageClock(fg.copy(alpha = 0.7f))
            // rolar: ▶/⏸ da rolagem automática e a velocidade (−/+), na linha do relógio (fora da letra)
            // um ícone só (aceso enquanto rola); a janelinha tem o play e a velocidade (pedido do usuário, 29/10)
            // metrônomo: tocando, o ícone vira stop e pisca no tempo (mais forte no 1); parado, abre a janelinha
            run {
                var metroDialog by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { if (metroOn) metroOn = false else metroDialog = true }) {
                        if (metroOn) Icon(AppIcons.Stop, stringResource(R.string.metro_stop), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * metroPulse.value))
                        else Icon(AppIcons.Metronome, stringResource(R.string.metro_title), tint = fg.copy(alpha = 0.7f))
                    }
                    if (metroDialog) MetronomeDialog(
                        bpm = metroBpm,
                        onBpm = { metroBpm = it.coerceIn(Chords.MIN_TEMPO, Chords.MAX_TEMPO) },
                        onPlay = { metroOn = true; metroDialog = false },
                        onDismiss = { metroDialog = false },
                    )
                }
            }
            if (autoAvailable) {
                var autoDialog by remember { mutableStateOf(false) }
                // a janelinha abre colada acima do ícone (pedido do usuário, 29/10)
                Box {
                    // rolando: o ícone vira pause e para na hora (pedido do usuário, 29/10); parado, abre a janelinha
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
                        uiState.songs.size > 1 -> stringResource(R.string.stage_end)
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
    // brilho do metrônomo na borda, esmaecendo para dentro, numa camada própria por cima: cada piscada redesenha só ela
    // (a letra fica guardada como está), e nada é desenhado fora das piscadas
    Spacer(
        Modifier
            .matchParentSize()
            .graphicsLayer()
            .drawBehind {
                val a = metroPulse.value
                if (a <= 0f || !look.metroGlow) return@drawBehind
                val c = metroColor.copy(alpha = a * METRO_GLOW_ALPHA)
                val clear = metroColor.copy(alpha = 0f)
                val g = glowPx.coerceAtMost(size.minDimension / 2)
                drawRect(Brush.verticalGradient(listOf(c, clear), 0f, g), size = androidx.compose.ui.geometry.Size(size.width, g))
                drawRect(Brush.verticalGradient(listOf(clear, c), size.height - g, size.height), topLeft = Offset(0f, size.height - g), size = androidx.compose.ui.geometry.Size(size.width, g))
                drawRect(Brush.horizontalGradient(listOf(c, clear), 0f, g), size = androidx.compose.ui.geometry.Size(g, size.height))
                drawRect(Brush.horizontalGradient(listOf(clear, c), size.width - g, size.width), topLeft = Offset(size.width - g, 0f), size = androidx.compose.ui.geometry.Size(g, size.height))
            },
    )
    }
}

/** "Zoom desta música: 120%" enquanto ajusta, com atalho para voltar ao tamanho padrão. */
@Composable
private fun ZoomBadge(visible: Boolean, percent: () -> Int, onReset: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        // lido aqui dentro: durante a pinça só o selo recompõe
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
 * Largura (px) de cada texto numa linha só, medidos todos de uma vez: no Fire, centenas de
 * medições separadas na troca de música davam um engasgo visível.
 */
/**
 * Altura de cada linha da letra na largura [width], numa medição só do texto inteiro (medir linha a
 * linha montava um layout por linha: dezenas na troca de música, o caro no Fire). A altura de uma
 * linha é a das linhas desenhadas que ela ocupa (quebras inclusas); bate com a medição separada porque
 * a altura de linha é fixa (LineHeightStyle.Trim.None). Linha vazia mede como um espaço.
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
        Pagination.Line(ceil(r.getLineBottom(last) - r.getLineTop(first)).toInt(), l.isEmpty())
    }
}

/**
 * Linhas com acordes que quebram na largura [width] viram um pedaço por linha da tela (medidas numa passada
 * só, como o resto). Pedaço sem acorde perde o espaço de cima e vira uma linha comum. Sem quebra, devolve a
 * mesma lista (o palco reaproveita a medição já feita).
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

/** Tablatura larga demais para a coluna: a letra de máquina diminui até caber (nunca quebra, senão as colunas se perdem). */
internal fun fitTabs(measurer: TextMeasurer, lines: List<StageLine>, style: TextStyle, width: Int): List<StageLine> {
    if (lines.none { it.text.hasStringAnnotations(TAB_TAG, 0, it.text.length) }) return lines
    // o bloco inteiro fica do mesmo tamanho (o da linha mais larga), para as cordas continuarem alinhadas entre si
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

/** O que o detector de gesto precisa ler sempre atualizado (ele não reinicia a cada página). */
private class GestureTargets(
    val next: () -> Unit,
    val previous: () -> Unit,
    val minZoom: Float,
    val maxZoom: Float,
    val hold: (Offset) -> Unit,
    val dismiss: () -> Boolean,   // fecha o botão de editar; true se ele estava aberto
    val chord: (Offset) -> Boolean,   // tocou num acorde: abre o desenho; true se abriu
)

/** `starts`: índice em `lines` da 1ª linha de cada coluna (-1 = coluna vazia). */
// leva também o estilo e as colunas da folha: a página que sai na animação não é remontada (nem muda de
// tamanho) com os da música nova
private const val FLASH_MS = 1500L
private const val METRO_FLASH_MS = 180
private const val KEYBOARD_HINT_MS = 6_000L
private val METRO_GLOW = 20.dp          // brilho da borda, esmaecendo para dentro (não chega na letra)
private val METRO_GLOW_ACCENT = 26.dp   // um pouco mais largo no 1 do compasso
private const val METRO_GLOW_ALPHA = 0.8f
private const val METRO_COUNT = 4   // a contagem é de um compasso de 4
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

/** Botão "editar esta linha" oferecido onde o dedo segurou; `line` é o índice em `lines`. */
private class EditOffer(val at: Offset, val line: Int)

/** Acha a linha embaixo do dedo pelo texto já desenhado na página (quebras de linha inclusas). */
private class PageHit {
    private var content: PageContent? = null
    private var columnWidth = 0
    private var gutter = 0
    private val layouts = arrayOfNulls<TextLayoutResult>(2)

    private var chordPx = 0f

    fun show(c: PageContent, columnWidth: Int, gutter: Int, chordPx: Float) {
        if (c != content) { content = c; layouts.fill(null) }
        this.columnWidth = columnWidth
        this.gutter = gutter
        this.chordPx = chordPx
    }

    fun laidOut(c: PageContent, column: Int, r: TextLayoutResult) { if (c == content && column < layouts.size) layouts[column] = r }

    /** Índice em `lines` da 1ª linha da página na tela. */
    fun firstLine(): Int? = content?.starts?.firstOrNull { it >= 0 }

    /**
     * O acorde no ponto `p`, ou null: os desenhados em cima da letra (marca [CHORD_TAG], na faixa de cima da linha)
     * e os das linhas só de acordes (a palavra tocada, se for acorde).
     */
    fun chordAt(p: Offset): String? {
        val c = content ?: return null
        val column = if (c.columns.size > 1 && p.x >= columnWidth + gutter / 2f) 1 else 0
        val layout = layouts[column] ?: return null
        val x = p.x - if (column == 1) columnWidth + gutter else 0
        if (p.y < 0 || p.y > layout.size.height) return null
        val line = layout.getLineForVerticalPosition(p.y)
        val text = layout.layoutInput.text
        val top = layout.getLineTop(line)
        val band = chordPx * 1.5f
        if (p.y <= top + band) {
            val tolerance = chordPx * 0.3f
            text.getStringAnnotations(CHORD_TAG, layout.getLineStart(line), layout.getLineEnd(line)).forEach { a ->
                val x0 = layout.getHorizontalPosition(a.start, true)
                if (x >= x0 - tolerance && x <= x0 + a.item.length * chordPx * 0.7f + tolerance) return a.item
            }
        }
        // linha só de acordes: a palavra embaixo do dedo
        val lineText = text.text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        if (Chords.isChordLine(lineText)) {
            val o = layout.getOffsetForPosition(Offset(x, p.y))
            val s = text.text
            var a = o.coerceIn(0, s.length)
            var b = a
            while (a > 0 && !s[a - 1].isWhitespace()) a--
            while (b < s.length && !s[b].isWhitespace()) b++
            s.substring(a, b).takeIf(Chords::isChord)?.let { return it }
        }
        return null
    }

    /** Índice em `lines` da linha no ponto `p`, ou null fora do texto. */
    fun lineAt(p: Offset): Int? {
        val c = content ?: return null
        val column = if (c.columns.size > 1 && p.x >= columnWidth + gutter / 2f) 1 else 0
        val layout = layouts[column] ?: return null
        val start = c.starts[column]
        if (start < 0 || p.y > layout.size.height) return null
        // só a altura importa: a linha inteira é a mesma, em qualquer ponto dela
        val offset = layout.getLineStart(layout.getLineForVerticalPosition(p.y))
        val text = layout.layoutInput.text
        val line = text.getStringAnnotations(LINE_TAG, offset, minOf(offset + 1, text.length)).firstOrNull()?.item?.toInt() ?: return null
        return start + line
    }
}

/**
 * Play/pause da faixa: círculo translúcido, para não esconder a letra. Sem faixa, o toque escolhe o arquivo; com
 * faixa, o toque longo oferece trocar ou tirar a faixa.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackButton(
    hasTrack: Boolean,
    playing: Boolean,
    fg: Color,
    onClick: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
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
                when { !hasTrack -> AppIcons.QueueMusic; playing -> AppIcons.Pause; else -> AppIcons.Play },
                stringResource(when { !hasTrack -> R.string.stage_track_pick; playing -> R.string.stage_pause; else -> R.string.stage_play }),
                tint = fg.copy(alpha = if (hasTrack) 0.85f else 0.5f),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.stage_track_change)) }, leadingIcon = { Icon(AppIcons.QueueMusic, null) }, onClick = { menu = false; onChange() })
            DropdownMenuItem(text = { Text(stringResource(R.string.stage_track_remove)) }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = { menu = false; onRemove() })
        }
    }
}

/** Tom ({key}) e capotraste ({capo}) da música no topo do palco; nada se a música não tem nenhum dos dois. */
/** Metrônomo (janelinha acima do ícone): o BPM com − e + e o play, que fecha a janela; o resto fica em Ajustes. */
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
                Text(stringResource(R.string.metro_in_settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onPlay, modifier = Modifier.size(Sizes.touch + Spacing.m)) {
                    Icon(AppIcons.PlayRounded, stringResource(R.string.metro_play), Modifier.size(Sizes.touch), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Põe a janelinha em cima do ícone, alinhada à esquerda dele, sem sair da tela. */
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

/** Rolagem automática (janelinha logo acima do ícone): velocidade de 1 a 10 e o play/pausa, que fecha a janela. */
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
                // título e o × para fechar (o play também fecha)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.stage_auto_title), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.action_close)) }
                }
                Text(stringResource(R.string.stage_auto_speed, level), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    IconButton(onClick = { onLevel(level - 1) }, enabled = level > 1) { Icon(AppIcons.Remove, stringResource(R.string.stage_auto_slower)) }
                    // play arredondado e grande, na cor de destaque e sem fundo (pedido do usuário, 29/10); o alvo de toque
                    // continua grande
                    IconButton(onClick = onToggle, modifier = Modifier.size(Sizes.touch + Spacing.m)) {
                        Icon(
                            if (on) AppIcons.PauseRounded else AppIcons.PlayRounded,
                            stringResource(if (on) R.string.stage_auto_pause else R.string.stage_auto_play),
                            Modifier.size(Sizes.touch),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onLevel(level + 1) }, enabled = level < com.juliano.stagebook.data.model.Look.MAX_AUTO_SCROLL) {
                        Icon(AppIcons.Add, stringResource(R.string.stage_auto_faster))
                    }
                }
                Text(stringResource(R.string.stage_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SongKeyBadges(text: String, fg: Color, showCapo: Boolean = true, keyboardCapo: Int = 0) {
    val key = remember(text) { Chords.key(text) }
    // teclado não usa capotraste (pedido do usuário, 29/10): no palco dele o capo não aparece
    val capo = remember(text, showCapo) { if (showCapo) Chords.capo(text) else 0 }
    if (key.isEmpty() && capo == 0 && keyboardCapo == 0) return
    val sounds = Chords.soundingKey(key, capo)
    val keyDesc = stringResource(R.string.stage_key_desc, key)
    val capoDesc = if (key.isNotEmpty()) stringResource(R.string.stage_capo_sounds_desc, capo, sounds) else stringResource(R.string.stage_capo_desc, capo)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        // o capo vem antes do tom e aceso: muda o que se toca, não pode passar despercebido (pedido do usuário, 29/10)
        if (capo > 0) {
            val accent = MaterialTheme.colorScheme.primary
            Row(Modifier.clearAndSetSemantics { contentDescription = capoDesc }, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Capo, null, tint = accent)
                Text(capo.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accent)
                // o tom que soa com o capo, à vista (os desenhos continuam os da cifra)
                if (key.isNotEmpty()) Text(" " + stringResource(R.string.stage_capo_sounds_short, sounds), style = MaterialTheme.typography.titleSmall, color = accent)
            }
        }
        if (key.isNotEmpty()) {
            Row(Modifier.clearAndSetSemantics { contentDescription = keyDesc }, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.MusicNote, null, tint = fg.copy(alpha = 0.6f))
                Text(key, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg.copy(alpha = 0.8f))
            }
        }
        // teclado com a cifra transposta pelo capo: diz por que o tom é outro
        if (keyboardCapo > 0) {
            Text(stringResource(R.string.stage_keyboard_capo, keyboardCapo), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A música tem backing track: o primeiro ícone depois do artista, aceso na cor de destaque. */
@Composable
private fun TrackBadge(modifier: Modifier = Modifier) {
    Icon(AppIcons.QueueMusic, stringResource(R.string.stage_backing_track), modifier, tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun EditLineButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
        Row(
            Modifier.heightIn(min = Sizes.touch).padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(AppIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(stringResource(R.string.stage_edit_line), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Uma bolinha por página, com o número dela (pedido do usuário, 23/10): a atual preenchida e acesa, as
 * outras só com o número apagado. Some quando a música cabe numa página só. Mais de [PAGE_SLOTS]
 * páginas: a paginação do Google ([PageSlots]), primeira, atual com as vizinhas, "…" e última; igual no
 * celular e no tablet (pedido do usuário, 23/10).
 */
@Composable
private fun PageDots(current: Int, count: Int, color: Color, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            PageSlots.of(current, count, PAGE_SLOTS).forEach { i ->
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
                    )
                }
            }
        }
    }
}

/** Hora atual no formato do aparelho, atualizada na virada de cada minuto. */
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

/** O palco fica sempre aceso (as barras do sistema o app já esconde em todas as telas). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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

/** Apertos do pedal/toque esperando a posição pedida aparecer (ver "pé rápido" no StageScreen). */
private class PressQueue {
    var from: Pair<Int, Int>? = null   // posição de onde saiu o aperto ainda não assentado
    var steps = 0                      // apertos que chegaram nesse meio tempo (+ avança, - volta)
}
