package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.data.web.WebReceive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Editor on the computer (T37d–f): search, 3 words, band, setlists, backing track and backup, like the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorMoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var repo: LibraryRepository
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)

    @Before fun setUp() {
        repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
    }

    private fun call(api: WebEditorApi, method: String, path: String, body: String = "", file: File? = null) =
        api.handle(HttpRequest(method, path.substringBefore('?'), path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') }, body = body.toByteArray(), file = file), device)!!

    @Test fun `search keeps only results from the configured sites`() {
        var asked = ""
        val page = kotlinx.serialization.json.JsonPrimitive("""[{"title":"Hazard - Cifra Club","url":"https://www.cifraclub.com.br/richard-marx/hazard/"}]""").toString()
        val api = WebEditorApi(repo, readPage = { url, script -> asked = url; if (script != null) page else null })
        val r = call(api, "POST", "/api/search", """{"q":"hazard"}""")
        assertEquals(200, r.status)
        assertTrue(r.text(), "cifraclub.com.br/richard-marx/hazard" in r.text())
        assertTrue(asked, asked.startsWith("https://www.google.com/search?q=cifra+hazard"))
        repo.updateLook { it.copy(internetOn = false) }
        assertEquals("""{"error":"offline"}""", call(api, "POST", "/api/search", """{"q":"hazard"}""").text())
    }

    @Test fun `3 words - review then create, with mine, the ones that came and a choice`() {
        val mine = repo.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "[G]minha"))
        val similarA = repo.saveSong(Song(title = "Tempo Perdido", author = "Legião Urbana"))
        repo.saveSong(Song(title = "Tempo Perdido", author = "Outra"))
        val payload = "LETRAS3\n4\nShow de sábado\nHazard\tRichard Marx\nNoite clara\tOutra Banda\nTempo Perdido\nSem música"
        val came = SetlistCloud.SharedSong("Noite clara", "Outra Banda", "", "[Am]uma\n[C]duas\n[G]três")
        var joined = ""
        val receiver = WebReceive(repo, { SetlistCloud.Received.Ok(payload, listOf(came)) }, { code, _ -> joined = code })
        val api = WebEditorApi(repo, receiver = { receiver })
        val review = call(api, "POST", "/api/receive", """{"code":"47-violao-lua"}""").text()
        assertTrue(review, "\"status\":\"have\"" in review && "\"status\":\"came\"" in review && "\"status\":\"similar\"" in review && "\"status\":\"missing\"" in review)
        val token = Regex("\"token\":\"([^\"]+)\"").find(review)!!.groupValues[1]
        val created = call(api, "POST", "/api/receive/create", """{"token":"$token","name":"Show de sábado","choices":{"2":"$similarA"}}""").text()
        val setId = Regex("\"setlist\":\"([^\"]+)\"").find(created)!!.groupValues[1]
        val set = repo.library.value.setlist(setId)!!
        assertEquals(3, set.items.size)   // the missing one stays out
        assertEquals(mine, set.items[0])
        assertEquals("Noite clara", repo.song(set.items[1])!!.title)
        assertEquals(similarA, set.items[2])
        assertEquals("47-violao-lua", joined)   // the sender joins the band, as in the app
        assertEquals("""{"error":"expired"}""", call(api, "POST", "/api/receive/create", """{"token":"$token","name":"x"}""").text())
    }

    @Test fun `band - lists what arrived, syncs, and opening reviews it`() {
        var synced = 0
        var done = ""
        val item = br.app.stagebook.data.BandInbox.Item("i1", "f", "Celular da Ana", "LETRAS3\n1\nEnsaio\nHazard", emptyList())
        val receiver = WebReceive(repo, { SetlistCloud.Received.NotFound }, inboxDone = { done = it })
        val api = WebEditorApi(repo, receiver = { receiver }, band = { WebEditorApi.Band({ listOf(item) }, { synced++ }) })
        assertTrue(call(api, "GET", "/api/band").text().contains("\"from\":\"Celular da Ana\""))
        assertEquals(204, call(api, "POST", "/api/band/sync", "{}").status)
        assertEquals(1, synced)
        val review = call(api, "POST", "/api/band/open", """{"code":"i1"}""").text()
        val token = Regex("\"token\":\"([^\"]+)\"").find(review)!!.groupValues[1]
        call(api, "POST", "/api/receive/create", """{"token":"$token","name":"Ensaio"}""")
        assertEquals("i1", done)   // reviewed: it leaves the inbox
    }

    @Test fun `setlists - create with the date when nameless, rename, reorder, delete`() {
        val a = repo.saveSong(Song(title = "A"))
        val b = repo.saveSong(Song(title = "B"))
        val api = WebEditorApi(repo)
        val id = Regex("\"id\":\"([^\"]+)\"").find(call(api, "POST", "/api/sets", """{"name":"","items":["$a","$b"]}""").text())!!.groupValues[1]
        assertTrue(repo.library.value.setlist(id)!!.name.matches(Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")))
        call(api, "PUT", "/api/sets/$id", """{"name":"Show","items":["$b","$a"]}""")
        assertEquals("Show", repo.library.value.setlist(id)!!.name)
        assertEquals(listOf(b, a), repo.library.value.setlist(id)!!.items)
        assertTrue(call(api, "GET", "/api/sets/$id").text().indexOf("\"title\":\"B\"") < call(api, "GET", "/api/sets/$id").text().indexOf("\"title\":\"A\""))
        assertEquals(204, call(api, "DELETE", "/api/sets/$id").status)
        assertEquals(null, repo.library.value.setlist(id))
    }

    @Test fun `backing track sent from the computer is linked to the song, and can be removed`() {
        val tracks = TrackStore(context)
        val id = repo.saveSong(Song(title = "Com faixa"))
        val api = WebEditorApi(repo, tracks = { tracks }, workDir = { context.cacheDir })
        val upload = File(context.cacheDir, "upload.part").apply { writeBytes("OggS".toByteArray() + ByteArray(28)) }
        val r = call(api, "PUT", "/api/songs/$id/track?name=minha.ogg", file = upload)
        assertEquals(200, r.status)
        val name = repo.song(id)!!.track
        assertTrue(tracks.file(name).exists())
        assertEquals(409, call(api, "PUT", "/api/songs/$id/track?name=foto.jpg", file = File(context.cacheDir, "x.part").apply { writeBytes(ByteArray(4)) }).status)
        assertEquals(204, call(api, "DELETE", "/api/songs/$id/track").status)
        assertEquals("", repo.song(id)!!.track)
    }

    @Test fun `backup file - inspect, then merge what's missing`() = runBlocking {
        val tracks = TrackStore(context)
        val backup = Backup(context, repo, tracks)
        repo.saveSong(Song(title = "Do backup", text = "letra"))
        val file = File(context.cacheDir, "b.part")
        file.outputStream().use { backup.write(it, setOf(Backup.Part.LIBRARY)) }
        repo.restore(emptyList(), emptyList())   // the library empties
        val api = WebEditorApi(repo, backup = { backup }, workDir = { context.cacheDir })
        val info = call(api, "POST", "/api/backup/inspect", file = file).text()
        assertTrue(info, "\"parts\":[\"library\"]" in info && "\"songs\":1" in info)
        val token = Regex("\"token\":\"([^\"]+)\"").find(info)!!.groupValues[1]
        assertEquals(200, call(api, "POST", "/api/backup/restore", """{"token":"$token","parts":["library"],"replace":false}""").status)
        assertEquals(listOf("Do backup"), repo.library.value.songs.map { it.title })
    }

    @Test fun `a second track for the same song leaves no orphan file, and turning off drops a waiting backup`() = runBlocking {
        val tracks = TrackStore(context)
        val backup = Backup(context, repo, tracks)
        val id = repo.saveSong(Song(title = "Duas faixas"))
        val api = WebEditorApi(repo, tracks = { tracks }, backup = { backup }, workDir = { context.cacheDir })
        call(api, "PUT", "/api/songs/$id/track?name=a.ogg", file = File(context.cacheDir, "a.part").apply { writeBytes("OggS".toByteArray() + ByteArray(8)) })
        val first = repo.song(id)!!.track
        call(api, "PUT", "/api/songs/$id/track?name=b.ogg", file = File(context.cacheDir, "b.part").apply { writeBytes("OggS".toByteArray() + ByteArray(8)) })
        assertTrue(!tracks.file(first).exists())   // the first one went away
        assertTrue(tracks.file(repo.song(id)!!.track).exists())
        val file = File(context.cacheDir, "wait.part")
        file.outputStream().use { backup.write(it, setOf(Backup.Part.LIBRARY)) }
        call(api, "POST", "/api/backup/inspect", file = file)
        val waiting = context.cacheDir.listFiles()!!.filter { it.name.startsWith("backup-") }
        assertEquals(1, waiting.size)
        api.clear()
        assertTrue(waiting.none { it.exists() })
    }
}
