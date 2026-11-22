package br.app.stagebook.ui.stage

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.app.stagebook.data.model.ExternalIdle
import br.app.stagebook.data.model.ExternalShow
import androidx.compose.ui.graphics.asImageBitmap
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.ui.theme.Spacing
import java.io.File

/**
 * What the stage has on screen, for the external screen (user, 22/11): the song, and the part of it in view: its song
 * lines [lines] (null: from the top), or the PDF's file page [pdfPage].
 */
@Immutable
data class MirrorFrame(
    val songId: String,
    val title: String,
    val meta: String = "",
    val text: String = "",
    val lines: IntRange? = null,
    val pdf: String = "",
    val pdfPage: Int = 0,
    val pdfInvert: Boolean = false,
    val pdfMarks: List<PdfMark> = emptyList(),
    val pdfCrops: List<PdfCrop> = emptyList(),
)

/**
 * Where the stage leaves what it shows; read by the external screen. [active] while an external screen shows: only then
 * the stage writes [frame], and with the stage closed it is null (the screen shows its idle picture).
 */
class StageMirror {
    var frame by mutableStateOf<MirrorFrame?>(null)
    var active by mutableStateOf(false)
    var mirrorTablet by mutableStateOf(false)   // the stage scrolls: the screen is let go and Android mirrors the tablet
}

/** The app's one mirror, from the root: the stage writes it, the external screen reads it. */
val LocalStageMirror = androidx.compose.runtime.staticCompositionLocalOf<StageMirror?> { null }

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * The first screen Android offers for a presentation (HDMI, wireless display), while [enabled]; null when there is none.
 * Listens to screens coming and going only while enabled: nothing runs with the option off (the Fire is the rule).
 */
@Composable
fun rememberPresentationDisplay(enabled: Boolean): Display? {
    val context = LocalContext.current
    var display by remember { mutableStateOf<Display?>(null) }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(DisplayManager::class.java)
        if (!enabled || manager == null) { display = null; return@DisposableEffect onDispose {} }
        fun pick() { display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull() }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = pick()
            override fun onDisplayRemoved(id: Int) = pick()
            override fun onDisplayChanged(id: Int) = pick()
        }
        pick()
        manager.registerDisplayListener(listener, null)
        onDispose { manager.unregisterDisplayListener(listener); display = null }
    }
    return display
}

/** [content] on [display], as an Android presentation, while it is in the composition. */
@Composable
fun ExternalPresentation(display: Display, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(content)
    DisposableEffect(display) {
        val activity = context.activity() ?: return@DisposableEffect onDispose {}
        val presentation = Presentation(activity, display)
        val view = ComposeView(presentation.context).apply { setContent { latest.value() } }
        presentation.setContentView(view)
        presentation.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
        }
        // the screen may go away between being found and shown
        runCatching { presentation.show() }
        onDispose { runCatching { presentation.dismiss() } }
    }
}

/**
 * The external screen's picture of [frame], as [look] says: the lines the stage has in view, as large as they fit, or the
 * PDF's page. A PDF song in a text mode, or a page with no song lines (the chord list), shows the song's name.
 */
