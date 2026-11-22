package br.app.stagebook.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import br.app.stagebook.R
import java.io.File
import java.io.IOException

/** Why a PDF was refused: shown to the person, in words (see [text]). */
enum class PdfProblem {
    NOT_PDF,          // doesn't start with %PDF-
    TOO_BIG,          // over PdfInspector.MAX_BYTES
    PASSWORD,         // protected: the app doesn't ask for passwords
    TOO_MANY_PAGES,   // over PdfInspector.MAX_PAGES
    UNREADABLE,       // damaged, or Android can't open it
    EXPIRED,          // sent over the internet, but gone from the server (it keeps PDFs a few hours) or not reachable
}

/** The verdict on a PDF file: what it is, or the reason it won't do. */
sealed interface PdfCheck {
    data class Ok(val pages: Int, val bytes: Long) : PdfCheck
    data class Refused(val problem: PdfProblem) : PdfCheck
}

/** Where a PDF went when attached: the saved file, or why it was refused (and nothing was kept). */
sealed interface PdfAttach {
    data class Saved(val name: String, val pages: Int, val bytes: Long) : PdfAttach
    data class Refused(val problem: PdfProblem) : PdfAttach
}

/**
 * A thin layer over Android's [PdfRenderer], only to count the pages: it throws [SecurityException] for a PDF with a
 * password and [IOException] for one it can't read, which is all [PdfInspector] needs. A fake stands in for it in tests
 * (Robolectric doesn't draw PDFs).
 */
interface PdfPageCounter {
    @Throws(IOException::class, SecurityException::class)
    fun pageCount(file: File): Int
}

class AndroidPdfPageCounter : PdfPageCounter {
    override fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                // the first page opens too: a damaged page tree gives a page count and fails here, not later on the stage
                r.pageCount.also { if (it > 0) r.openPage(0).close() }
            }
        }
}

/**
 * The check every PDF passes before the app keeps it, whether the person chose it or the band sent it: the `%PDF-`
 * header, at most [MAX_BYTES], opens in [PdfRenderer], has no password and at most [MAX_PAGES] pages. The renderer
 * doesn't run the PDF's scripts.
 */
class PdfInspector(private val counter: PdfPageCounter) {
    @javax.inject.Inject constructor() : this(AndroidPdfPageCounter())

    fun check(file: File): PdfCheck {
        val head = ByteArray(5)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        if (read < head.size || !PdfStore.looksLikePdf(head)) return PdfCheck.Refused(PdfProblem.NOT_PDF)
        val bytes = file.length()
        if (bytes > MAX_BYTES) return PdfCheck.Refused(PdfProblem.TOO_BIG)
        val pages = try {
            counter.pageCount(file)
        } catch (e: SecurityException) {
            return PdfCheck.Refused(PdfProblem.PASSWORD)
        } catch (e: Exception) {
            // damaged, a renderer that gives up (it also throws IllegalStateException, IllegalArgumentException): not a crash
            return PdfCheck.Refused(PdfProblem.UNREADABLE)
        }
        return when {
            pages <= 0 -> PdfCheck.Refused(PdfProblem.UNREADABLE)
            pages > MAX_PAGES -> PdfCheck.Refused(PdfProblem.TOO_MANY_PAGES)
            else -> PdfCheck.Ok(pages, bytes)
        }
    }

    companion object {
        const val MAX_BYTES = PdfStore.MAX_BYTES
        const val MAX_PAGES = 300
    }
}

/** The sentence that tells the person why the PDF was refused. */
fun PdfProblem.text(context: Context): String = when (this) {
    PdfProblem.NOT_PDF -> context.getString(R.string.pdf_refused_not_pdf)
    PdfProblem.TOO_BIG -> context.getString(R.string.pdf_refused_too_big, (PdfInspector.MAX_BYTES / (1024 * 1024)).toInt())
    PdfProblem.PASSWORD -> context.getString(R.string.pdf_refused_password)
    PdfProblem.TOO_MANY_PAGES -> context.getString(R.string.pdf_refused_too_many_pages, PdfInspector.MAX_PAGES)
    PdfProblem.UNREADABLE -> context.getString(R.string.pdf_refused_unreadable)
    PdfProblem.EXPIRED -> context.getString(R.string.pdf_refused_expired, br.app.stagebook.domain.BandContent.INTERNET_PDF_HOURS)
}
