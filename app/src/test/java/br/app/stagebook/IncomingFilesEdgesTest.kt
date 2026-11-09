package br.app.stagebook

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.IncomingFiles
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Edges of a .stagebook tapped outside the app: what the first bytes say and what stays in the cache. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class IncomingFilesEdgesTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun incoming(): IncomingFiles = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        IncomingFiles(context, Backup(context, repo, TrackStore(context)))
    }

    private fun sent(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.filesDir, "sent").apply { mkdirs() }.resolve(name).apply { writeBytes(bytes) })

    private val songs = LibraryCodec.encodeSongs(listOf(Song(title = "Hazard", author = "Richard Marx", text = "linha um\nlinha dois")))

    @Test fun `a songs file with leading blank lines is still read`() = runBlocking {
        assertTrue(incoming().receive(sent("blank.json", ("\n\n  " + songs).toByteArray())) is IncomingFiles.Result.Accepted)
    }

    // Windows editors save JSON with a byte order mark: the first-bytes check skips it, and so must the decoder
    @Test fun `a songs file saved with a byte order mark is still read`() = runBlocking {
        val bom = Char(0xFEFF).toString()
        assertTrue(incoming().receive(sent("bom.json", (bom + songs).toByteArray())) is IncomingFiles.Result.Accepted)
    }

    @Test fun `a songs file from a newer app goes on, so Open file can say to update`() = runBlocking {
        val newer = songs.replaceFirst(Regex("\"version\"\\s*:\\s*\\d+"), "\"version\":99")
            .let { if (it == songs) "{\"version\":99," + songs.trimStart().removePrefix("{") else it }
        assertTrue(incoming().receive(sent("newer.json", newer.toByteArray())) is IncomingFiles.Result.Accepted)
    }

    @Test fun `only the latest received file stays in the cache`() = runBlocking {
        val inc = incoming()
        val dir = File(context.cacheDir, "incoming")
        dir.mkdirs()
        File(dir, "old-leftover.bin").writeText("x")
        val r = inc.receive(sent("songs.json", songs.toByteArray()))
        assertTrue(r is IncomingFiles.Result.Accepted)
        assertEquals(listOf("received.${Backup.EXTENSION}"), dir.listFiles()!!.map { it.name })
    }

    @Test fun `an empty file is rejected and leaves nothing`() = runBlocking {
        assertEquals(IncomingFiles.Result.Rejected, incoming().receive(sent("empty.stagebook", ByteArray(0))))
        assertTrue(File(context.cacheDir, "incoming").listFiles().orEmpty().isEmpty())
    }

    @Test fun `broken JSON is rejected`() = runBlocking {
        assertEquals(IncomingFiles.Result.Rejected, incoming().receive(sent("broken.json", "{\"songs\": [ {".toByteArray())))
    }

    @Test fun `a file starting with PK but not a real zip is rejected`() = runBlocking {
        val fake = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(200) { 7 }
        assertEquals(IncomingFiles.Result.Rejected, incoming().receive(sent("fake.stagebook", fake)))
    }
}
