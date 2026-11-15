package br.app.stagebook.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A PDF opened for reading its pages on the stage: how many, how big each one is and a page drawn at a width. */
interface PdfSource {
    val pageCount: Int
    /** The page's own size (any unit: only the proportion counts). */
    fun pageSize(page: Int): Pair<Int, Int>
    fun render(page: Int, width: Int): Bitmap
    fun close()
}

/** [PdfSource] over Android's [PdfRenderer]. It keeps one page open at a time (the renderer allows no more) and is closed when the stage leaves the song. */
class AndroidPdfSource(file: File) : PdfSource {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(page: Int): Pair<Int, Int> = renderer.openPage(page).use { it.width to it.height }

    override fun render(page: Int, width: Int): Bitmap = renderer.openPage(page).use { p ->
        val w = width.coerceAtLeast(1)
        val h = (w.toLong() * p.height / p.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)   // the renderer draws on transparent: a PDF page is white paper
        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    override fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }
}

/**
 * The pages of one PDF as bitmaps for the stage: drawn off the main thread, one at a time (the renderer allows no more),
 * and at most [MAX_KEPT] kept (the one on screen, the next and one back): a Fire HD 10 has no room for a whole book.
 * Closed when the song leaves the stage.
 */
class PdfPages(private val source: PdfSource) {
    private val lock = Mutex()
    private val kept = LinkedHashMap<Pair<Int, Int>, Bitmap>()   // (page, width) → the page, oldest first
    @Volatile private var closed = false

    /** Read once, when it opened: asking a closed PDF for its pages would throw (a screen that still holds an old one). */
    val pageCount: Int = source.pageCount

    suspend fun size(page: Int): Pair<Int, Int> = lock.withLock {
        if (closed) throw CancellationException("the PDF was closed")
        withContext(Dispatchers.IO) { source.pageSize(page) }
    }

    /** The page drawn at [width] pixels, from the kept ones when it was drawn already. */
    suspend fun bitmap(page: Int, width: Int): Bitmap = lock.withLock {
        if (closed) throw CancellationException("the PDF was closed")
        val key = page to width
        kept.remove(key)?.let { kept[key] = it; return@withLock it }   // seen again: the newest
        val drawn = withContext(Dispatchers.IO) { source.render(page, width) }
        kept[key] = drawn
        while (kept.size > MAX_KEPT) kept.remove(kept.keys.first())
        drawn
    }

    /** How many pages are kept now (for the tests). */
    val keptCount: Int get() = kept.size

    /**
     * The file is closed once a page being drawn (the renderer holds one at a time, off the main thread) is done: closing
     * it under that page threw, and left the renderer open. Asking for a page after this is a cancellation, not a crash.
     */
    fun close() {
        closed = true
        CoroutineScope(Dispatchers.IO).launch { lock.withLock { kept.clear(); source.close() } }
    }

    companion object {
        const val MAX_KEPT = 3
    }
}
