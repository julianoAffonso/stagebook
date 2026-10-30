package br.app.stagebook.data

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

    /** Copies the chosen track; returns the saved name, or null if it isn't OGG/MP3/WAV or couldn't be read. */
    suspend fun copy(songId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val ext = extensionOf(uri) ?: return@withContext null
        val name = "${songId.take(36)}-${System.currentTimeMillis()}.$ext"
        val tmp = File(dir, "$name.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return@withContext null
            if (!tmp.renameTo(File(dir, name))) throw IOException("não renomeou")
            name
        } catch (e: IOException) {
            tmp.delete()
            null
        }
    }

    fun remove(name: String) { if (name.isNotEmpty()) File(dir, name).delete() }

    private fun extensionOf(uri: Uri): String? {
        val shown = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }.orEmpty()
        val byName = shown.substringAfterLast('.', "").lowercase()
        if (byName in FORMATS) return byName
        return when (context.contentResolver.getType(uri)) {
            "audio/ogg", "application/ogg", "audio/vorbis" -> "ogg"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            else -> null
        }
    }

    companion object {
        val FORMATS = setOf("ogg", "mp3", "wav")
        val MIME = arrayOf("audio/ogg", "application/ogg", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/*")
    }
}
