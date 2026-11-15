package br.app.stagebook.ui.stage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
                // the page is drawn at the width it takes on the screen: fitting the height when it is the taller proportion
                val drawn by produceState<Pair<ImageBitmap, Pair<Int, Int>>?>(null, pages, at, widthPx, heightPx) {
                    val (pw, ph) = pages.size(at)
                    val scale = minOf(widthPx / pw.toFloat(), heightPx / ph.toFloat())
                    val fitW = (pw * scale).toInt().coerceAtLeast(1)
                    val fitH = (ph * scale).toInt().coerceAtLeast(1)
                    value = pages.bitmap(at, fitW).asImageBitmap() to (fitW to fitH)
                    // the next page is drawn ahead, so the turn shows it at once
                    if (at + 1 < pages.pageCount) pages.bitmap(at + 1, fitW)
                }
                drawn?.let { (image, size) ->
                    with(density) {
                        Image(
                            image, contentDescription = stringResource(R.string.stage_pdf_page, at + 1, pages.pageCount),
                            modifier = Modifier.size(size.first.toDp(), size.second.toDp()).background(Color.White),
                            colorFilter = if (invert) InvertFilter else null,
                        )
                    }
                } ?: CircularProgressIndicator()
            }
        }
    }
}
