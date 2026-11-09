package br.app.stagebook

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.IncomingFiles
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.PendingFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A .stagebook tapped outside the app (file manager, download, chat) opens Open file; anything that isn't a Stagebook
 * file opens nothing (item 14 of the user's list).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OpenStagebookFileTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun setup(): Triple<LibraryRepository, Backup, IncomingFiles> = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val backup = Backup(context, repo, TrackStore(context))
        Triple(repo, backup, IncomingFiles(context, backup))
    }

    private fun sent(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.filesDir, "sent").apply { mkdirs() }.resolve(name).apply { writeBytes(bytes) })

    private fun resolves(intent: Intent) =
        context.packageManager.queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.name == MainActivity::class.java.name }

    // --- the manifest: Android offers the app for the file ---

    @Test fun `a file sent as binary opens the app, from a chat or a download`() {
        assertTrue(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://chat.files/doc/42"), Backup.MIME)))
        assertTrue(resolves(Intent(Intent.ACTION_SEND).setType(Backup.MIME).putExtra(Intent.EXTRA_STREAM, Uri.parse("content://chat.files/doc/42"))))
    }

    @Test fun `a file manager that names the type differently still opens it by the extension`() =
        assertTrue(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///storage/emulated/0/Download/show.backup.stagebook"), "application/x-unknown")))

    @Test fun `a photo doesn't offer the app`() =
        assertFalse(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/photo.jpg"), "image/jpeg")))

    // --- the intent: what file it brings ---

    @Test fun `the file comes from open with or from a share`() {
        val doc = Uri.parse("content://chat.files/doc/42")
        assertEquals(doc, IncomingFiles.fileUri(Intent(Intent.ACTION_VIEW, doc)))
        assertEquals(doc, IncomingFiles.fileUri(Intent(Intent.ACTION_SEND).setType(Backup.MIME).putExtra(Intent.EXTRA_STREAM, doc)))
    }

    @Test fun `a receive link, a shared text or the launcher bring no file`() {
        assertNull(IncomingFiles.fileUri(Intent(Intent.ACTION_VIEW, Uri.parse("https://stagebook.app.br/r#47-violao-lua"))))
        assertNull(IncomingFiles.fileUri(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hi")))
        assertNull(IncomingFiles.fileUri(Intent(Intent.ACTION_MAIN)))
        assertNull(IncomingFiles.fileUri(null))
    }

    // --- the content: only what the app's own reader understands goes on ---

    @Test fun `a backup is accepted and Open file reads its copy`() = runBlocking {
        val (repo, backup, incoming) = setup()
        repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "[G]linha um\nlinha dois"))
        val out = ByteArrayOutputStream()
        backup.write(out, Backup.Part.entries.toSet())
        val r = incoming.receive(sent("show.stagebook", out.toByteArray()))
        assertTrue(r is IncomingFiles.Result.Accepted)
        val copy = (r as IncomingFiles.Result.Accepted).uri
        val contents = context.contentResolver.openInputStream(copy)!!.use { backup.inspect(it) }
        assertNotNull(contents)
        assertEquals(1, contents!!.songs)
    }

    @Test fun `something else renamed to stagebook is rejected and nothing stays in the cache`() = runBlocking {
        val (_, _, incoming) = setup()
        assertEquals(IncomingFiles.Result.Rejected, incoming.receive(sent("a.stagebook", "%PDF-1.7 not a backup".toByteArray())))
        assertTrue(File(context.cacheDir, "incoming").listFiles().orEmpty().isEmpty())
    }

    @Test fun `a zip without the backup's manifest is rejected`() = runBlocking {
        val (_, _, incoming) = setup()
        val zip = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zip).use { it.putNextEntry(java.util.zip.ZipEntry("payload.apk")); it.write(ByteArray(100)); it.closeEntry() }
        assertEquals(IncomingFiles.Result.Rejected, incoming.receive(sent("a.stagebook", zip.toByteArray())))
    }

    @Test fun `an old songs file opens only with song text in it`() = runBlocking {
        val (_, _, incoming) = setup()
        val good = br.app.stagebook.data.LibraryCodec.encodeSongs(listOf(Song(title = "Hazard", author = "Richard Marx", text = "linha um\nlinha dois")))
        assertTrue(incoming.receive(sent("songs.json", good.toByteArray())) is IncomingFiles.Result.Accepted)
        assertEquals(IncomingFiles.Result.Rejected, incoming.receive(sent("other.json", """{"name":"x"}""".toByteArray())))
        // a "song" that is an encoded block: not song text
        val hidden = br.app.stagebook.data.LibraryCodec.encodeSongs(listOf(Song(title = "x", text = "A".repeat(400))))
        assertEquals(IncomingFiles.Result.Rejected, incoming.receive(sent("hidden.json", hidden.toByteArray())))
    }

    @Test fun `a file that can't be read is rejected`() = runBlocking {
        val (_, _, incoming) = setup()
        assertEquals(IncomingFiles.Result.Rejected, incoming.receive(Uri.parse("content://nobody.here/doc/1")))
    }

    // the songs file path (also reached from Settings) keeps out what isn't song text, like the backup and the band
    @Test fun `importing a songs file leaves out what isn't song text`() = runBlocking {
        val (repo, _, _) = setup()
        val before = repo.library.value.songs.size
        val json = br.app.stagebook.data.LibraryCodec.encodeSongs(listOf(Song(title = "Hazard", author = "Richard Marx", text = "linha"), Song(title = "x", text = "A".repeat(400))))
        assertEquals(1, repo.importSongs(json).added)
        assertEquals(before + 1, repo.library.value.songs.size)
    }

    // --- the routing: on the stage it waits ---

    @Test fun `a file opens right away, waits on the stage, and a rejected one opens nothing`() {
        val p = PendingFile<String>()
        assertEquals(PendingFile.Arrival.OPEN, p.arrive("a", onStage = false))
        assertNull(p.release())
        assertEquals(PendingFile.Arrival.HELD, p.arrive("b", onStage = true))
        assertEquals(PendingFile.Arrival.HELD, p.arrive("c", onStage = true))   // only the latest waits
        assertEquals("c", p.release())
        assertNull(p.release())
        assertEquals(PendingFile.Arrival.REJECTED, p.arrive(null, onStage = false))
        assertEquals(PendingFile.Arrival.REJECTED, p.arrive(null, onStage = true))
        assertNull(p.release())
    }
}
