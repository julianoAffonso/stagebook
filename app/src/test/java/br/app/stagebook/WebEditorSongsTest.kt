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
        api = WebEditorApi(repo) { completed += it }
    }

    private fun call(method: String, path: String, body: String = "") = api.handle(HttpRequest(method, path, body = body.toByteArray()), device)!!

    private fun idOf(json: String) = Regex("\"id\":\"([^\"]+)\"").find(json)!!.groupValues[1]

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
}
