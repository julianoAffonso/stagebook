package com.juliano.stagebook.ui.stage

import androidx.compose.foundation.layout.offset
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
    var index by rememberSaveable { mutableIntStateOf(viewModel.start) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    KeepScreenOn()
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
            onHiddenChords = viewModel::setHiddenChords,
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
    val sourced = remember(song.text, look, sectionNames) { stageLines(song.text, sectionNames, Color(look.sectionColor), look.showSections, chordLook) }
    val lines = remember(sourced) { sourced.map { it.text } }
    // segurar uma linha mostra um botão para editá-la (não abre direto: no show o polegar apoiado
    // na tela não pode tirar a letra da frente); some sozinho e ao virar a página
    val hit = remember(index) { PageHit() }
    var editOffer by remember(index) { mutableStateOf<EditOffer?>(null) }
    LaunchedEffect(editOffer) { if (editOffer != null) { delay(EDIT_OFFER_MS); editOffer = null } }
    val haptics = LocalHapticFeedback.current
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
    fun go(forward: Boolean) {
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
                PedalAction.NONE -> Unit
            }
        }
        true   // o "soltar" também é consumido (alguns pedais mandam só ele para teclas de mídia)
    }

    Column(
        modifier
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
            // tom e capo da música (ícone + valor, o texto inteiro só para leitores de tela); no celular vão
            // para a linha do artista, senão o título encurta
            if (!song.missing && metaInline && look.chords) SongKeyBadges(song.text, fg)
            if (!song.missing) {
                IconButton(onClick = { editAt(hit.firstLine()) }) {
                    Icon(AppIcons.Edit, stringResource(R.string.stage_edit), tint = fg.copy(alpha = 0.6f))
                }
            }
        }
        // tom e capo são de cifra: no perfil só letra não aparecem
        val hasKey = look.chords && remember(song.text) { Chords.key(song.text).isNotEmpty() || Chords.capo(song.text) > 0 }
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
                if (!song.missing && hasKey) SongKeyBadges(song.text, fg)
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
            val columns = remember(lines, styleOne, width, height, look.columns, layoutZoom, fitTwo) {
                if (look.columns != StageColumns.AUTO) StageFit.columns(look.columns, false, emptyList(), halfWidth, true)
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
            val stripChordCount = remember(song.text) { stripChords(song.text).size }
            val stripPlace = if (look.chords && stripChordCount > 0) look.chordList else ChordListPlace.NONE
            val stripEm = with(density) {
                val perRow = maxOf(1, columnWidth / STRIP_ITEM_WIDTH.roundToPx())
                val rows = (stripChordCount + perRow - 1) / perRow
                (rows * STRIP_ITEM_HEIGHT.toPx() + Spacing.m.toPx()) / style.fontSize.toPx()
            }
            val fitted = remember(sourced, style, columnWidth, stripPlace, stripEm) {
                val split = splitWrappedChordLines(measurer, sourced, style, columnWidth)
                val strip = StageLine(chordListLine(stripEm), -1)
                when (stripPlace) {
                    ChordListPlace.TOP -> listOf(strip) + split
                    ChordListPlace.BOTTOM -> split + strip
                    ChordListPlace.NONE -> split
                }
            }
            val shownLines = remember(fitted) { if (fitted === sourced) lines else fitted.map { it.text } }
            SideEffect { pageLines = fitted }
            val sheets = remember(shownLines, style, columnWidth, height, columns) {
                val measured = if (shownLines === lines && style == styleOne && columnWidth == width) oneLines.value else lineHeights(measurer, shownLines, style, columnWidth)
                Pagination.sheets(measured, height, columns)
            }
            SideEffect { pageCount = sheets.size; pageCountFor = index }
            val shownAt = if (page == LAST_PAGE) sheets.lastIndex else page.coerceIn(0, sheets.lastIndex)
            // cada página da animação leva o próprio texto: a que está saindo não pode ser montada com
            // as linhas da música nova (era o crash ao trocar de música)
            val shown = PageContent(
                index,
                shownAt,
                sheets[shownAt].map { range -> joinLines(if (range.isEmpty()) emptyList() else shownLines.subList(range.first, range.last + 1)) },
                sheets[shownAt].map { range -> if (range.isEmpty()) -1 else range.first },
                style,
                columns,
                // acorde: negrito, na cor e no tamanho dele, acompanhando o zoom da letra
                style.copy(color = chordLook.color, fontSize = style.fontSize * chordLook.scale, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified),
            )
            val chordPx = with(LocalDensity.current) { shown.chordStyle.fontSize.toPx() }
            SideEffect { hit.show(shown, columnWidth, gutter, chordPx) }
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
                                modifier = Modifier.fillMaxSize(),
                                onTextLayout = { laid = it; hit.laidOut(content, column, it) },
                            )
                            // a lista de acordes, por cima do espaço reservado para ela
                            val l = laid
                            if (strip != null && l != null && strip.start < l.layoutInput.text.length) {
                                val top = l.getLineTop(l.getLineForOffset(strip.start)).roundToInt()
                                ChordStrip(
                                    song.text,
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

/** Tom ({key}) e capotraste ({capo}) da música no topo do palco; nada se a música não tem nenhum dos dois. */
@Composable
private fun SongKeyBadges(text: String, fg: Color) {
    val key = remember(text) { Chords.key(text) }
    val capo = remember(text) { Chords.capo(text) }
    if (key.isEmpty() && capo == 0) return
    val sounds = Chords.soundingKey(key, capo)
    val keyDesc = stringResource(R.string.stage_key_desc, key)
    val capoDesc = if (key.isNotEmpty()) stringResource(R.string.stage_capo_sounds_desc, capo, sounds) else stringResource(R.string.stage_capo_desc, capo)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        if (key.isNotEmpty()) {
            Row(Modifier.clearAndSetSemantics { contentDescription = keyDesc }, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.MusicNote, null, tint = fg.copy(alpha = 0.6f))
                Text(key, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg.copy(alpha = 0.8f))
            }
        }
        if (capo > 0) {
            Row(Modifier.clearAndSetSemantics { contentDescription = capoDesc }, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Capo, null, tint = fg.copy(alpha = 0.6f))
                Text(capo.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg.copy(alpha = 0.8f))
            }
        }
    }
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
