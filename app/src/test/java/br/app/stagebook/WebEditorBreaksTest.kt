package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.data.web.WebEditorServer
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
    private val repo = testRepo(empty = true)
    private val api = WebEditorApi(repo)
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)
    private val ids = listOf("A", "B", "C", "D", "E").map { repo.saveSong(Song(title = it, author = "x", text = it)) }

    private fun call(method: String, path: String, body: String = "") = api.send(method, path, body, device = device)

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

    @Test fun `a save without items keeps the songs and the breaks, an empty list empties them`() {
        val id = create(ids, "[2]")
        assertEquals(200, call("PUT", "/api/sets/$id", """{"name":"Renamed"}""").status)
        assertEquals("Renamed", repo.library.value.setlist(id)!!.name)
        assertEquals(ids, repo.library.value.setlist(id)!!.items)
        assertEquals(listOf(2), breaksOf(id))
        assertEquals(200, call("PUT", "/api/sets/$id", """{"name":"Renamed","items":[]}""").status)
        assertEquals(emptyList<String>(), repo.library.value.setlist(id)!!.items)
        assertEquals(emptyList<Int>(), breaksOf(id))
    }

    @Test fun `a new setlist without items is created empty`() {
        val made = call("POST", "/api/sets", """{"name":"Empty"}""")
        assertEquals(200, made.status)
        val id = Regex("\"id\":\"([^\"]+)\"").find(made.text())!!.groupValues[1]
        assertEquals(emptyList<String>(), repo.library.value.setlist(id)!!.items)
    }

    @Test fun `closing the editor forgets the review waiting for the computer`() {
        val receiver = br.app.stagebook.data.web.WebReceive(repo, { throw AssertionError("not used") })
        val review = receiver.fromInbox("in1", "STAGEBOOK1\n1\nShow\nA", emptyList())!!
        val closing = WebEditorApi(repo, receiver = { receiver }, clearReview = receiver::clear)
        closing.clear()
        assertEquals(null, receiver.create(review.token, "Show", emptyMap()))
        // and without clear() the same review is still there to create from
        val kept = receiver.fromInbox("in2", "STAGEBOOK1\n1\nShow\nA", emptyList())!!
        assertTrue(receiver.create(kept.token, "Show", emptyMap()) != null)
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

    // the page draws these (setView: label, button, hint, summary); here, that the tablet sends them in each language
    private fun texts() = br.app.stagebook.data.web.WebEditorServer.texts(context, R.string.editor_lyrics)

    @Test @Config(sdk = [35], qualifiers = "pt-rBR") fun `the page gets the break texts in Portuguese`() {
        val t = texts()
        assertEquals("INTERVALO", t["breakLabel"])
        assertEquals("Intervalo", t["breakAdd"])
        assertEquals("Precisa de pelo menos 2 músicas", t["breakNeedsTwo"])
        assertEquals("%1\$s · %2\$s", t["setCountBreaks"])
        assertEquals(listOf("%d música", "%d música", "%d músicas"), listOf(t["songCount0"], t["songCount1"], t["songCountN"]))
        assertEquals(listOf("%d intervalo", "%d intervalo", "%d intervalos"), listOf(t["breakCount0"], t["breakCount1"], t["breakCountN"]))
    }

    @Test @Config(sdk = [35], qualifiers = "en") fun `the page gets the break texts in English`() {
        val t = texts()
        assertEquals("BREAK", t["breakLabel"])
        assertEquals("Break", t["breakAdd"])
        assertEquals("Needs at least 2 songs", t["breakNeedsTwo"])
        assertEquals(listOf("%d songs", "%d song", "%d songs"), listOf(t["songCount0"], t["songCount1"], t["songCountN"]))
        assertEquals(listOf("%d breaks", "%d break", "%d breaks"), listOf(t["breakCount0"], t["breakCount1"], t["breakCountN"]))
    }

    // ---- Duplicate (user, 23/11: the computer's editor had no way to copy a setlist) ----

    @Test fun `duplicate makes a copy with the name sent, the same songs and breaks, first in the list`() {
        val id = create(ids, "[2,4]")
        val made = call("POST", "/api/sets/$id/duplicate", """{"name":"Show (cópia)"}""")
        assertEquals(200, made.status)
        val copy = Regex("\"id\":\"([^\"]+)\"").find(made.text())!!.groupValues[1]
        assertTrue(copy != id)
        val sets = repo.library.value.sets
        assertEquals(listOf("Show (cópia)", "Show"), sets.map { it.name })
        assertEquals(sets[1].items, sets[0].items)
        assertEquals(listOf(2, 4), sets[0].breaks)
        // the original is left as it was
        assertEquals(listOf(2, 4), breaksOf(id))
    }

    @Test fun `duplicate with no name keeps the setlist's own, and one that is gone is not found`() {
        val id = create(ids)
        assertEquals(200, call("POST", "/api/sets/$id/duplicate").status)
        assertEquals(listOf("Show", "Show"), repo.library.value.sets.map { it.name })
        assertEquals(404, call("POST", "/api/sets/nope/duplicate", "{}").status)
    }

    @Test fun `the page has the Duplicate button and its texts come from the app's strings in both languages`() {
        val html = context.assets.open("web/index.html").bufferedReader().readText()
        assertTrue("id=\"set-duplicate\"" in html)
        for (lang in listOf("en", "pt-BR")) {
            val c = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(lang)) }
            val texts = WebEditorServer.texts(context.createConfigurationContext(c), R.string.editor_lyrics)
            listOf("setDuplicate", "setCopyName", "setDuplicated").forEach { assertTrue("$lang $it", !texts[it].isNullOrBlank()) }
            // the copy is named as the app names it
            assertEquals(context.createConfigurationContext(c).getString(R.string.set_duplicate), texts["setDuplicate"])
        }
    }
}
