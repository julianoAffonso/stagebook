package br.app.stagebook.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.data.model.PdfPoint
import br.app.stagebook.domain.PdfMarks
import br.app.stagebook.domain.PdfCrops
import br.app.stagebook.data.model.PdfCrop
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.app.stagebook.domain.PdfZoom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R
import br.app.stagebook.data.AndroidPdfSource
import br.app.stagebook.data.PdfPages
import br.app.stagebook.data.PdfSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/** The PDF of a song on the stage, while it opens, once it is open, and when it can't be (the file is gone or damaged). */
sealed interface PdfOpen {
    data object Loading : PdfOpen
    data object Failed : PdfOpen
    class Ready(val pages: PdfPages) : PdfOpen
}

/**
 * Opens [file] off the main thread and closes it when the song leaves the stage. The state starts again for each file (a
 * state that went on from the song before showed a PDF already closed), and a PDF opened for a song that left meanwhile is
 * closed too. [open] is replaced by a fake in tests.
 */
@Composable
fun rememberPdfOpen(file: File, open: (File) -> PdfSource = ::AndroidPdfSource): State<PdfOpen> {
    val state = remember(file) { mutableStateOf<PdfOpen>(PdfOpen.Loading) }
    LaunchedEffect(file) {
        val made = java.util.concurrent.atomic.AtomicReference<PdfPages?>()
        try {
            // the opening itself isn't interrupted: it ends, and what it opened is closed if nobody wants it any more
            withContext(Dispatchers.IO) { made.set(runCatching { PdfPages(open(file)) }.getOrNull()) }
            state.value = made.get()?.let { PdfOpen.Ready(it) } ?: PdfOpen.Failed
        } finally {
            if (!isActive) made.get()?.close()
        }
    }
    DisposableEffect(file) { onDispose { (state.value as? PdfOpen.Ready)?.pages?.close() } }
    return state
}

/** The colours turned over: white paper becomes black, for a dark stage. */
internal val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

/**
 * The page [page] of an open PDF, whole on the screen, as large as it fits and centred. [onPageCount] says how many pages
 * there are, which is what the stage turns through (the pedal, the tap and the swipe are the stage's own). Two fingers
 * zoom and move the page; one finger never moves it (it turns the page, or draws when marking). With [marking] on, one finger draws the highlighter: [marks] of
 * this page are drawn over it in [markColor], and a stroke made comes out through [onStroke] when the finger lifts.
 */
