package br.app.stagebook.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.CornerRadius
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
fun PdfCropDialog(pdf: EditorPdf, onSet: (PdfCrop) -> Unit, onClear: (page: Int) -> Unit, onDismiss: () -> Unit) {
    val pages = pdf.pages.coerceAtLeast(1)
    var page by rememberSaveable { mutableIntStateOf(0) }
    // the scope is "all pages" unless this page has a trim of its own
    var all by rememberSaveable { mutableStateOf(pdf.crops.none { it.page == 0 }) }
    val scope = if (all) PdfCrop.ALL else page
    val current = pdf.crops.firstOrNull { it.page == scope } ?: PdfCrop(scope)
    // the frame being dragged, in fractions of the page; starts from what is kept for this scope
    var left by remember(scope, current) { mutableFloatStateOf(current.left) }
    var top by remember(scope, current) { mutableFloatStateOf(current.top) }
    var right by remember(scope, current) { mutableFloatStateOf(current.right) }
    var bottom by remember(scope, current) { mutableFloatStateOf(current.bottom) }
    val open by pdf.file?.let { rememberPdfOpen(it) } ?: remember { mutableStateOf<PdfOpen>(PdfOpen.Failed) }
    val ready = open as? PdfOpen.Ready
    val image by produceState<ImageBitmap?>(null, ready, page) {
        value = ready?.let { r -> runCatching { r.pages.bitmap(page.coerceIn(0, r.pages.pageCount - 1), 900).asImageBitmap() }.getOrNull() }
    }
    val shape = image?.let { it.width.toFloat() / it.height } ?: PAGE_SHAPE

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                        CropFrame(left, top, right, bottom) { l, t, r, b -> left = l; top = t; right = r; bottom = b }
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
                    ActionButton(stringResource(R.string.action_save), { onSet(PdfCrop(scope, left, top, right, bottom)); onDismiss() }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** The frame over the page: what is outside it is dimmed, and each edge, corner or the inside is dragged. */
@Composable
private fun CropFrame(left: Float, top: Float, right: Float, bottom: Float, onChange: (Float, Float, Float, Float) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val rect = PdfCrop(0, left, top, right, bottom)
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(Unit) {
                var grab = 0
                val slop = 36.dp.toPx()
                var l = 0f; var t = 0f; var r = 1f; var b = 1f
                detectDragGestures(
                    onDragStart = { p ->
                        // which edges the touch is on: left, top, right, bottom (corners are two); the inside moves the frame
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val nearL = abs(p.x - rect.left * w) < slop; val nearR = abs(p.x - rect.right * w) < slop
                        val nearT = abs(p.y - rect.top * h) < slop; val nearB = abs(p.y - rect.bottom * h) < slop
                        val inside = p.x in rect.left * w..rect.right * w && p.y in rect.top * h..rect.bottom * h
                        grab = (if (nearL) 1 else 0) or (if (nearT) 2 else 0) or (if (nearR) 4 else 0) or (if (nearB) 8 else 0)
                        if (grab == 0 && inside) grab = 15
                        l = rect.left; t = rect.top; r = rect.right; b = rect.bottom
                    },
                    onDrag = { change, amount ->
                        if (grab == 0) return@detectDragGestures
                        change.consume()
                        val dx = amount.x / size.width; val dy = amount.y / size.height
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
                        onChange(l, t, r, b)
                    },
                )
            },
    ) {
        val x = left * size.width; val y = top * size.height
        val w = (right - left) * size.width; val h = (bottom - top) * size.height
        // dim everything, then punch the frame out of it
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, Offset(x, y), Size(w, h), CornerRadius(0f), blendMode = BlendMode.Clear)
        drawRect(line, Offset(x, y), Size(w, h), style = Stroke(width = 2.dp.toPx()))
        // handles at the corners and the middles of the sides
        val r = 6.dp.toPx()
        listOf(x to y, x + w to y, x to y + h, x + w to y + h, x + w / 2 to y, x + w / 2 to y + h, x to y + h / 2, x + w to y + h / 2)
            .forEach { (cx, cy) -> drawCircle(line, r, Offset(cx, cy)) }
    }
}

internal const val CROP_FRAME_TAG = "pdf_crop_frame"
private const val PAGE_SHAPE = 2f / 3f   // a page of paper, until the real one is drawn
