package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.domain.Chords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editor on the computer (T37c): songs listed, created, edited and deleted, like the app's editor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorSongsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var repo: LibraryRepository
    private lateinit var api: WebEditorApi
    private val completed = mutableListOf<String>()
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)

    @Before fun setUp() = runBlocking {
        repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        api = WebEditorApi(repo, afterSave = { completed += it })
    }

    private fun call(method: String, path: String, body: String = "") = api.handle(HttpRequest(method, path, body = body.toByteArray()), device)!!

    private fun idOf(json: String) = Regex("\"id\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    @Test fun `leaving the app does not wait behind a restore or upload holding the editor's lock`() {
        val holding = java.util.concurrent.CountDownLatch(1)
        val busy = Thread { synchronized(api) { holding.countDown(); Thread.sleep(3_000) } }.apply { start() }
        holding.await()
        val started = System.nanoTime()
        api.clear()
        val ms = (System.nanoTime() - started) / 1_000_000
        busy.join()
        assertTrue("clear() waited $ms ms", ms < 1_000)
    }

    @Test fun `creates a song - chords above the lyrics become ChordPro and the BPM goes to tempo`() {
        val r = call("POST", "/api/songs", """{"title":" Nova ","author":"Banda","tempo":"92","text":"G       D\nlinha de exemplo"}""")
        assertEquals(200, r.status)
        val song = repo.song(idOf(r.text()))!!
        assertEquals("Nova", song.title)
        assertEquals("92", Chords.directive(song.text, "tempo"))
        assertTrue("[G]" in song.text)
        assertEquals(listOf(song.id), completed)   // artist/year completed later, like the app
    }

    @Test fun `title is required and the same title and artist is refused`() {
        assertEquals("""{"error":"title_empty"}""", call("POST", "/api/songs", """{"title":"  "}""").text())
        repo.saveSong(Song(title = "Hazard", author = "Richard Marx"))
        val r = call("POST", "/api/songs", """{"title":"hazard","author":"richard marx"}""")
        assertEquals(409, r.status)
        assertEquals("""{"error":"title_taken"}""", r.text())
    }

    @Test fun `lists sorted with the key, opens and edits keeping what the editor doesn't show`() {
        val id = repo.saveSong(Song(title = "Wild Horses", author = "Stones", text = "{key: G}\n[G]linha", zoom = 1.4f, track = "wild.ogg"))
        repo.saveSong(Song(title = "Hazard"))
        val list = call("GET", "/api/songs").text()
        assertTrue(list.indexOf("Hazard") < list.indexOf("Wild Horses"))
        assertTrue("\"key\":\"G\"" in list)
        assertTrue("\"title\":\"Wild Horses\"" in call("GET", "/api/songs/$id").text())
        // the BPM comes in its field, not in the text
        repo.updateSong(id) { it.copy(text = "{tempo: 76}\n" + it.text) }
        val opened = call("GET", "/api/songs/$id").text()
        assertTrue("\"tempo\":\"76\"" in opened && "{tempo" !in opened)
        assertEquals(200, call("PUT", "/api/songs/$id", """{"title":"Wild Horses","author":"The Rolling Stones","text":"{key: G}\n[G]outra"}""").status)
        val song = repo.song(id)!!
        assertEquals("The Rolling Stones", song.author)
        assertTrue("outra" in song.text)
        assertEquals(1.4f, song.zoom)
        assertEquals("wild.ogg", song.track)
    }

    @Test fun `deletes, and an unknown song is not found`() {
        val id = repo.saveSong(Song(title = "Sai"))
        assertEquals(204, call("DELETE", "/api/songs/$id").status)
        assertNull(repo.song(id))
        assertEquals(404, call("GET", "/api/songs/$id").status)
        assertEquals(404, call("PUT", "/api/songs/$id", """{"title":"x"}""").status)
    }

    @Test fun `key and capo change the text like the app's buttons`() {
        val up = call("POST", "/api/transpose", """{"text":"{key: G}\n[G]linha [D]outra","semitones":2}""").text()
        assertTrue("{key: A}" in up && "[A]linha [E]outra" in up)
        val capo = call("POST", "/api/capo", """{"text":"{key: G}\n[G]linha","capo":3}""").text()
        assertTrue("{capo: 3}" in capo)
    }

    // the app's import engine behind the editor on the computer (T37f); no lookups on the internet in the tests
    private fun importing(page: String? = null): WebEditorApi {
        val engine = br.app.stagebook.data.PageImporter(repo, br.app.stagebook.data.MusicBrainz(), br.app.stagebook.data.DeezerBpm())
        return WebEditorApi(repo, importer = { engine }, readPage = { _, _ -> page })
    }

    private fun import(api: WebEditorApi, body: String) = api.handle(HttpRequest("POST", "/api/import", body = body.toByteArray()), device)!!

    @Test fun `pasted chords become ChordPro with key and BPM, ready to review and not saved yet`() {
        repo.updateLook { it.copy(internetOn = false) }
        val text = "Tom: G\nBPM: 92\nG       D\nSaio cedo pela rua\nEm      C\nOnde a estrada vai dar\nG  D\nfim"
        val r = import(importing(), """{"text":${kotlinx.serialization.json.JsonPrimitive(text)},"title":"Estrada"}""")
        assertEquals(200, r.status)
        val body = r.text()
        assertTrue(body, "\"status\":\"song\"" in body && "\"title\":\"Estrada\"" in body)
        assertTrue(body, "[G]" in body && "\"tempo\":\"92\"" in body)
        assertTrue(repo.library.value.songs.isEmpty())   // only Save saves, like the app
    }

    @Test fun `a ChordPro file brings its own title and artist`() {
        repo.updateLook { it.copy(internetOn = false) }
        val text = "{title: Noite clara}\n{artist: Outra Banda}\n[Am]uma\n[C]duas\n[G]três"
        val body = import(importing(), """{"text":${kotlinx.serialization.json.JsonPrimitive(text)},"title":"arquivo"}""").text()
        assertTrue(body, "\"title\":\"Noite clara\"" in body && "\"author\":\"Outra Banda\"" in body && "{title" !in body)
    }

    @Test fun `tablature asks first, then comes in with the answer`() {
        repo.updateLook { it.copy(internetOn = false) }
        val text = "G       D\nlinha um\ne|---0---3---|\nB|---1---0---|\nG|---0---0---|\nD|---2---0---|\nEm      C\nlinha dois\nG       D\nlinha três"
        val json = kotlinx.serialization.json.JsonPrimitive(text)
        assertTrue("\"status\":\"askTab\"" in import(importing(), """{"text":$json}""").text())
        val answered = import(importing(), """{"text":$json,"withTab":false}""").text()
        assertTrue(answered, "\"status\":\"song\"" in answered && "e|---" !in answered)
    }

    @Test fun `a page address is read by the tablet with the same extraction`() {
        val page = kotlinx.serialization.json.JsonPrimitive("""{"text":"","chordText":"G       D\nSaio cedo pela rua\nEm      C\nOnde a estrada vai dar\nG\nfim","key":"G"}""").toString()
        val r = import(importing(page), """{"url":"https://www.cifraclub.com.br/x/y/"}""")
        assertEquals(200, r.status)
        assertTrue(r.text(), "[G]" in r.text())
    }

    @Test fun `page address refused offline, with a bad address or an unreadable page`() {
        assertEquals("""{"error":"bad_address"}""", import(importing("x"), """{"url":"file:///etc/hosts"}""").text())
        assertEquals("""{"error":"unreadable"}""", import(importing(null), """{"url":"https://example.com"}""").text())
        repo.updateLook { it.copy(internetOn = false) }
        assertEquals("""{"error":"offline"}""", import(importing("x"), """{"url":"https://example.com"}""").text())
    }

    @Test fun `key and capo stay out of the lyrics on the page and come back on save - tags too`() {
        val id = repo.saveSong(br.app.stagebook.data.model.Song(title = "Hazard", author = "Richard Marx", text = "{key: G}\n{capo: 2}\n[G]linha um", tags = listOf("Rock")))
        val got = call("GET", "/api/songs/$id").text()
        assertTrue(got, "\"text\":\"[G]linha um\"" in got)
        assertTrue(got, "\"key\":\"G\"" in got && "\"capo\":2" in got && "\"tags\":[\"Rock\"]" in got)
        assertEquals(200, call("PUT", "/api/songs/$id", """{"title":"Hazard","author":"Richard Marx","text":"[G]linha um\nlinha dois","key":"A","capo":3,"tags":["Rock","Nacional"," "]}""").status)
        val song = repo.song(id)!!
        assertEquals("A", br.app.stagebook.domain.Chords.key(song.text))
        assertEquals(3, br.app.stagebook.domain.Chords.capo(song.text))
        assertTrue(song.text.endsWith("[G]linha um\nlinha dois"))
        assertEquals(listOf("Rock", "Nacional"), song.tags)
        assertTrue(call("GET", "/api/songs").text().contains("\"tags\":[\"Rock\",\"Nacional\"]"))
    }

    @Test fun `transposing from the page moves the key it keeps apart and the chords`() {
        val r = call("POST", "/api/transpose", """{"text":"[G]linha","semitones":2,"meta":{"key":"G","capo":1}}""").text()
        assertTrue(r, "\"text\":\"[A]linha\"" in r)
        assertTrue(r, "\"key\":\"A\"" in r && "\"capo\":1" in r)
    }
}
