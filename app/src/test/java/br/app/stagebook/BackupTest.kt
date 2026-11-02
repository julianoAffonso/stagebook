package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.Backup.Part
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Backup (T25): what goes out comes back the same; each part only touches its own data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun setup(): Triple<LibraryRepository, TrackStore, Backup> = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val tracks = TrackStore(context)
        Triple(repo, tracks, Backup(context, repo, tracks))
    }

    @Test fun `everything checked - songs with zoom, setlist, track and settings come back the same`() = runBlocking {
        val (repo, tracks, backup) = setup()
        tracks.file("faixa.ogg").writeBytes(OGG)
        val id = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "linha", zoom = 1.4f, track = "faixa.ogg"))
        repo.updateLook { it.copy(fontSp = 44f) }
        context.getSharedPreferences("player", 0).edit().putFloat("volume", 0.7f).putString("perfil", "Interface X").commit()
        val out = ByteArrayOutputStream()
        backup.write(out, Part.entries.toSet())
        val bytes = out.toByteArray()

        assertEquals(Part.entries.toSet(), backup.inspect(ByteArrayInputStream(bytes))!!.parts)
        // changes everything and restores
        repo.restore(emptyList(), emptyList())
        repo.updateLook { it.copy(fontSp = 20f) }
        tracks.file("faixa.ogg").delete()
        context.getSharedPreferences("player", 0).edit().clear().commit()
        assertTrue(backup.restore(ByteArrayInputStream(bytes), Part.entries.toSet()))

        val song = repo.song(id)!!
        assertEquals(1.4f, song.zoom)
        assertEquals("faixa.ogg", song.track)
        assertTrue(tracks.file("faixa.ogg").readBytes().contentEquals(OGG))
        assertEquals(44f, repo.library.value.look.fontSp)
        assertEquals(0.7f, context.getSharedPreferences("player", 0).getFloat("volume", 0f))
        assertEquals("Interface X", context.getSharedPreferences("player", 0).getString("perfil", null))
    }

    @Test fun `unchecking - without tracks the song points to no file, and settings only leave songs alone`() = runBlocking {
        val (repo, tracks, backup) = setup()
        tracks.file("t.ogg").writeBytes(byteArrayOf(9))
        repo.saveSong(Song(title = "Com faixa", text = "x", track = "t.ogg"))
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY, Part.SETTINGS))
        val bytes = out.toByteArray()
        assertEquals(setOf(Part.LIBRARY, Part.SETTINGS), backup.inspect(ByteArrayInputStream(bytes))!!.parts)

        val before = repo.library.value.songs
        repo.updateLook { it.copy(fontSp = 50f) }
        backup.restore(ByteArrayInputStream(bytes), setOf(Part.SETTINGS))
        assertEquals(before, repo.library.value.songs)   // settings only: the songs don't change
        backup.restore(ByteArrayInputStream(bytes), setOf(Part.LIBRARY))
        assertEquals("", repo.library.value.songs.single { it.title == "Com faixa" }.track)
    }

    @Test fun `restoring the band - the band in memory follows the file`() = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val trust = br.app.stagebook.data.BandTrust(context)
        val backup = Backup(context, repo, TrackStore(context), trust)
        trust.add("ana", "Tablet da Ana", "k1")
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.SETTINGS))
        trust.remove("ana")
        trust.add("bia", "Celular da Bia", "k2")
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.SETTINGS)))
        assertEquals(listOf("ana"), trust.members.value.map { it.id })
        trust.add("caio", "Caio", "k3")   // the next change keeps the restored band
        assertEquals(listOf("ana", "caio"), br.app.stagebook.data.BandTrust(context).members.value.map { it.id }.sorted())
    }

    @Test fun `file that is not a backup is not read`() = runBlocking {
        val (_, _, backup) = setup()
        assertEquals(null, backup.inspect(ByteArrayInputStream("não é zip".toByteArray())))
    }

    @Test fun `add - only missing songs and setlists come in, the ones here stay as they are`() = runBlocking {
        val (repo, _, backup) = setup()
        repo.restore(emptyList(), emptyList())
        val a = repo.saveSong(Song(title = "Asa Branca", text = "do arquivo"))
        val b = repo.saveSong(Song(title = "Garota de Ipanema", text = "x"))
        repo.createSetlist("Show").let { id -> repo.addToSetlist(id, listOf(a, b)) }
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY))
        // here: Asa Branca was edited and Garota no longer exists
        repo.restore(listOf(Song(id = a, title = "Asa Branca", text = "editada aqui")), emptyList())
        backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY), replace = false)
        val songs = repo.library.value.songs
        assertEquals("editada aqui", songs.single { it.title == "Asa Branca" }.text)   // the one here stays
        assertTrue(songs.any { it.title == "Garota de Ipanema" })                    // the missing one comes in
        val show = repo.library.value.sets.single { it.name == "Show" }
        assertEquals(listOf("Asa Branca", "Garota de Ipanema"), show.items.map { id -> repo.song(id)!!.title })
    }
}

// a backing track has to be real audio to come back from a backup (checked by its first bytes)
private val OGG = "OggS".toByteArray() + byteArrayOf(1, 2, 3)
