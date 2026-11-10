package br.app.stagebook.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A `.stagebook` file tapped outside the app (file manager, download notification, a chat's "open with" or share):
 * copied into the app's cache and checked before anything opens. Only what the app's own reader understands goes on
 * to Settings → Library → Open file, which shows the parts and restores with its usual filters (song text only).
 *
 * Copied, not read from the sender's address: a chat app's permission to read the file can end before the person
 * picks the parts, and Open file reads the file twice (once to show the parts, once to restore).
 */
@Singleton
class IncomingFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: Backup,
) {
    sealed interface Result {
        /** A backup (or an old songs file) the app can read: opens Open file with [uri], the copy in the cache. */
        data class Accepted(val uri: Uri) : Result
        /** Not a Stagebook file, or it couldn't be read: nothing opens, a short message says so. */
        data object Rejected : Result
    }

    // one at a time: a second file tapped while the first is still being copied waits, instead of writing over it
    private val oneAtATime = Mutex()

    /** Copies and checks the file at [uri]. */
    suspend fun receive(uri: Uri): Result = withContext(Dispatchers.IO) {
        oneAtATime.withLock {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            // copied under a fixed name (a copy cut short by the app closing is written over by the next one) and only
            // given its own name once checked
            val part = File(dir, PART)
            val ok = runCatching {
                val kind = context.contentResolver.openInputStream(uri)!!.use { input ->
                    val buffered = input.buffered()
                    val kind = kindOf(buffered) ?: return@use null
                    part.outputStream().use { out -> copyLimited(buffered, out, if (kind == Kind.SONGS_JSON) MAX_JSON_BYTES else Long.MAX_VALUE) }
                    kind
                } ?: return@runCatching false
                decodes(part, kind)
            }.getOrDefault(false)
            // its own name: whoever still holds the previous copy's address never reads this file in its place
            val copy = File(dir, "received-${UUID.randomUUID()}.${Backup.EXTENSION}")
            if (ok && part.renameTo(copy)) {
                // one received file at a time: the previous copy (opened or not) goes, now that this one takes its place
                dir.listFiles()?.forEach { if (it != copy) it.delete() }
                Result.Accepted(Uri.fromFile(copy))
            } else {
                // a rejected file takes only its own copy: an earlier one may still be waiting to open (stage, editor)
                part.delete()
                Result.Rejected
            }
        }
    }

    private enum class Kind { BACKUP_ZIP, SONGS_JSON }

    // a look at the first bytes before copying: a video or a PDF shared by mistake isn't copied at all
    private fun kindOf(input: java.io.BufferedInputStream): Kind? {
        input.mark(HEAD_BYTES)
        val head = ByteArray(HEAD_BYTES)
        val n = input.read(head).coerceAtLeast(0)
        input.reset()
        return kindOfHead(head.copyOf(n))
    }

    // the same readers as Open file: the backup's manifest, or a songs file with at least one song that is song text
    private suspend fun decodes(file: File, kind: Kind): Boolean = when (kind) {
        Kind.BACKUP_ZIP -> file.inputStream().use { backup.inspect(it) } != null
        Kind.SONGS_JSON -> runCatching { LibraryCodec.decodeSongs(file.readText()).any(::isSongText) }
            .getOrElse { it is LibraryCodec.NewerFileException }   // newer app: Open file says to update
    }

    private fun copyLimited(input: InputStream, out: java.io.OutputStream, max: Long) {
        val buf = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            check(total <= max) { "file too large" }
            out.write(buf, 0, n)
        }
    }

    companion object {
        private const val DIR = "incoming"
        private const val PART = "incoming.part"
        private const val HEAD_BYTES = 64
        private const val COPY_BUFFER = 64 * 1024
        private const val MAX_JSON_BYTES = 32L * 1024 * 1024   // thousands of songs; a bigger "songs file" isn't one

        private fun isSongText(s: br.app.stagebook.data.model.Song) =
            br.app.stagebook.domain.BandContent.songOk(s.title, s.author, s.year, s.text)

        /** What the first bytes say: a zip (the backup) or JSON text (an old songs file); null for anything else. */
        private fun kindOfHead(head: ByteArray): Kind? {
            if (head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()) return Kind.BACKUP_ZIP
            val text = head.decodeToString().trimStart { it.isWhitespace() || it == Char(0xFEFF) }
            return if (text.startsWith("{")) Kind.SONGS_JSON else null
        }

        /**
         * The file an intent brings: Android's "open with" (VIEW, from a file manager, a download or a chat) or a share
         * to the app (SEND). Web links (the receive links) and anything without a file are not files: null.
         */
        fun fileUri(intent: Intent?): Uri? {
            intent ?: return null
            val uri = when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> streamOf(intent)
                else -> null
            } ?: return null
            return uri.takeIf { it.scheme == "content" || it.scheme == "file" }
        }

        @Suppress("DEPRECATION")   // the typed getParcelableExtra is API 33
        private fun streamOf(intent: Intent): Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
