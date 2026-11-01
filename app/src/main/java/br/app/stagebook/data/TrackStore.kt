package br.app.stagebook.data

import br.app.stagebook.domain.AudioFormat
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing track files: when a track is linked to a song, the file is **copied into the app** (it doesn't break if the
 * original is moved or deleted). Only OGG, MP3 and WAV, the engine's formats.
 */
@Singleton
class TrackStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "tracks").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /**
     * Copies the chosen track; returns the saved name, or null if it isn't really OGG/MP3/WAV (checked by its first
     * bytes, not only the name), is bigger than [MAX_BYTES] or couldn't be read.
     */
    suspend fun copy(songId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        // the time plus a random bit: two tracks chosen in the same millisecond never get the same name
        val tmp = File(dir, "${songId.take(36)}-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}.part")
        try {
            if (extensionOf(uri) == null) return@withContext null
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            // the real format names the file (a .mp3 that is a WAV is kept as .wav)
            val ext = input.use { copyAudio(it, tmp) } ?: throw IOException("not audio or too big")
            val name = tmp.name.removeSuffix(".part") + ".$ext"
            if (!tmp.renameTo(File(dir, name))) throw IOException("rename failed")
            name
        } catch (e: Exception) {
            // any failure reading the picked file (a revoked permission, a provider that throws) is "this file won't
            // do", never a crash: the callers run in the screen's scope (audit 01/11)
            if (e is kotlinx.coroutines.CancellationException) throw e
            tmp.delete()
            null
        }
    }

    fun remove(name: String) { if (name.isNotEmpty()) File(dir, name).delete() }

    /**
     * Deletes the files no song points to (deleted songs, a restored backup) and copies left half done (`.part`), once
     * older than [SWEEP_AGE_MS]: a new song's track, not saved yet (even across Android closing the app), keeps its
     * file meanwhile. They stayed on the disk for good (audit 01/11).
     */
    fun sweep(referenced: Set<String>, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { f -> if (f.name !in referenced && now - f.lastModified() > SWEEP_AGE_MS) f.delete() }
    }

    /** The chosen file's name without the extension ("Under Pressure"), to show; empty when Android doesn't say. */
    fun displayName(uri: Uri): String = runCatching { queryName(uri) }.getOrNull().orEmpty().let(::shownName)

    private fun queryName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun extensionOf(uri: Uri): String? {
        val shown = queryName(uri).orEmpty()
        val byName = shown.ifEmpty { uri.lastPathSegment.orEmpty() }.substringAfterLast('.', "").lowercase()   // file:// has no display name
        if (byName in FORMATS) return byName
        return when (context.contentResolver.getType(uri)) {
            "audio/ogg", "application/ogg", "audio/vorbis" -> "ogg"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            else -> null
        }
    }

    companion object {
        /**
         * Copies [input] into [to] in chunks (without closing [input]); returns the real format's extension from the
         * first bytes, or null when it isn't audio or passes [MAX_BYTES] (the caller deletes [to]). A track picked by
         * hand and one from a backup are checked the same way.
         */
        internal fun copyAudio(input: java.io.InputStream, to: File): String? {
            val head = ByteArray(AudioFormat.HEADER)
            var size = 0L
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (size < head.size) System.arraycopy(buf, 0, head, size.toInt(), minOf(n.toLong(), head.size - size).toInt())
                    size += n
                    if (size > MAX_BYTES) return null
                    out.write(buf, 0, n)
                }
            }
            return AudioFormat.of(head)
        }

        /** "Under Pressure.ogg" → "Under Pressure". */
        fun shownName(file: String): String = file.substringBeforeLast('.').trim()

        val FORMATS = setOf("ogg", "mp3", "wav")
        const val MAX_BYTES = 200L * 1024 * 1024   // a long multitrack OGG is well under this
        const val SWEEP_AGE_MS = 24L * 60 * 60 * 1000
        val MIME = arrayOf("audio/ogg", "application/ogg", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/*")
    }
}
