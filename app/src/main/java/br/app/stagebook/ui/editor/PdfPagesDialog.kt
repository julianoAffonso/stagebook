package br.app.stagebook.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.app.stagebook.R
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.stage.InvertFilter
import br.app.stagebook.ui.stage.PdfOpen
import br.app.stagebook.data.PdfPages
import br.app.stagebook.ui.stage.rememberPdfOpen
import br.app.stagebook.ui.theme.Spacing

/**
 * Which of the PDF's pages show on the stage (user, 22/11): every page small, and a tap leaves one off the stage or brings it
 * back. The file is not changed; the last page shown can't be hidden.
 */
@Composable
fun PdfPagesDialog(pdf: EditorPdf, onToggle: (page: Int) -> Unit, onDismiss: () -> Unit) {
    val count = pdf.pages.coerceAtLeast(1)
    val open by pdf.file?.let { rememberPdfOpen(it) } ?: remember { mutableStateOf<PdfOpen>(PdfOpen.Failed) }
    val pages = (open as? PdfOpen.Ready)?.pages
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.action_close)) }
                    Text(stringResource(R.string.pdf_pages_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Text(stringResource(R.string.pdf_pages_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = count - pdf.hidden.count { it in 0 until count }
                Text(pluralStringResource(R.plurals.pdf_pages_shown, count, shown, count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(PDF_PAGES_COUNT_TAG))
                LazyVerticalGrid(GridCells.Adaptive(THUMB_MIN), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items((0 until count).toList(), key = { it }) { p ->
                        PageThumb(pages, p, hidden = p in pdf.hidden, invert = pdf.invert) { onToggle(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumb(pages: PdfPages?, page: Int, hidden: Boolean, invert: Boolean, onTap: () -> Unit) {
    val image by br.app.stagebook.ui.stage.rememberPdfPageImage(pages, page, THUMB_WIDTH)
    val state = stringResource(if (hidden) R.string.pdf_page_hidden else R.string.pdf_page_shown)
    val label = stringResource(R.string.pdf_page_label, page + 1)
    Column(
        Modifier.clickable(onClick = onTap).semantics { contentDescription = "$label, $state" }.testTag(pdfPageTag(page)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(PAGE_SHAPE).alpha(if (hidden) 0.3f else 1f).clip(MaterialTheme.shapes.small).background(Color.White)) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = if (invert) InvertFilter else null) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(if (hidden) AppIcons.VisibilityOff else AppIcons.Visibility, null, Modifier.size(18.dp), tint = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

const val PDF_PAGES_COUNT_TAG = "pdf_pages_count"
fun pdfPageTag(page: Int) = "pdf_page_$page"
private val THUMB_MIN = 104.dp
private const val THUMB_WIDTH = 240
private const val PAGE_SHAPE = 0.707f
