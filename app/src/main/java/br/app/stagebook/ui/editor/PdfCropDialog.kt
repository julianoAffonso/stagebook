package br.app.stagebook.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.app.stagebook.R
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.domain.PdfCrops
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.Choice
import br.app.stagebook.ui.components.SegmentedChoice
import br.app.stagebook.ui.stage.InvertFilter
import br.app.stagebook.ui.stage.PdfOpen
import br.app.stagebook.ui.stage.rememberPdfOpen
import br.app.stagebook.ui.theme.Spacing
import kotlin.math.abs

/**
 * The trim of a PDF (user, 15/11): its page with a frame whose edges are dragged, for the same trim on every page or for
 * this page only. The stage then shows just that part, as large as the screen allows. The file is not changed, and the
 * trim stays on this device (the band gets the whole PDF).
 */
@Composable
fun PdfCropDialog(
    pdf: EditorPdf, onSet: (PdfCrop) -> Unit, onClear: (page: Int) -> Unit,
    onComposed: () -> Unit = {},   // called each time the window's body composes (the tests count that a drag makes none)
    onDismiss: () -> Unit,
) {
    val pages = pdf.pages.coerceAtLeast(1)
    var page by rememberSaveable { mutableIntStateOf(0) }
    // the scope is "all pages" unless this page has a trim of its own
    var all by rememberSaveable { mutableStateOf(pdf.crops.none { it.page == 0 }) }
    // a page with a trim of its own shows that trim (it is the one the stage uses), not the one for all pages (audit 16/11)
    androidx.compose.runtime.LaunchedEffect(page) { if (pdf.crops.any { it.page == page }) all = false }
    val scope = if (all) PdfCrop.ALL else page
    val current = pdf.crops.firstOrNull { it.page == scope } ?: PdfCrop(scope)
    // the frame being dragged, in fractions of the page; starts from what is kept for this scope
    val frame = remember(scope, current) { CropFrameState(current) }
    val open by pdf.file?.let { rememberPdfOpen(it) } ?: remember { mutableStateOf<PdfOpen>(PdfOpen.Failed) }
    val ready = open as? PdfOpen.Ready
    val image by br.app.stagebook.ui.stage.rememberPdfPageImage(ready?.pages, page, CROP_PAGE_WIDTH)
    val shape = image?.let { it.width.toFloat() / it.height } ?: PAGE_SHAPE

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            onComposed()
            Column(Modifier.systemBarsPadding().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.action_cancel)) }
                    Text(stringResource(R.string.pdf_crop_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Text(stringResource(R.string.pdf_crop_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(shape).fillMaxWidth().widthIn(max = 900.dp).testTag(CROP_FRAME_TAG)) {
                        val shown = image
                        if (shown != null) {
                            Image(
                                shown, stringResource(R.string.editor_pdf_preview), Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).background(Color.White),
                                contentScale = ContentScale.FillBounds, colorFilter = if (pdf.invert) InvertFilter else null,
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                                if (open is PdfOpen.Loading) CircularProgressIndicator()
                            }
                        }
                        CropFrame(frame)
                    }
                }
                if (pages > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Icon(AppIcons.ChevronLeft, stringResource(R.string.pdf_crop_previous)) }
                    Text("${page + 1} / $pages", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { page = (page + 1).coerceAtMost(pages - 1) }, enabled = page < pages - 1) { Icon(AppIcons.ChevronRight, stringResource(R.string.pdf_crop_next)) }
                }
                if (pages > 1) SegmentedChoice(
                    listOf(Choice(true, R.string.pdf_crop_all), Choice(false, R.string.pdf_crop_page)), all, { all = it },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ActionButton(stringResource(R.string.pdf_crop_none), { onClear(scope); onDismiss() }, Modifier.weight(1f), tone = ButtonTone.Quiet)
                    ActionButton(stringResource(R.string.action_save), { onSet(PdfCrop(scope, frame.left, frame.top, frame.right, frame.bottom)); onDismiss() }, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The frame over the page: what is outside it is dimmed, and each edge, corner or the inside is dragged. The drawing is a
 * little larger than the page, all round, so the handles on its edge are whole and not cut by the page's border.
 */
@Composable
private fun CropFrame(frame: CropFrameState) {
    val line = MaterialTheme.colorScheme.primary
    // what the drag reads must be the frame as it is now (and of the scope chosen now), not as it was when the gesture
    // detector was made (the other edges went back to where they started on every new drag)
    val now by rememberUpdatedState(frame)
    Canvas(
        Modifier
            .layout { measurable, c ->
                val room = HANDLE_ROOM.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(c.maxWidth + 2 * room, c.maxHeight + 2 * room))
                layout(c.maxWidth, c.maxHeight) { placeable.place(-room, -room) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(Unit) {
                val slop = 36.dp.toPx()
                val room = HANDLE_ROOM.toPx()
                awaitEachGesture {
                    // taken from where the finger went down (not from where it first moved: a quick drag would have left the edge)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width - 2 * room; val h = size.height - 2 * room
                    val x = down.position.x - room; val y = down.position.y - room
                    val f = now
                    // an edge counts only along its own length (plus the slack): a touch in the empty beside a tall frame is not on it
                    val inX = x >= f.left * w - slop && x <= f.right * w + slop
                    val inY = y >= f.top * h - slop && y <= f.bottom * h + slop
                    val nearL = inY && abs(x - f.left * w) < slop; val nearR = inY && abs(x - f.right * w) < slop
                    val nearT = inX && abs(y - f.top * h) < slop; val nearB = inX && abs(y - f.bottom * h) < slop
                    val inside = x in f.left * w..f.right * w && y in f.top * h..f.bottom * h
                    // which edges the touch is on (a corner is two); the inside moves the whole frame
                    var grab = (if (nearL) 1 else 0) or (if (nearT) 2 else 0) or (if (nearR) 4 else 0) or (if (nearB) 8 else 0)
                    if (grab == 0 && inside) grab = 15
                    if (grab == 0) return@awaitEachGesture
                    var l = f.left; var t = f.top; var r = f.right; var b = f.bottom
                    down.consume()
                    drag(down.id) { change ->
                        val delta = change.position - change.previousPosition
                        change.consume()
                        val dx = delta.x / w; val dy = delta.y / h
                        if (grab == 15) {
                            // the whole frame, kept inside the page
                            val mx = dx.coerceIn(-l, 1f - r); val my = dy.coerceIn(-t, 1f - b)
                            l += mx; r += mx; t += my; b += my
                        } else {
                            if (grab and 1 != 0) l = (l + dx).coerceIn(0f, r - PdfCrops.MIN_SIDE)
                            if (grab and 4 != 0) r = (r + dx).coerceIn(l + PdfCrops.MIN_SIDE, 1f)
                            if (grab and 2 != 0) t = (t + dy).coerceIn(0f, b - PdfCrops.MIN_SIDE)
                            if (grab and 8 != 0) b = (b + dy).coerceIn(t + PdfCrops.MIN_SIDE, 1f)
                        }
                        f.left = l; f.top = t; f.right = r; f.bottom = b
                    }
                }
            },
    ) {
        val room = HANDLE_ROOM.toPx()
        val pw = size.width - 2 * room; val ph = size.height - 2 * room
        // read here, where it is drawn: a drag draws the frame again and composes nothing
        val x = room + frame.left * pw; val y = room + frame.top * ph
        val w = (frame.right - frame.left) * pw; val h = (frame.bottom - frame.top) * ph
        // dim the page (not the margin), then punch the frame out of it
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(room, room), Size(pw, ph))
        drawRect(Color.Transparent, Offset(x, y), Size(w, h), blendMode = BlendMode.Clear)
        drawRect(line, Offset(x, y), Size(w, h), style = Stroke(width = 2.dp.toPx()))
        // handles at the corners and the middles of the sides
        val radius = 6.dp.toPx()
        listOf(x to y, x + w to y, x to y + h, x + w to y + h, x + w / 2 to y, x + w / 2 to y + h, x to y + h / 2, x + w to y + h / 2)
            .forEach { (cx, cy) -> drawCircle(line, radius, Offset(cx, cy)) }
    }
}

/**
 * The frame being dragged, in fractions of the page. It is read where it is drawn and when it is saved, never while the
 * window composes: every event of a drag used to compose the whole window again, the page's picture and the buttons too
 * (audit 16/11).
 */
@Stable
private class CropFrameState(from: PdfCrop) {
    var left by mutableFloatStateOf(from.left)
    var top by mutableFloatStateOf(from.top)
    var right by mutableFloatStateOf(from.right)
    var bottom by mutableFloatStateOf(from.bottom)
}

/** The room around the page for the handles on its edge. */
private val HANDLE_ROOM = 12.dp

internal const val CROP_FRAME_TAG = "pdf_crop_frame"
private const val PAGE_SHAPE = 2f / 3f   // a page of paper, until the real one is drawn
private const val CROP_PAGE_WIDTH = 900
