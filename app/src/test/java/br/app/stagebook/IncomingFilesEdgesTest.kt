package br.app.stagebook

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.IncomingFiles
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.PendingFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        assertEquals(listOf(copyOf(r)), dir.listFiles()!!.toList())
        // the next one accepted takes its place: the folder never holds more than one
        val next = inc.receive(sent("more.json", songsFile(3)))
        assertEquals(listOf(copyOf(next)), dir.listFiles()!!.toList())
    }

    // on the stage the first file waits for the person to leave: one that isn't Stagebook's must not take its copy
    @Test fun `a rejected file leaves the copy of the one waiting`() = runBlocking {
        val inc = incoming()
        val held = PendingFile<Uri>()
        val first = inc.receive(sent("songs.json", songs.toByteArray()))
        assertEquals(PendingFile.Arrival.HELD, held.arrive((first as? IncomingFiles.Result.Accepted)?.uri, onStage = true))
        // one that isn't copied at all, and one that is copied and then fails the check
        for (bad in listOf("%PDF-1.7 not a backup", "{\"songs\": [ {")) {
            val r = inc.receive(sent("bad.${Backup.EXTENSION}", bad.toByteArray()))
            assertEquals(PendingFile.Arrival.REJECTED, held.arrive((r as? IncomingFiles.Result.Accepted)?.uri, onStage = true))
        }
        val waiting = File(held.release()!!.path!!)
        assertTrue("the copy of the waiting file is gone", waiting.exists())
        assertEquals(songs, waiting.readText())
        assertEquals(listOf(waiting), File(context.cacheDir, "incoming").listFiles()!!.toList())
    }

    // a second tap while the first file is still being copied: one at a time, neither runs over the other's copy
    @Test fun `two files arriving together are both read whole`() = runBlocking {
        val inc = incoming()
        val first = songsFile(30)
        val second = songsFile(70)   // another size: halves of the two mixed in one copy pass for neither
        val firstCopying = CountDownLatch(1)
        val secondCopying = CountDownLatch(1)
        val firstDone = CountDownLatch(1)
        val a = Uri.parse("content://sender.files/doc/1")
        val b = Uri.parse("content://sender.files/doc/2")
        // the first stops halfway and gives the second time to start; the second stops halfway until the first is done
        shadowOf(context.contentResolver).registerInputStreamSupplier(a) {
            Halfway(first) { firstCopying.countDown(); secondCopying.await(HEAD_START_MS, TimeUnit.MILLISECONDS) }
        }
        shadowOf(context.contentResolver).registerInputStreamSupplier(b) {
            Halfway(second) { secondCopying.countDown(); firstDone.await(10, TimeUnit.SECONDS) }
        }
        val firstCopy = async(Dispatchers.Default) {
            // read before the second one is accepted: from then on only the second's copy stays
            val r = inc.receive(a)
            (r as? IncomingFiles.Result.Accepted)?.uri?.let { File(it.path!!).readBytes() }.also { firstDone.countDown() }
        }
        assertTrue(firstCopying.await(10, TimeUnit.SECONDS))
        val secondResult = async(Dispatchers.Default) { inc.receive(b) }
        assertArrayEquals(first, firstCopy.await())
        val kept = (secondResult.await() as? IncomingFiles.Result.Accepted)?.uri?.let { File(it.path!!) }
        assertNotNull("the second file was rejected", kept)
        assertArrayEquals(second, kept!!.readBytes())
        assertEquals(listOf(kept), File(context.cacheDir, "incoming").listFiles()!!.toList())   // nothing half-written
    }

    private fun copyOf(r: IncomingFiles.Result) = File((r as IncomingFiles.Result.Accepted).uri.path!!)

    private fun songsFile(count: Int): ByteArray =
        LibraryCodec.encodeSongs((1..count).map { Song(title = "Song $it", author = "Band", text = "line one of $it\nline two") }).toByteArray()

    /** Hands out half of [bytes], then runs [halfway] before the rest: the copy is caught in the middle. */
    private class Halfway(bytes: ByteArray, private val halfway: () -> Unit) : InputStream() {
        private val input = bytes.inputStream()
        private var left = bytes.size / 2
        private var stopped = false

        override fun read(): Int = ByteArray(1).let { if (read(it, 0, 1) < 0) -1 else it[0].toInt() and 0xFF }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left > 0) return input.read(b, off, minOf(len, left)).also { left -= it }
            if (!stopped) { stopped = true; halfway() }
            return input.read(b, off, len)
        }
    }

    private companion object {
        // how long the first file waits, halfway through its copy, for the second to start. With one at a time the
        // second never starts and the wait runs out: long enough for a busy machine, short enough for the test
        const val HEAD_START_MS = 1_000L
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
