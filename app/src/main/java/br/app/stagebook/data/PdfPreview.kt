package br.app.stagebook.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import br.app.stagebook.domain.PdfZoom
import java.io.File

/** One page of a song's PDF as a JPEG, for the editor on the computer (its crop window shows the page it trims). */
object PdfPreview {
    const val WIDTH = 900

    /** Page [page] of [file] drawn [width] pixels wide, or null when it can't be opened or drawn. Opened and closed here. */
    fun jpeg(file: File, page: Int, width: Int = WIDTH, open: (File) -> PdfSource = ::AndroidPdfSource): ByteArray? = runCatching {
        // one page drawn at a time: the computer's page can ask for several at once, and each is megabytes on a device that may
        // be on stage
        one.acquire()
        val pages = try { PdfPages(open(file)) } catch (e: Throwable) { one.release(); throw e }
        try {
            val n = page.coerceIn(0, (pages.pageCount - 1).coerceAtLeast(0))
            // held to the pixels a device can take: a thin tall page (a forged one) must not ask for a huge bitmap
            val (pw, ph) = kotlinx.coroutines.runBlocking { pages.size(n) }
            val bitmap = kotlinx.coroutines.runBlocking { pages.bitmap(n, PdfZoom.capWidth(width, pw, ph)) }
            // the picture is this call's alone (the PDF was opened here): its memory goes back at once, not when the collector runs
            try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() } finally { bitmap.recycle() }
        } finally {
            pages.close()
            one.release()
        }
    }.getOrNull()

    private val one = java.util.concurrent.Semaphore(1)
}
