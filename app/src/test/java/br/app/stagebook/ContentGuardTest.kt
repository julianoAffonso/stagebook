package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.domain.AudioFormat
import br.app.stagebook.domain.BandContent
import br.app.stagebook.domain.ImportSiteRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Only lyrics and chords come in (user's request of 30/10): text, import sites, backing tracks and backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ContentGuardTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun repo(): LibraryRepository = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { r ->
        runBlocking { withTimeout(5_000) { r.loaded.first { it } } }
    }

    @Test fun `verses look like lyrics, a page of prose doesn't`() {
        assertTrue(BandContent.looksLikeLyrics("Saio cedo pela rua sem saber\nOnde a estrada vai dar\nLevo o violão e um papel"))
        val prose = (1..5).joinToString("\n") { "Este é um parágrafo longo de um documento qualquer, que não tem nada a ver com música e segue sem quebras por muitas palavras." }
        assertFalse(BandContent.looksLikeLyrics(prose))
    }

    @Test fun `an import site keeps only the domain`() {
        assertEquals("https://www.cifraclub.com.br/", ImportSiteRules.normalize("cifraclub.com.br/richard-marx/hazard/?x=1".let { "www.$it" }))
        assertEquals("https://letras.mus.br/", ImportSiteRules.normalize("https://letras.mus.br/abc"))
        assertNull(ImportSiteRules.normalize("not a site"))
    }

    @Test fun `audio is checked by its first bytes`() {
        assertEquals("ogg", AudioFormat.of("OggS".toByteArray() + ByteArray(8)))
        assertEquals("wav", AudioFormat.of("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()))
        assertEquals("mp3", AudioFormat.of("ID3".toByteArray() + ByteArray(9)))
        assertEquals("mp3", AudioFormat.of(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0, 0)))
        assertNull(AudioFormat.of(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))   // a JPEG
    }

    @Test fun `a photo renamed to mp3 doesn't become a backing track`() = runBlocking {
        val store = TrackStore(context)
        val fake = File(context.cacheDir, "foto.mp3").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(64)) }
        assertNull(store.copy("s1", android.net.Uri.fromFile(fake)))
        val real = File(context.cacheDir, "faixa.mp3").apply { writeBytes("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray() + ByteArray(32)) }
        assertTrue(store.copy("s1", android.net.Uri.fromFile(real))!!.endsWith(".wav"))   // the real format names it
    }

    @Test fun `the web editor refuses to save what isn't song text`() {
        val repo = repo()
        val api = WebEditorApi(repo)
        val encoded = "A".repeat(400)   // a file disguised as text
        val r = api.handle(HttpRequest("POST", "/api/songs", body = """{"title":"x","text":"$encoded"}""".toByteArray()), WebEditorAuth.Device("d", "n", "h", 0))!!
        assertEquals("""{"error":"not_song"}""", r.text())
    }

    @Test fun `a backup song that isn't song text stays out, and is counted`() = runBlocking {
        val repo = repo()
        val tracks = TrackStore(context)
        val backup = Backup(context, repo, tracks)
        repo.saveSong(Song(title = "Boa", text = "uma\nduas\ntrês"))
        repo.saveSong(Song(title = "Disfarçada", text = "B".repeat(500)))
        val file = File(context.cacheDir, "guard.stagebook")
        file.outputStream().use { backup.write(it, setOf(Backup.Part.LIBRARY)) }
        repo.restore(emptyList(), emptyList())
        assertTrue(file.inputStream().use { backup.restore(it, setOf(Backup.Part.LIBRARY)) })
        assertEquals(listOf("Boa"), repo.library.value.songs.map { it.title })
        assertEquals(1, backup.skipped)
    }

    @Test fun `a backup track that isn't audio stays out and leaves no file behind`() = runBlocking {
        val repo = repo()
        val tracks = TrackStore(context)
        val backup = Backup(context, repo, tracks)
        tracks.file("boa.ogg").writeBytes("OggS".toByteArray() + ByteArray(64))
        tracks.file("foto.mp3").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(64))
        repo.saveSong(Song(title = "Com faixa boa", text = "uma\nduas\ntrês", track = "boa.ogg"))
        repo.saveSong(Song(title = "Com foto", text = "uma\nduas\ntrês", track = "foto.mp3"))
        val file = File(context.cacheDir, "tracks.stagebook")
        file.outputStream().use { backup.write(it, setOf(Backup.Part.LIBRARY, Backup.Part.TRACKS)) }
        repo.restore(emptyList(), emptyList())
        tracks.file("boa.ogg").delete(); tracks.file("foto.mp3").delete()
        assertTrue(file.inputStream().use { backup.restore(it, setOf(Backup.Part.LIBRARY, Backup.Part.TRACKS)) })
        val byTitle = repo.library.value.songs.associateBy { it.title }
        assertEquals("boa.ogg", byTitle.getValue("Com faixa boa").track)
        assertEquals("", byTitle.getValue("Com foto").track)
        assertTrue(tracks.file("boa.ogg").exists())
        assertTrue(tracks.file("boa.ogg").parentFile!!.listFiles()!!.none { it.name.endsWith(".part") })
    }
}
