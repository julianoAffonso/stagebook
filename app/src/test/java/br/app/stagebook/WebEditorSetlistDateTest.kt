package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Setlist
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

/** The setlist's show day on the computer's API (editor and assistants): read, saved, left alone and taken off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorSetlistDateTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repo = testRepo(empty = true)
    private val api = WebEditorApi(repo)
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)

    private fun call(method: String, path: String, body: String = "") = api.send(method, path, body, device = device)

    private fun create(extra: String = ""): String {
        val made = call("POST", "/api/sets", """{"name":"Show"$extra}""")
        assertEquals(200, made.status)
        return Regex("\"id\":\"([^\"]+)\"").find(made.text())!!.groupValues[1]
    }

    private fun dateOf(id: String) = repo.library.value.setlist(id)!!.date

    @Test fun `a setlist made without a day gets today and with a day keeps it`() {
        assertEquals(Setlist.today(), dateOf(create()))
        assertEquals("2026-12-24", dateOf(create(""","date":"2026-12-24"""")))
    }

    @Test fun `get and the list show the day`() {
        val id = create(""","date":"2026-12-24"""")
        assertTrue(call("GET", "/api/sets/$id").text().contains("\"date\":\"2026-12-24\""))
        assertTrue(call("GET", "/api/sets").text().contains("\"date\":\"2026-12-24\""))
    }

    @Test fun `saving without a day leaves it and with an empty one takes it off`() {
        val id = create(""","date":"2026-12-24"""")
        assertEquals(200, call("PUT", "/api/sets/$id", """{"name":"Renamed"}""").status)
        assertEquals("2026-12-24", dateOf(id))
        assertEquals(200, call("PUT", "/api/sets/$id", """{"name":"Renamed","date":""}""").status)
        assertEquals("", dateOf(id))
    }

    @Test fun `a day that is no date is not saved`() {
        val id = create(""","date":"2026-12-24"""")
        call("PUT", "/api/sets/$id", """{"name":"Show","date":"amanha"}""")
        assertEquals("2026-12-24", dateOf(id))
    }

    @Test fun `the list comes newest day first with the ones with no day last`() {
        val old = create(""","date":"2025-01-01"""")
        val none = create(",\"date\":\"\"")
        val fresh = create(""","date":"2026-11-15"""")
        val ids = Regex("\"id\":\"([^\"]+)\"").findAll(call("GET", "/api/sets").text()).map { it.groupValues[1] }.toList()
        assertEquals(listOf(fresh, old, none), ids)
    }
}
