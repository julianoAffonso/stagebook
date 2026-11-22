package br.app.stagebook.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import br.app.stagebook.R
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.domain.PdfCrops
import br.app.stagebook.domain.PdfScroll
import kotlin.math.roundToInt

/**
 * A PDF in the stage's scroll mode (user, 22/11): the pages one under the other, as wide as the screen, each with its
 * trim. [step] is where the pedal, the tap and the swipe have it (the stage turns through [onStepCount] steps, as through
 * pages); [autoOn] scrolls on its own at [autoPxPerSecond] until the end ([onAutoEnd]), and a pause makes the place it
 * stopped the next step ([onPaused]). Only the pages on screen (and one either side) are drawn: the Fire is the rule.
 */
@Composable
fun PdfScrollView(
    open: PdfOpen,
    step: Int,
    invert: Boolean,
    onStepCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
    crops: List<PdfCrop> = emptyList(),
    marks: List<PdfMark> = emptyList(),
    markColor: Color = Color(0xFFFFEB3B),
    autoOn: Boolean = false,
    autoPxPerSecond: Float = 0f,
    onAutoEnd: () -> Unit = {},
    onPaused: (step: Int) -> Unit = {},
    onTopPage: ((page: Int) -> Unit)? = null,   // the page the external screen shows (every page passes there in turn)
    pageKey: Any = Unit,
) {
    when (open) {
        PdfOpen.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        PdfOpen.Failed -> {
            LaunchedEffect(pageKey) { onStepCount(1) }
            Box(modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.stage_pdf_unreadable), style = MaterialTheme.typography.titleMedium) }
        }
        is PdfOpen.Ready -> BoxWithConstraints(modifier.fillMaxSize()) {
            val pages = open.pages
            val width = constraints.maxWidth
            val viewport = constraints.maxHeight
            // each page's shape (the part shown), read once per PDF and trims
            var shapes by remember(pages, crops) { mutableStateOf<List<Pair<Float, Float>>?>(null) }
            LaunchedEffect(pages, crops) {
                shapes = (0 until pages.pageCount).map { p ->
                    val (pw, ph) = pages.size(p)
                    val c = PdfCrops.forPage(crops, p)
                    (pw * (c?.let { it.right - it.left } ?: 1f)) to (ph * (c?.let { it.bottom - it.top } ?: 1f))
                }
            }
            val shown = shapes
            if (shown == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return@BoxWithConstraints }
            val heights = remember(shown, width) { PdfScroll.heights(shown, width) }
            val tops = remember(heights) { PdfScroll.tops(heights) }
            val total = heights.sum()
            var anchor by remember(pages, pageKey) { mutableIntStateOf(0) }
            val steps = remember(total, viewport, anchor) { PdfScroll.steps(total, viewport, anchor) }
            LaunchedEffect(steps.size, pageKey) { onStepCount(steps.size) }
            val scroll = remember(pages, pageKey) { Animatable(0f) }
            val at = step.coerceIn(0, steps.lastIndex)
            val syncing = remember(pages, pageKey) { BooleanArray(1) }   // a pause sets the step without moving
            LaunchedEffect(at, steps) {
                if (syncing[0]) { syncing[0] = false; return@LaunchedEffect }
                scroll.animateTo(steps[at].toFloat(), tween(PDF_SCROLL_MS))
            }
            // the automatic scroll: on at the speed in Settings until the end; paused, the place becomes a step
            val autoRan = remember(pages, pageKey) { BooleanArray(1) }
            val onAutoEndNow by rememberUpdatedState(onAutoEnd)
            val onPausedNow by rememberUpdatedState(onPaused)
            val max = (total - viewport).coerceAtLeast(0).toFloat()
            LaunchedEffect(autoOn, max, autoPxPerSecond) {
                if (!autoOn) {
                    if (autoRan[0]) {
                        autoRan[0] = false
                        val stopAt = scroll.value.roundToInt()
                        anchor = stopAt
                        val k = PdfScroll.steps(total, viewport, stopAt).indexOfFirst { it >= stopAt }.coerceAtLeast(0)
                        syncing[0] = true
                        onPausedNow(k)
                    }
                    return@LaunchedEffect
                }
                autoRan[0] = true
                var last = androidx.compose.runtime.withFrameNanos { it }
                while (true) {
                    val now = androidx.compose.runtime.withFrameNanos { it }
                    val v = (scroll.value + autoPxPerSecond * autoScrollNanos(now - last) / 1_000_000_000f).coerceAtMost(max)
                    last = now
                    scroll.snapTo(v)
                    if (v >= max) { onAutoEndNow(); break }
                }
            }
            val offset = scroll.value.roundToInt()
            val visible = PdfScroll.visible(tops, heights, offset, viewport)
            if (onTopPage != null) {
                // from the top of the view at the start to its bottom at the end: every page passes on the TV, short ones too
                val shownPage = PdfScroll.pageAt(tops, br.app.stagebook.domain.MirrorLines.pdfProbe(offset, viewport, total))
                LaunchedEffect(shownPage, pageKey) { onTopPage(shownPage) }
            }
            // the pictures of the pages in view, drawn off the main thread; the others are let go
            val drawn = remember(pages, width, crops) { mutableStateMapOf<Int, ImageBitmap>() }
            LaunchedEffect(visible, pages, width, crops) {
                drawn.keys.filter { it !in visible }.forEach { drawn.remove(it) }
                for (p in visible) {
                    if (p in drawn) continue
                    val bitmap = runCatching {
                        val (pw, ph) = pages.size(p)
                        pages.bitmap(p, PdfCrops.wholeWidth(pw to ph, PdfCrops.forPage(crops, p), width, viewport)).asImageBitmap()
                    }.getOrNull() ?: continue
                    drawn[p] = bitmap
                }
            }
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = -scroll.value; clip = false }) {
                for (p in visible) {
                    val crop = PdfCrops.forPage(crops, p)
                    val label = stringResource(R.string.stage_pdf_page, p + 1, pages.pageCount)
                    Box(Modifier.offset { IntOffset(0, tops[p]) }.fillMaxWidth().height(with(density) { heights[p].toDp() })) {
                        val image = drawn[p]
                        if (image == null) Box(Modifier.fillMaxSize().background(Color.White))
                        else Canvas(Modifier.fillMaxSize().background(Color.White).semantics { contentDescription = label }) {
                            val cw = crop?.let { it.right - it.left } ?: 1f
                            val ch = crop?.let { it.bottom - it.top } ?: 1f
                            drawImage(
                                image,
                                srcOffset = IntOffset(((crop?.left ?: 0f) * image.width).toInt(), ((crop?.top ?: 0f) * image.height).toInt()),
                                srcSize = IntSize((cw * image.width).toInt().coerceAtLeast(1), (ch * image.height).toInt().coerceAtLeast(1)),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                colorFilter = if (invert) InvertFilter else null,
                            )
                        }
                        // the highlighter strokes of this page, over the paper and not turned over with it
                        val ink = remember(marks, p, crop) { PdfInk(marks.filter { it.page == p }, crop) }
                        Canvas(Modifier.fillMaxSize()) { ink.draw(this, emptyList(), markColor) }
                    }
                }
            }
        }
    }
}

private const val PDF_SCROLL_MS = 450
