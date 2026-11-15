package br.app.stagebook.ui.stage

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import br.app.stagebook.domain.PdfZoom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.withContext
import java.io.File

/** The PDF of a song on the stage, while it opens, once it is open, and when it can't be (the file is gone or damaged). */
sealed interface PdfOpen {
    data object Loading : PdfOpen
    data object Failed : PdfOpen
    class Ready(val pages: PdfPages) : PdfOpen
}

/** Opens [file] off the main thread and closes it when the song leaves the stage. [open] is replaced by a fake in tests. */
@Composable
fun rememberPdfOpen(file: File, open: (File) -> PdfSource = ::AndroidPdfSource): State<PdfOpen> {
    val state = produceState<PdfOpen>(PdfOpen.Loading, file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfOpen.Ready(PdfPages(open(file))) }.getOrDefault(PdfOpen.Failed) }
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
 * there are, which is what the stage turns through (the pedal, the tap and the swipe are the stage's own).
 */
@Composable
fun PdfStageView(
    open: PdfOpen,
    page: Int,
    invert: Boolean,
    onPageCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (open) {
        PdfOpen.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        PdfOpen.Failed -> {
            Box(modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.stage_pdf_unreadable), style = MaterialTheme.typography.titleMedium) }
        }
        is PdfOpen.Ready -> {
            val pages = open.pages
            LaunchedEffect(pages) { onPageCount(pages.pageCount.coerceAtLeast(1)) }
            val at = page.coerceIn(0, (pages.pageCount - 1).coerceAtLeast(0))
            BoxWithConstraints(modifier.fillMaxSize(), Alignment.Center) {
                val widthPx = constraints.maxWidth
                val heightPx = constraints.maxHeight
                val density = LocalDensity.current
                // the zoom and where the page was moved to: back to the whole page on every page (turning is not zoomed)
                var scale by remember(pages, at) { mutableFloatStateOf(1f) }
                var offset by remember(pages, at) { mutableStateOf(Offset.Zero) }
                // the page's size on the screen: as large as it fits, drawn once and kept while the zoom changes the sharpness
                var fit by remember(pages, at, widthPx, heightPx) { mutableStateOf<Pair<Int, Int>?>(null) }
                val maxScale = fit?.let { PdfZoom.maxScale(it.first, it.second) } ?: 1.5f
                val bucket = PdfZoom.bucket(scale, maxScale)
                var drawn by remember(pages, at) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(pages, at, widthPx, heightPx, bucket) {
                    val (pw, ph) = pages.size(at)
                    val factor = minOf(widthPx / pw.toFloat(), heightPx / ph.toFloat())
                    val fitW = (pw * factor).toInt().coerceAtLeast(1)
                    val fitH = (ph * factor).toInt().coerceAtLeast(1)
                    fit = fitW to fitH
                    // the sharper one replaces the page on screen only once it is drawn
                    drawn = pages.bitmap(at, fitW * bucket).asImageBitmap()
                    // the next page is drawn ahead, so the turn shows it at once
                    if (bucket == 1 && at + 1 < pages.pageCount) pages.bitmap(at + 1, fitW)
                }
                val fitNow by rememberUpdatedState(fit)
                val maxNow by rememberUpdatedState(maxScale)
                fun moved(by: Offset): Offset {
                    val f = fitNow ?: return Offset.Zero
                    val (x, y) = PdfZoom.clamp((offset.x + by.x) to (offset.y + by.y), scale, f.first.toFloat(), f.second.toFloat(), widthPx.toFloat(), heightPx.toFloat())
                    return Offset(x, y)
                }
                val size = fit
                val image = drawn
                if (size == null || image == null) CircularProgressIndicator()
                else with(density) {
                    Image(
                        image, contentDescription = stringResource(R.string.stage_pdf_page, at + 1, pages.pageCount),
                        modifier = Modifier
                            .size(size.first.toDp(), size.second.toDp())
                            // taken here before the stage's own gestures: two fingers zoom and move the page, and with the page
                            // enlarged one finger moves it; at its size a tap and a swipe still turn the page
                            .pointerInput(pages, at) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var travelled = Offset.Zero
                                    var pinching = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        if (event.changes.count { it.pressed } >= 2) {
                                            pinching = true
                                            scale = PdfZoom.scaled(scale, event.calculateZoom(), maxNow)
                                            offset = moved(pan)
                                            event.changes.forEach { it.consume() }
                                        } else if (!pinching && scale > 1f) {
                                            travelled += pan
                                            if (travelled.getDistance() > viewConfiguration.touchSlop) {
                                                offset = moved(pan)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (scale < 1.02f) { scale = 1f; offset = Offset.Zero }
                                }
                            }
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                            .background(Color.White),
                        colorFilter = if (invert) InvertFilter else null,
                    )
                }
            }
        }
    }
}
