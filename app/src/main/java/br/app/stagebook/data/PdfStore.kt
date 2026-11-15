package br.app.stagebook.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF files of the songs that are a PDF: the chosen file is **copied into the app** under a generated name (never the
 * origin's name), like [TrackStore] does with the audio. Here only the cheap checks (the `%PDF-` header and the size);
 * the page count, the password and the rest come from `PdfInspector`.
 */
@Singleton
class PdfStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "pdfs").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /**
     * Copies the chosen PDF and checks it with [inspector]: [PdfAttach.Saved] with the generated name, or the reason it was
     * refused (and nothing is left on the disk). A file that can't be read at all is "unreadable", never a crash.
     */
    suspend fun attach(songId: String, uri: Uri, inspector: PdfInspector): PdfAttach = withContext(Dispatchers.IO) {
        val tmp = newFile(songId, "part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext PdfAttach.Refused(PdfProblem.UNREADABLE)
            input.use { copyReason(it, tmp) }?.let { tmp.delete(); return@withContext PdfAttach.Refused(it) }
            finish(tmp, inspector)
        } catch (e: Exception) {
            // a revoked permission or a provider that throws is "this file won't do", never a crash
            if (e is kotlinx.coroutines.CancellationException) throw e
            tmp.delete()
            PdfAttach.Refused(PdfProblem.UNREADABLE)
        }
    }

    /** Saves the bytes that came by the band under a new generated name, after the same check; refused leaves nothing. */
    suspend fun saveChecked(songId: String, bytes: ByteArray, inspector: PdfInspector): PdfAttach =
        withContext(Dispatchers.IO) { saveCheckedNow(songId, bytes, inspector) }

    /** [saveChecked] for a caller already off the main thread (the band's session thread). */
    fun saveCheckedNow(songId: String, bytes: ByteArray, inspector: PdfInspector): PdfAttach {
        if (!looksLikePdf(bytes)) return PdfAttach.Refused(PdfProblem.NOT_PDF)
        if (bytes.size > MAX_BYTES) return PdfAttach.Refused(PdfProblem.TOO_BIG)
        val tmp = newFile(songId, "part")
        return try {
            tmp.writeBytes(bytes)
            finish(tmp, inspector)
        } catch (e: Exception) {
            tmp.delete()
            PdfAttach.Refused(PdfProblem.UNREADABLE)
        }
    }

    // checks the half copy; only a good one gets its final name
    private fun finish(tmp: File, inspector: PdfInspector): PdfAttach = when (val v = inspector.check(tmp)) {
        is PdfCheck.Refused -> { tmp.delete(); PdfAttach.Refused(v.problem) }
        is PdfCheck.Ok -> {
            val name = tmp.name.removeSuffix(".part") + ".pdf"
            if (tmp.renameTo(File(dir, name))) PdfAttach.Saved(name, v.pages, v.bytes) else { tmp.delete(); PdfAttach.Refused(PdfProblem.UNREADABLE) }
        }
    }

    // the time plus a random bit: two PDFs chosen in the same millisecond never get the same name
    private fun newFile(songId: String, ext: String) =
        File(dir, "${songId.take(36)}-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}.$ext")

    fun remove(name: String) { if (name.isNotEmpty()) File(dir, name).delete() }

    /** Deletes the files no song points to, and copies left half done, once older than [SWEEP_AGE_MS] (as [TrackStore.sweep]). */
    fun sweep(referenced: Set<String>, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { f -> if (f.name !in referenced && now - f.lastModified() > SWEEP_AGE_MS) f.delete() }
    }

    /** The chosen file's own name ("Setlist.pdf"), to show; empty when Android doesn't say. */
    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull().orEmpty().ifEmpty { uri.lastPathSegment.orEmpty() }.substringAfterLast('/')

    companion object {
        const val MAX_BYTES = br.app.stagebook.domain.BandContent.MAX_PDF_BYTES
        const val SWEEP_AGE_MS = 24L * 60 * 60 * 1000
        val MIME = arrayOf("application/pdf")
        private val MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

        fun looksLikePdf(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

        /**
         * Copies [input] into [to] in chunks (without closing [input]); the reason it isn't good, or null: it doesn't start
         * with `%PDF-` or passes [MAX_BYTES] (the caller deletes [to]). A PDF picked by hand and one from a backup are
         * checked the same way.
         */
        internal fun copyReason(input: InputStream, to: File): PdfProblem? {
            val head = ByteArray(MAGIC.size)
            var size = 0L
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (size < head.size) System.arraycopy(buf, 0, head, size.toInt(), minOf(n.toLong(), head.size - size).toInt())
                    size += n
                    if (size > MAX_BYTES) return PdfProblem.TOO_BIG
                    out.write(buf, 0, n)
                }
            }
            return if (size >= head.size && looksLikePdf(head)) null else PdfProblem.NOT_PDF
        }

        internal fun copyPdf(input: InputStream, to: File): Boolean = copyReason(input, to) == null
    }
}
