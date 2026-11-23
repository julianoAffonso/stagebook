package br.app.stagebook.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import br.app.stagebook.ui.components.SwitchRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.ui.components.ActionButton
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ButtonTone
import br.app.stagebook.ui.components.DarkPreview
import br.app.stagebook.ui.components.LightPreview
import br.app.stagebook.ui.components.PreviewSurface
import br.app.stagebook.ui.theme.Spacing

/** The PDF file icon (Material "picture_as_pdf", Apache-2.0), drawn here like the others; the color comes from the Icon's tint. */
internal val PdfFileIcon: ImageVector by lazy {
    ImageVector.Builder(name = "picture_as_pdf", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(
                "M20,2L8,2c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM11.5,9.5c0,0.83 -0.67,1.5 -1.5,1.5L9,11v2L7.5,13L7.5,7L10,7c0.83,0 1.5,0.67 1.5,1.5v1zM14.5,13L12,13L12,7h2.5c0.83,0 1.5,0.67 1.5,1.5v3c0,0.83 -0.67,1.5 -1.5,1.5zM20.5,8.5L19,8.5v1h1.5L20.5,11L19,11v2h-1.5L17.5,7h3v1.5zM9,9.5h1v-1L9,8.5v1zM4,6L2,6v14c0,1.1 0.9,2 2,2h14v-2L4,20L4,6zM14,11.5h1v-3h-1v3z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
}

/** "320 KB" or "2.4 MB": the file's size for the card. */
internal fun fileSizeText(bytes: Long): String =
    if (bytes < 1024 * 1024) "${(bytes + 1023) / 1024} KB" else String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))

/**
 * What takes the place of the lyrics field when the song is a PDF: the PDF icon, its name, how many pages and how big, the
 * sentence that says it can't be edited here, and the actions to replace or remove it. [pdf] with `lost`: the file isn't on this
 * device (a backup without the files), and the card says to attach it again.
 */
@Composable
fun PdfSongCard(pdf: EditorPdf, onReplace: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier, onInvert: (Boolean) -> Unit = {}, onCrop: () -> Unit = {}, onPages: () -> Unit = {}, onAsText: (Boolean) -> Unit = {}) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(
                    if (pdf.lost) AppIcons.Warning else PdfFileIcon, null, Modifier.size(40.dp),
                    tint = if (pdf.lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(pdf.name.ifEmpty { "PDF" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (pdf.lost) {
                        Text(stringResource(R.string.editor_pdf_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else if (pdf.pages > 0) {
                        Text(
                            pluralStringResource(R.plurals.editor_pdf_facts, pdf.pages, pdf.pages, fileSizeText(pdf.bytes)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // the first page, as it will look on the stage (with the colours turned over, if they are)
            pdf.file?.let { PdfPreview(it, pdf.invert, br.app.stagebook.domain.PdfHidden.visible(pdf.pages.coerceAtLeast(1), pdf.hidden).first()) }
            Text(stringResource(R.string.editor_pdf_note), style = MaterialTheme.typography.bodyMedium)
            // the colours of this song's PDF: white paper black, for a dark stage. Here, and not on the stage's header
            if (!pdf.lost) SwitchRow(stringResource(R.string.stage_pdf_invert), pdf.invert, onInvert, Modifier.fillMaxWidth())
            // the lyrics and chords the song kept, on the stage in place of the PDF: offered only when there are some
            if (pdf.keptLyrics) SwitchRow(stringResource(R.string.editor_pdf_as_text), pdf.asText, onAsText, Modifier.fillMaxWidth(), detail = stringResource(R.string.editor_pdf_as_text_detail))
            // which pages show on the stage (user, 22/11): only with more than one
            if (!pdf.lost && pdf.pages > 1) ActionButton(
                stringResource(R.string.pdf_pages_title) + " · " + stringResource(R.string.pdf_pages_of, pdf.pages - pdf.hidden.count { it in 0 until pdf.pages }, pdf.pages),
                onPages, Modifier.fillMaxWidth(), icon = AppIcons.Visibility, tone = ButtonTone.Tonal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (!pdf.lost) ActionButton(stringResource(R.string.editor_pdf_crop), onCrop, Modifier.weight(1f), tone = ButtonTone.Tonal)
                ActionButton(stringResource(R.string.editor_pdf_replace), onReplace, Modifier.weight(1f), tone = ButtonTone.Tonal)
                ActionButton(stringResource(R.string.editor_pdf_remove), onRemove, Modifier.weight(1f), tone = ButtonTone.Quiet)
            }
        }
    }
}

@LightPreview
@Composable
private fun PdfSongCardLightPreview() = PreviewSurface(dark = false) {
    PdfSongCard(EditorPdf("Chart - Canção do Vento.pdf", pages = 3, bytes = 412_000), onReplace = {}, onRemove = {}, modifier = Modifier.padding(Spacing.m))
}

@DarkPreview
@Composable
private fun PdfSongCardDarkPreview() = PreviewSurface(dark = true) {
    PdfSongCard(EditorPdf("Chart - Canção do Vento.pdf", lost = true), onReplace = {}, onRemove = {}, modifier = Modifier.padding(Spacing.m))
}

/** The PDF's first page, small, in the card: drawn off the main thread, closed with the card. */
@Composable
private fun PdfPreview(file: java.io.File, invert: Boolean, page: Int = 0, modifier: Modifier = Modifier) {
    val open by br.app.stagebook.ui.stage.rememberPdfOpen(file)
    val ready = open as? br.app.stagebook.ui.stage.PdfOpen.Ready
    val image by br.app.stagebook.ui.stage.rememberPdfPageImage(ready?.pages, page, PREVIEW_WIDTH)
    Box(modifier.fillMaxWidth().heightIn(max = PREVIEW_MAX_HEIGHT), contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) {
            Image(
                shown, stringResource(R.string.editor_pdf_preview), Modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).background(Color.White),
                contentScale = ContentScale.Fit, colorFilter = if (invert) br.app.stagebook.ui.stage.InvertFilter else null,
            )
        } else if (open is br.app.stagebook.ui.stage.PdfOpen.Loading) CircularProgressIndicator()
    }
}

private const val PREVIEW_WIDTH = 480
private val PREVIEW_MAX_HEIGHT = 320.dp
