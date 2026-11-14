package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The computer's song lists show what the device's rows show (key, BPM, capo, backing track: ui/songs/RowFacts.kt), so
 * every list the API feeds carries those facts: the song list, a setlist's songs and the review of what was received.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorRowFactsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)

    private fun call(api: WebEditorApi, method: String, path: String, body: String = "") =
        api.handle(HttpRequest(method, path, emptyMap(), body = body.toByteArray()), device)!!.text()

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun JsonObject.facts() = Triple(getValue("bpm").jsonPrimitive.int, getValue("capo").jsonPrimitive.int, getValue("hasTrack").jsonPrimitive.boolean)

    private val full = repo.saveSong(Song(title = "Cheia", author = "A", text = "{key: Em}\n{tempo: 120}\n{capo: 2}\n[Em]la", track = "cheia.ogg"))
    private val bare = repo.saveSong(Song(title = "Nada", author = "B", text = "[G]la"))
    init { repo.saveSong(Song(title = "Fora", author = "C", text = "{tempo: 900}\n{capo: 40}\n[G]la")) }
    private val api = WebEditorApi(repo)

    private fun row(list: JsonArray, title: String) = list.map { it.jsonObject }.first { it.getValue("title").jsonPrimitive.content == title }

    @Test fun `the song list carries key, BPM, capo and the backing track`() {
        val list = Json.parseToJsonElement(call(api, "GET", "/api/songs")).jsonArray
        val a = row(list, "Cheia")
        assertEquals("Em", a.getValue("key").jsonPrimitive.content)
        assertEquals(Triple(120, 2, true), a.facts())
        assertEquals(Triple(0, 0, false), row(list, "Nada").facts())
    }

    @Test fun `a tempo out of range is none and a capo is held to the device's range, as the rows read them`() {
        val list = Json.parseToJsonElement(call(api, "GET", "/api/songs")).jsonArray
        assertEquals(Triple(0, 12, false), row(list, "Fora").facts())
    }

    @Test fun `a setlist's songs carry the same facts`() {
        val id = obj(call(api, "POST", "/api/sets", """{"name":"Show","items":["$full","$bare"]}""")).getValue("id").jsonPrimitive.content
        val songs = obj(call(api, "GET", "/api/sets/$id")).getValue("songs").jsonArray
        assertEquals(Triple(120, 2, true), songs[0].jsonObject.facts())
        assertEquals("Em", songs[0].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals(Triple(0, 0, false), songs[1].jsonObject.facts())
    }

    @Test fun `the review of a received setlist carries the facts of my song and of the one that came along`() {
        val list = "STAGEBOOK1\n3\nShow\nCheia\nVeio\tD\nNada"
        val came = SetlistCloud.SharedSong("Veio", "D", text = "{key: A}\n{tempo: 88}\n{capo: 3}\n[A]la")
        val receiver = WebReceive(repo, { SetlistCloud.Received.Ok(list, listOf(came)) })
        val web = WebEditorApi(repo, receiver = { receiver })
        val review = obj(call(web, "POST", "/api/receive", """{"code":"47-violao-lua"}""")).getValue("rows").jsonArray
        assertEquals(Triple(120, 2, true), review[0].jsonObject.facts())
        assertEquals("Em", review[0].jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals(Triple(88, 3, false), review[1].jsonObject.facts())   // came along: its text has them, no track comes
        assertEquals("A", review[1].jsonObject.getValue("key").jsonPrimitive.content)
        assertFalse(review[2].jsonObject.getValue("hasTrack").jsonPrimitive.boolean)
    }
}