@Composable
fun ExternalStageContent(frame: MirrorFrame?, look: Look, pdfFile: (String) -> File, modifier: Modifier = Modifier, idleImage: File? = null) {
    if (frame == null) { ExternalIdleContent(look.externalIdle, idleImage, modifier); return }
    val bg = Color(look.bg)
    val fg = Color(look.fg)
    Box(modifier.fillMaxSize().background(bg).testTag(EXTERNAL_TAG), Alignment.Center) {
        if (frame.pdf.isNotEmpty()) {
            if (look.externalShow == ExternalShow.PDF) {
                val open by rememberPdfOpen(pdfFile(frame.pdf))
                PdfStageView(
                    open, frame.pdfPage, frame.pdfInvert, onPageCount = {}, marks = frame.pdfMarks, markColor = Color(look.highlightColor),
                    crops = frame.pdfCrops, pageKey = frame.songId,
                )
            } else TitleCard(frame, fg)
            return@Box
        }
        val names = rememberSectionNames(look.translateSections)
        val chords = ChordLook(look.externalShow != ExternalShow.LYRICS, look.chordScale, Color(look.chordColor))
        val text = remember(frame.text, frame.lines, names, chords, look.sectionColor, look.showSections, look.sectionScale, look.sectionFont, look.highlightColor) {
            val all = stageLines(frame.text, names, Color(look.sectionColor), look.showSections, chords, look.sectionScale, Color(look.highlightColor), look.sectionFont.family)
            val shown = frame.lines?.let { r -> all.filter { it.source in r } } ?: all
            shown.map { it.text }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        }
        if (text.isEmpty()) { TitleCard(frame, fg); return@Box }
        BoxWithConstraints(Modifier.fillMaxSize().padding(Spacing.l)) {
            val measurer = rememberTextMeasurer(cacheSize = 0)
            val joined = remember(text) { joinLines(text) }
            val density = LocalDensity.current
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val size = remember(joined, w, h, look.bold, look.font, chords.scale) {
                with(density) { fitFontPx(measurer, joined, w, h) { px -> externalStyle(px.toSp(), fg, look) }.toSp() }
            }
            val style = externalStyle(size, fg, look)
            ChordText(
                joined, style,
                style.copy(color = chords.color, fontSize = style.fontSize * chords.scale, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified),
                Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * The external screen with the stage closed (user, 22/11): the app's logo on black (the default), black, or the person's
 * image, whole and centred. An image that is gone or can't be read leaves the screen black.
 */
@Composable
fun ExternalIdleContent(idle: ExternalIdle, image: File?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black).testTag(EXTERNAL_IDLE_TAG), Alignment.Center) {
        when (idle) {
            ExternalIdle.LOGO -> br.app.stagebook.ui.components.AppLogo(size = androidx.compose.ui.unit.Dp(LOGO_DP))
            ExternalIdle.BLACK -> Unit
            ExternalIdle.IMAGE -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val picture by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image, image?.lastModified(), w, h) {
                    value = image?.takeIf { it.exists() }?.let { f -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sampled(f, w, h) } }
                }
                picture?.let {
                    androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize().testTag(EXTERNAL_IMAGE_TAG), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
        }
    }
}

// read at about the screen's size: a phone photo at full size would take tens of MB for a TV that shows 2
private fun sampled(f: File, w: Int, h: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(f.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= w.coerceAtLeast(1) && bounds.outHeight / (sample * 2) >= h.coerceAtLeast(1)) sample *= 2
    android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
private fun TitleCard(frame: MirrorFrame, fg: Color) {
    Column(Modifier.padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(frame.title, color = fg, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (frame.meta.isNotBlank()) Text(frame.meta, color = fg.copy(alpha = 0.7f), fontSize = 36.sp, textAlign = TextAlign.Center)
    }
}

private fun externalStyle(size: TextUnit, fg: Color, look: Look) = TextStyle(
    color = fg,
    fontSize = size,
    lineHeight = size * 1.25f,
    fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = look.font.family,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    hyphens = Hyphens.None,
)

/**
 * The largest font, in pixels, at which [text] fits [width] × [height] with no line wrapped. Lines are never wrapped on the
 * external screen: chords stay over their syllables and the part reads as on the stage.
 */
internal fun fitFontPx(measurer: TextMeasurer, text: AnnotatedString, width: Int, height: Int, style: (Float) -> TextStyle): Float {
    if (width <= 0 || height <= 0) return MIN_EXTERNAL_PX
    fun fits(px: Float): Boolean {
        val r = measurer.measure(text, style(px), softWrap = false, constraints = Constraints())
        // the chords drawn above a letter can reach a little past the line's end
        return r.size.width * CHORD_SLACK <= width && r.size.height <= height
    }
    var lo = MIN_EXTERNAL_PX
    var hi = MAX_EXTERNAL_PX
    if (!fits(lo)) return lo
    repeat(FIT_STEPS) {
        val mid = (lo + hi) / 2
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

const val EXTERNAL_TAG = "external_screen"
const val EXTERNAL_IDLE_TAG = "external_idle"
const val EXTERNAL_IMAGE_TAG = "external_image"
private const val LOGO_DP = 240f
private const val MIN_EXTERNAL_PX = 12f
private const val MAX_EXTERNAL_PX = 400f
private const val FIT_STEPS = 9
private const val CHORD_SLACK = 1.04f