@Composable
fun PdfStageView(
    open: PdfOpen,
    page: Int,
    invert: Boolean,
    onPageCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
    marks: List<PdfMark> = emptyList(),
    marking: Boolean = false,
    markColor: Color = Color(0xFFFFEB3B),
    onStroke: (page: Int, points: List<PdfPoint>) -> Unit = { _, _ -> },
    view: br.app.stagebook.domain.PdfView = br.app.stagebook.domain.PdfView(),
    onView: (br.app.stagebook.domain.PdfView) -> Unit = {},
    crops: List<br.app.stagebook.data.model.PdfCrop> = emptyList(),   // the trims of its pages: only that part shows, enlarged
) {
    when (open) {
        PdfOpen.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        PdfOpen.Failed -> {
            LaunchedEffect(Unit) { onPageCount(1) }   // the count of the song before must not stay
            Box(modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.stage_pdf_unreadable), style = MaterialTheme.typography.titleMedium) }
        }
        is PdfOpen.Ready -> {
            val pages = open.pages
            LaunchedEffect(pages) { onPageCount(pages.pageCount.coerceAtLeast(1)) }
            val at = page.coerceIn(0, (pages.pageCount - 1).coerceAtLeast(0))
            val crop = PdfCrops.forPage(crops, at)
            val cropW = crop?.let { it.right - it.left } ?: 1f
            val cropH = crop?.let { it.bottom - it.top } ?: 1f
            BoxWithConstraints(modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth
                val heightPx = constraints.maxHeight
                val density = LocalDensity.current
                // the zoom and where the page was moved to: kept with the song, and every page opens as the last one was left
                var scale by remember(pages) { mutableFloatStateOf(view.scale.coerceAtLeast(1f)) }
                var offset by remember(pages) { mutableStateOf(Offset(view.panX * widthPx, view.panY * heightPx)) }
                // the stroke under the finger, before it is kept
                var live by remember(pages, at) { mutableStateOf(emptyList<PdfPoint>()) }
                // the page's size on the screen: as large as it fits, drawn once and kept while the zoom changes the sharpness
                var fit by remember(pages, at, widthPx, heightPx, crop) { mutableStateOf<Pair<Int, Int>?>(null) }
                // the pixels of the whole page, drawn once, are what sets how far it may be enlarged: a trimmed page needs more of them
                val maxScale = fit?.let { PdfZoom.maxScale((it.first / cropW).toInt(), (it.second / cropH).toInt()) } ?: 1.5f
                val bucket = PdfZoom.bucket(scale, maxScale)
                var drawn by remember(pages, at, crop) { mutableStateOf<ImageBitmap?>(null) }
                // a page that can't be drawn (a damaged one, no room for its bitmap) says so, and doesn't close the app
                var broken by remember(pages, at) { mutableStateOf(false) }
                LaunchedEffect(pages, at, widthPx, heightPx, bucket, crop) {
                    try {
                        val (pw, ph) = pages.size(at)
                        // the part shown (the whole page, or its trim) takes the box as far as it fits
                        val factor = minOf(widthPx / (pw * cropW), heightPx / (ph * cropH))
                        val fitW = (pw * cropW * factor).toInt().coerceAtLeast(1)
                        val fitH = (ph * cropH * factor).toInt().coerceAtLeast(1)
                        fit = fitW to fitH
                        // the whole page is drawn wide enough for its trim to come out as wide as the box
                        val wholeW = (fitW / cropW).toInt().coerceAtLeast(1)
                        // the sharper one replaces the page on screen only once it is drawn
                        drawn = pages.bitmap(at, wholeW * bucket).asImageBitmap()
                        // the next page is drawn ahead, so the turn shows it at once
                        if (bucket == 1 && at + 1 < pages.pageCount && crops.isEmpty()) pages.bitmap(at + 1, wholeW)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        broken = true
                    } catch (e: OutOfMemoryError) {
                        broken = true
                    }
                }
                val fitNow by rememberUpdatedState(fit)
                val maxNow by rememberUpdatedState(maxScale)
                val onStrokeNow by rememberUpdatedState(onStroke)
                fun moved(by: Offset): Offset {
                    val f = fitNow ?: return Offset.Zero
                    val (x, y) = PdfZoom.clamp((offset.x + by.x) to (offset.y + by.y), scale, f.first.toFloat(), f.second.toFloat(), widthPx.toFloat(), heightPx.toFloat())
                    return Offset(x, y)
                }
                fun onPage(p: Offset): PdfPoint? {
                    val f = fitNow ?: return null
                    // a point of the part shown, taken to a point of the whole page: the strokes belong to the page
                    return PdfMarks.onPage(p.x, p.y, widthPx.toFloat(), heightPx.toFloat(), f.first.toFloat(), f.second.toFloat(), scale, offset.x, offset.y)
                        ?.let { PdfCrops.toPage(crop, it) }
                }
                val size = fit
                val image = drawn
                Box(
                    Modifier
                        .fillMaxSize()
                        // over the whole area, not only the page: an enlarged page reaches past its own layout. Taken here before
                        // the stage's own gestures: two fingers zoom and move the page; one finger draws the highlighter, or
                        // with the page enlarged moves it; at its size a tap and a swipe still turn the page
                        .pointerInput(pages, at, marking) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var pinching = false
                                val stroke = ArrayList<PdfPoint>()
                                if (marking) {
                                    // taken at once: the stage's own handler then leaves this finger alone (no page turn on a dot)
                                    down.consume()
                                    onPage(down.position)?.let { stroke += it; live = stroke.toList() }
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        if (!pinching) { stroke.clear(); live = emptyList() }   // a second finger: it is a zoom, not a stroke
                                        pinching = true
                                        scale = PdfZoom.scaled(scale, event.calculateZoom(), maxNow)
                                        offset = moved(pan)
                                        event.changes.forEach { it.consume() }
                                    } else if (marking && !pinching) {
                                        event.changes.forEach { change ->
                                            onPage(change.position)?.let { stroke += it }
                                            change.consume()
                                        }
                                        live = stroke.toList()
                                    }
                                } while (event.changes.any { it.pressed })
                                if (marking && !pinching && stroke.isNotEmpty()) onStrokeNow(at, stroke.toList())
                                live = emptyList()
                                if (scale < 1.02f) { scale = 1f; offset = Offset.Zero }
                                onView(br.app.stagebook.domain.PdfView(scale, offset.x / widthPx, offset.y / heightPx))
                            }
                        },
                    Alignment.Center,
                ) {
                    if (broken) Text(stringResource(R.string.stage_pdf_unreadable), style = MaterialTheme.typography.titleMedium)
                    else if (size == null || image == null) CircularProgressIndicator()
                    else with(density) {
                        // the page and its strokes move and grow together
                        Box(
                            Modifier
                                .size(size.first.toDp(), size.second.toDp())
                                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                        ) {
                            val pageLabel = stringResource(R.string.stage_pdf_page, at + 1, pages.pageCount)
                            if (crop == null) Image(
                                image, contentDescription = pageLabel,
                                modifier = Modifier.fillMaxSize().background(Color.White),
                                colorFilter = if (invert) InvertFilter else null,
                            ) else Canvas(Modifier.fillMaxSize().background(Color.White).semantics { contentDescription = pageLabel }) {
                                // only the trimmed part of the whole page, stretched over the box (they share one shape)
                                drawImage(
                                    image,
                                    srcOffset = androidx.compose.ui.unit.IntOffset((crop.left * image.width).toInt(), (crop.top * image.height).toInt()),
                                    srcSize = androidx.compose.ui.unit.IntSize((cropW * image.width).toInt().coerceAtLeast(1), (cropH * image.height).toInt().coerceAtLeast(1)),
                                    dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt()),
                                    colorFilter = if (invert) InvertFilter else null,
                                )
                            }
                            // over the paper and not turned over with it: the highlighter keeps its colour on a dark stage
                            // the strokes of this page, as paths made once and drawn on every frame of a zoom or a move
                            val strokes = remember(marks, at) { marks.filter { it.page == at } }
                            Canvas(Modifier.fillMaxSize()) {
                                strokes.forEach { drawStroke(it.points, it.width, markColor, crop) }
                                if (live.isNotEmpty()) drawStroke(live, PdfMarks.WIDTH, markColor, crop)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A highlighter stroke over the page: [points] are fractions of the page, [width] a fraction of its width; [crop], if any, is the part shown. */
private fun DrawScope.drawStroke(points: List<PdfPoint>, width: Float, color: Color, crop: PdfCrop? = null) {
    if (points.isEmpty()) return
    val cw = crop?.let { it.right - it.left } ?: 1f
    val ch = crop?.let { it.bottom - it.top } ?: 1f
    val x0 = crop?.left ?: 0f
    val y0 = crop?.top ?: 0f
    fun px(p: PdfPoint) = Offset((p.x - x0) / cw * size.width, (p.y - y0) / ch * size.height)
    val thick = width / cw * size.width
    val paint = color.copy(alpha = 0.4f)
    if (points.size == 1) {
        drawCircle(paint, thick / 2f, px(points[0]))
        return
    }
    val path = Path().apply {
        val first = px(points[0])
        moveTo(first.x, first.y)
        points.drop(1).forEach { val q = px(it); lineTo(q.x, q.y) }
    }
    drawPath(path, paint, style = Stroke(width = thick, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
