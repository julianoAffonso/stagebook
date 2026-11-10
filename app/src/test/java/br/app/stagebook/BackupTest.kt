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
import org.junit.Assert.assertFalse
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

    // audit 07/11: a backup from a newer version (an enum value this one doesn't know) restores instead of failing whole
    @Test fun `a backup with an unknown setting value still restores`() = runBlocking {
        val (repo, _, backup) = setup()
        repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "linha"))
        val out = ByteArrayOutputStream()
        backup.write(out, Part.entries.toSet())
        // the settings file with a clock face this version has never heard of
        val changed = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zin ->
            java.util.zip.ZipOutputStream(changed).use { zout ->
                generateSequence { zin.nextEntry }.forEach { e ->
                    val bytes = zin.readBytes()
                    val text = bytes.decodeToString()
                    val edited = if (e.name == "ajustes.json") text.replace("\"clockMode\":\"clock\"", "\"clockMode\":\"FACE_FROM_THE_FUTURE\"") else text
                    if (e.name == "ajustes.json") assertTrue("the test needs the field in the file", edited != text)
                    zout.putNextEntry(java.util.zip.ZipEntry(e.name)); zout.write(if (e.name == "ajustes.json") edited.toByteArray() else bytes); zout.closeEntry()
                }
            }
        }
        repo.restore(emptyList(), emptyList())
        assertTrue(backup.restore(ByteArrayInputStream(changed.toByteArray()), Part.entries.toSet()))
        assertEquals(br.app.stagebook.data.model.StageClockMode.CLOCK, repo.library.value.look.clockMode)   // the unknown value fell to the default
        assertEquals(1, repo.library.value.songs.size)
    }

    @Test fun `everything checked - songs with zoom, setlist, track and settings come back the same`() = runBlocking {
        val (repo, tracks, backup) = setup()
        tracks.file("faixa.ogg").writeBytes(OGG)
        val id = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "linha", zoom = 1.4f, track = "faixa.ogg"))
        // the metronome's volume too (user, 07/11), and the show's length on the clock
        repo.updateLook { it.copy(fontSp = 44f, metroVolume = 40, showMinutes = 150) }
        context.getSharedPreferences("player", 0).edit().putBoolean("batteryAsked", true).commit()
        val out = ByteArrayOutputStream()
        backup.write(out, Part.entries.toSet())
        val bytes = out.toByteArray()

        assertEquals(Part.entries.toSet(), backup.inspect(ByteArrayInputStream(bytes))!!.parts)
        // changes everything and restores
        repo.restore(emptyList(), emptyList())
        repo.updateLook { it.copy(fontSp = 20f, metroVolume = 100, showMinutes = 60) }
        tracks.file("faixa.ogg").delete()
        context.getSharedPreferences("player", 0).edit().clear().commit()
        assertTrue(backup.restore(ByteArrayInputStream(bytes), Part.entries.toSet()))

        val song = repo.song(id)!!
        assertEquals(1.4f, song.zoom)
        assertEquals("faixa.ogg", song.track)
        assertTrue(tracks.file("faixa.ogg").readBytes().contentEquals(OGG))
        assertEquals(44f, repo.library.value.look.fontSp)
        assertEquals(40, repo.library.value.look.metroVolume)
        assertEquals(150, repo.library.value.look.showMinutes)
        // the battery question is each device's own: freeing the app from battery saving doesn't travel either
        assertFalse(context.getSharedPreferences("player", 0).getBoolean("batteryAsked", false))
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

    // a band saved with [members] by one device, restored after [between] ran (audit 10/11, user: "Faça")
    private suspend fun bandAgainAfter(members: Boolean = true, identity: Boolean = true, between: () -> Unit): Boolean {
        val prefs = context.getSharedPreferences("identity", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        if (identity) br.app.stagebook.data.DeviceIdentity(context).id   // the key pair is made on first use
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val trust = br.app.stagebook.data.BandTrust(context)
        trust.members.value.forEach { trust.remove(it.id) }
        val backup = Backup(context, repo, TrackStore(context), trust)
        if (members) trust.add("ana", "Tablet da Ana", "k1")
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.SETTINGS))
        between()
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.SETTINGS)))
        return backup.bandAgain
    }

    private fun becomeAnotherDevice() {
        context.getSharedPreferences("identity", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        br.app.stagebook.data.DeviceIdentity(context).id
    }

    @Test fun `a band restored on the device that saved it asks for nothing`() = runBlocking {
        assertEquals(false, bandAgainAfter { })
    }

    @Test fun `a band restored on another device says it has to be approved again`() = runBlocking {
        assertEquals(true, bandAgainAfter { becomeAnotherDevice() })
    }

    @Test fun `a device that never had an identity is new to the band too`() = runBlocking {
        assertEquals(true, bandAgainAfter { context.getSharedPreferences("identity", android.content.Context.MODE_PRIVATE).edit().clear().commit() })
    }

    @Test fun `with no band in the file, another device has nothing to approve`() = runBlocking {
        assertEquals(false, bandAgainAfter(members = false) { becomeAnotherDevice() })
    }

    @Test fun `a file that does not say who saved it shows no notice`() = runBlocking {
        // the same as a file from before the device was written in it
        assertEquals(false, bandAgainAfter(identity = false) { becomeAnotherDevice() })
    }

    // leaving the tracks out on purpose takes their names too: nothing to attach again on the other side (user, 10/11)
    @Test fun `a file saved without the tracks carries no audio names, and nothing comes in as lost`() = runBlocking {
        val (repo, tracks, backup) = setup()
        repo.restore(emptyList(), emptyList())
        val file = tracks.file("a.ogg").apply { parentFile!!.mkdirs(); writeBytes(OGG) }
        repo.saveSong(Song(title = "With audio", author = "x", text = "line", track = file.name, trackName = "Live take"))
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY))
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY)))
        assertEquals("", repo.library.value.songs.single().trackName)
        assertEquals(emptyList<br.app.stagebook.data.model.LostAudio>(), backup.lost)
    }

    @Test fun `tracks in the file but left out when opening it take their names along`() = runBlocking {
        val (repo, tracks, backup) = setup()
        repo.restore(emptyList(), emptyList())
        val file = tracks.file("a.ogg").apply { parentFile!!.mkdirs(); writeBytes(OGG) }
        repo.saveSong(Song(title = "With audio", author = "x", text = "line", track = file.name, trackName = "Live take"))
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY, Part.TRACKS))
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY)))
        assertEquals("", repo.library.value.songs.single().trackName)
        assertEquals(emptyList<br.app.stagebook.data.model.LostAudio>(), backup.lost)
    }

    @Test fun `a track that should have come and didn't is named, and joining names only the songs that came in`() = runBlocking {
        val (repo, tracks, backup) = setup()
        repo.restore(emptyList(), emptyList())
        val file = tracks.file("a.ogg").apply { parentFile!!.mkdirs(); writeBytes(OGG) }
        repo.saveSong(Song(title = "With audio", author = "x", text = "line", track = file.name, trackName = "Live take"))
        repo.saveSong(Song(title = "Gone", author = "x", text = "line", track = "gone.ogg", trackName = "Gone live"))   // no file
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY, Part.TRACKS))
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY, Part.TRACKS)))
        val lost = listOf(br.app.stagebook.data.model.LostAudio("Gone", "Gone live"))
        assertEquals(lost, repo.lostAudio.value)
        assertEquals(lost, backup.lost)
        // joining the same file again brings nothing new: nothing to say
        repo.lostAudioSeen()
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY, Part.TRACKS), replace = false))
        assertEquals(emptyList<br.app.stagebook.data.model.LostAudio>(), repo.lostAudio.value)
        assertEquals(emptyList<br.app.stagebook.data.model.LostAudio>(), backup.lost)
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
