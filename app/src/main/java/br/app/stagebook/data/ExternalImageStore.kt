package br.app.stagebook.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The person's own image for the external screen while the stage is closed (user, 22/11). One file, on this device only:
 * it doesn't go to the band nor in the backup.
 */
@Singleton
class ExternalImageStore @Inject constructor(@ApplicationContext private val context: Context) {
    val file: File get() = File(context.filesDir, FILE)

    /** Copies the picked image in place of the one before; false when it isn't an image or is too big. */
    suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "$FILE.part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val copied = input.use { i -> tmp.outputStream().use { o -> copyAtMost(i, o, MAX_BYTES) } }
            if (!copied || !isImage(tmp)) { tmp.delete(); return@withContext false }
            tmp.renameTo(file)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            tmp.delete()
            false
        }
    }

    companion object {
        const val FILE = "external_idle"
        const val MAX_BYTES = 20L * 1024 * 1024

        /** Android can read it as a picture (only the size is read, not the pixels). */
        fun isImage(f: File): Boolean {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            return o.outWidth > 0 && o.outHeight > 0
        }

        internal fun copyAtMost(input: java.io.InputStream, out: java.io.OutputStream, max: Long): Boolean {
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) return total > 0
                total += n
                if (total > max) return false
                out.write(buf, 0, n)
            }
        }
    }
}
