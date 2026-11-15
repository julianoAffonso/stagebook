package br.app.stagebook.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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

    /** Copies the chosen PDF; returns the saved name, or null if it isn't a PDF, passes [MAX_BYTES] or can't be read. */
    suspend fun copy(songId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${songId.take(36)}-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}.part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            if (!input.use { copyPdf(it, tmp) }) throw IOException("not a PDF or too big")
            val name = tmp.name.removeSuffix(".part") + ".pdf"
            if (!tmp.renameTo(File(dir, name))) throw IOException("rename failed")
            name
        } catch (e: Exception) {
            // a revoked permission or a provider that throws is "this file won't do", never a crash
            if (e is kotlinx.coroutines.CancellationException) throw e
            tmp.delete()
            null
        }
    }

    /** Saves bytes that came by the band into a new generated name; null when they aren't a PDF or are too big. */
    suspend fun save(songId: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (!looksLikePdf(bytes) || bytes.size > MAX_BYTES) return@withContext null
        val name = "${songId.take(36)}-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}.pdf"
        runCatching { File(dir, name).writeBytes(bytes); name }.getOrNull()
    }

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
        const val MAX_BYTES = 8L * 1024 * 1024
        const val SWEEP_AGE_MS = 24L * 60 * 60 * 1000
        val MIME = arrayOf("application/pdf")
        private val MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

        fun looksLikePdf(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

        /**
         * Copies [input] into [to] in chunks (without closing [input]); false when it doesn't start with `%PDF-` or passes
         * [MAX_BYTES] (the caller deletes [to]). A PDF picked by hand and one from a backup are checked the same way.
         */
        internal fun copyPdf(input: InputStream, to: File): Boolean {
            val head = ByteArray(MAGIC.size)
            var size = 0L
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (size < head.size) System.arraycopy(buf, 0, head, size.toInt(), minOf(n.toLong(), head.size - size).toInt())
                    size += n
                    if (size > MAX_BYTES) return false
                    out.write(buf, 0, n)
                }
            }
            return size >= head.size && looksLikePdf(head)
        }
    }
}
