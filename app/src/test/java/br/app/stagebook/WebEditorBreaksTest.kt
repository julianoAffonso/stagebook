package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
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

/** The setlist's breaks on the computer's API (editor and assistants): read, saved, kept and refused when they can't be. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorBreaksTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }
    private val api = WebEditorApi(repo)
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)
    private val ids = listOf("A", "B", "C", "D", "E").map { repo.saveSong(Song(title = it, author = "x", text = it)) }

    private fun call(method: String, path: String, body: String = "") =
        api.handle(HttpRequest(method, path, emptyMap(), body = body.toByteArray()), device)!!

    private fun list(items: List<String>) = items.joinToString(",", "[", "]") { "\"$it\"" }

    private fun create(items: List<String>, breaks: String? = null): String {
        val extra = breaks?.let { ""","breaks":$it""" }.orEmpty()
        val made = call("POST", "/api/sets", """{"name":"Show","items":${list(items)}$extra}""")
        assertEquals(200, made.status)
        return Regex("\"id\":\"([^\"]+)\"").find(made.text())!!.groupValues[1]
    }

    private fun put(id: String, items: List<String>, breaks: String? = null): Int {
        val extra = breaks?.let { ""","breaks":$it""" }.orEmpty()
        return call("PUT", "/api/sets/$id", """{"name":"Show","items":${list(items)}$extra}""").status
    }

    private fun breaksOf(id: String) = repo.library.value.setlist(id)!!.breaks

    @Test fun `get shows the breaks`() {
        val id = create(ids, "[2,4]")
        assertTrue(call("GET", "/api/sets/$id").text(), call("GET", "/api/sets/$id").text().contains("\"breaks\":[2,4]"))
    }

    @Test fun `a setlist created without breaks has none, and with them has them`() {
        assertEquals(emptyList<Int>(), breaksOf(create(ids)))
        assertEquals(listOf(1, 3), breaksOf(create(ids, "[1,3]")))
    }

    @Test fun `saving without breaks keeps the ones it has`() {
        val id = create(ids, "[2]")
        assertEquals(200, put(id, ids))
        assertEquals(listOf(2), breaksOf(id))
        // the app's rule for replaced songs: the break stays before C
        assertEquals(200, put(id, listOf(ids[4], ids[3], ids[2], ids[1], ids[0])))
        assertEquals(listOf(2), breaksOf(id))
    }

    @Test fun `saving with breaks replaces them, and an empty list takes them all out`() {
        val id = create(ids, "[2]")
        assertEquals(200, put(id, ids, "[1,4]"))
        assertEquals(listOf(1, 4), breaksOf(id))
        assertEquals(200, put(id, ids, "[]"))
        assertEquals(emptyList<Int>(), breaksOf(id))
    }

    @Test fun `breaks sent follow the songs the library doesn't have, as the app does`() {
        val id = create(ids)
        // B is gone from the library: the break before it (2) stays before the next song that is, C
        val items = listOf(ids[0], "gone", ids[2], ids[3])
        assertEquals(200, put(id, items, "[1,2]"))
        assertEquals(listOf(ids[0], ids[2], ids[3]), repo.library.value.setlist(id)!!.items)
        assertEquals(listOf(1), breaksOf(id))   // both breaks land before C: one stays
    }

    @Test fun `breaks that can't be are refused and nothing changes`() {
        val id = create(ids, "[2]")
        for (bad in listOf("[0]", "[5]", "[-1]", "[3,2]", "[2,2]")) {
            val r = call("PUT", "/api/sets/$id", """{"name":"Other","items":${list(ids)},"breaks":$bad}""")
            assertEquals(bad, 409, r.status)
            assertTrue(r.text(), r.text().contains("bad_breaks"))
        }
        assertEquals(409, call("POST", "/api/sets", """{"name":"X","items":${list(ids)},"breaks":[9]}""").status)
        assertEquals(400, call("PUT", "/api/sets/$id", """{"items":[],"breaks":["a"]}""").status)
        assertEquals(listOf(2), breaksOf(id))
        assertEquals("Show", repo.library.value.setlist(id)!!.name)
        assertEquals(1, repo.library.value.sets.size)
    }
}
