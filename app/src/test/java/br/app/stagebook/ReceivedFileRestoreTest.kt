package br.app.stagebook

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.Backup
import br.app.stagebook.data.Backup.Part
import br.app.stagebook.data.IncomingFiles
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MetronomeSound
import br.app.stagebook.data.MidiOut
import br.app.stagebook.data.MidiPedal
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A second `.stagebook` tapped while the first one's "what to restore" window is still open (audit 10/11): the cache
 * keeps one received file, so the window of the first can't stay up pointing at a copy that is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReceivedFileRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: LibraryRepository
    private lateinit var backup: Backup
    private lateinit var incoming: IncomingFiles
    private lateinit var vm: SettingsViewModel

    @Before fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = LibraryRepository(context, scope)
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        backup = Backup(context, repo, TrackStore(context))
        incoming = IncomingFiles(context, backup)
        vm = SettingsViewModel(repo, context, AppUpdater(context), MidiPedal(context, repo, scope), backup, MetronomeSound(context), MidiOut(context, repo, scope))
    }

    @After fun stop() = Dispatchers.resetMain()

    private fun sent(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.filesDir, "sent").apply { mkdirs() }.resolve(name).apply { writeBytes(bytes) })

    private suspend fun backupFile(name: String, title: String): Uri {
        repo.saveSong(Song(title = title, author = "Someone", text = "line one\nline two"))
        val out = java.io.ByteArrayOutputStream()
        backup.write(out, Part.entries.toSet())
        return sent(name, out.toByteArray())
    }

    private suspend fun receive(uri: Uri): Uri = (incoming.receive(uri) as IncomingFiles.Result.Accepted).uri

    private val songs = LibraryCodec.encodeSongs(listOf(Song(title = "Hazard", author = "Richard Marx", text = "line one\nline two")))
    private val newerSongs = songs.replaceFirst(Regex("\"version\"\\s*:\\s*\\d+"), "\"version\":99")
        .let { if (it == songs) "{\"version\":99," + songs.trimStart().removePrefix("{") else it }

    @Test fun `a file from a newer app closes the window of the file before it`() = runBlocking {
        val first = receive(backupFile("first.stagebook", "First"))
        vm.inspectBackup(first).join()
        assertEquals(first, vm.pendingRestore.value?.uri)

        vm.inspectBackup(receive(sent("newer.json", newerSongs.toByteArray()))).join()
        // the first copy left the cache when the second came in: its window restoring would read nothing
        assertTrue(!File(first.path!!).exists())
        assertNull(vm.pendingRestore.value)
    }

    @Test fun `a second backup takes the window, and it restores from a copy that is there`() = runBlocking {
        val first = receive(backupFile("first.stagebook", "First"))
        vm.inspectBackup(first).join()
        val second = receive(backupFile("second.stagebook", "Second"))
        vm.inspectBackup(second).join()
        val pending = vm.pendingRestore.value
        assertNotNull(pending)
        assertEquals(second, pending!!.uri)
        assertTrue(File(pending.uri.path!!).exists())
    }
}
