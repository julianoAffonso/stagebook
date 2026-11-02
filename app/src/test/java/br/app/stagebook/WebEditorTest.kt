package br.app.stagebook

import br.app.stagebook.data.web.HttpError
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.data.web.WebEditorRoutes
import br.app.stagebook.data.web.WebHttp
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.shown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Editor on the computer (T37): the little HTTP, asking to come in, the cookie and the protections. */
class WebEditorTest {
    private class MemoryStore : WebEditorAuth.Store {
        var saved: String? = null
        override fun load() = saved
        override fun save(json: String) { saved = json }
    }

    private var clock = 1_000L
    private val store = MemoryStore()
    private val auth = WebEditorAuth(store) { clock }
    private val routes = WebEditorRoutes(auth, { "<html>page</html>" }, { mapOf("title" to "Editor") }, { "Tablet" })

    private fun req(method: String, path: String, body: String = "", cookie: String? = null, header: Boolean = true, host: String = "192.168.0.10:8090") =
        HttpRequest(
            method, path.substringBefore('?'),
            path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=') },
            buildMap {
                put("host", host)
                if (header) put("x-stagebook", "1")
                cookie?.let { put("cookie", "other=1; ${WebEditorRoutes.COOKIE}=$it") }
            },
            body.toByteArray(),
        )

    // asks, approves on the tablet and returns the cookie key the browser got
    private fun approvedKey(): String {
        val id = Regex("\"id\":\"([^\"]+)\"").find(routes.handle(req("POST", "/api/ask", """{"name":"Chrome · Windows"}""")).text())!!.groupValues[1]
        auth.answer(auth.requests.value.single().id, true)
        val r = routes.handle(req("GET", "/api/ask?id=$id"))
        assertEquals(200, r.status)
        return Regex("${WebEditorRoutes.COOKIE}=([^;]+)").find(r.headers.getValue("Set-Cookie"))!!.groupValues[1]
    }

    @Test fun `reads a request with headers, query and body`() {
        val raw = "POST /api/ask?id=a%20b HTTP/1.1\r\nHost: 192.168.0.10:8090\r\nContent-Length: 5\r\nCookie: x=1\r\n\r\nhello"
        val r = WebHttp.read(ByteArrayInputStream(raw.toByteArray())) { 100L }
        assertEquals("POST", r.method)
        assertEquals("/api/ask", r.path)
        assertEquals("a b", r.query["id"])
        assertEquals("hello", r.text())
        assertEquals("1", r.cookie("x"))
    }

    @Test fun `refuses a body bigger than the limit`() {
        val raw = "POST /x HTTP/1.1\r\nContent-Length: 500\r\n\r\n"
        val e = runCatching { WebHttp.read(ByteArrayInputStream(raw.toByteArray())) { 100L } }.exceptionOrNull()
        assertEquals(413, (e as HttpError).status)
    }

    @Test fun `a refused head never gets its body written to a file`() {
        var fileAsked = false
        val raw = "POST /api/backup/inspect HTTP/1.1\r\nHost: 192.168.0.10:8090\r\nContent-Length: 5\r\n\r\nhello"
        val e = runCatching {
            WebHttp.read(ByteArrayInputStream(raw.toByteArray()), toFile = { fileAsked = true; null }, gate = { 401 }) { 100L }
        }.exceptionOrNull()
        assertEquals(401, (e as HttpError).status)
        assertEquals(false, fileAsked)
    }

    @Test fun `writes status, length and extra headers`() {
        val out = ByteArrayOutputStream()
        WebHttp.write(out, br.app.stagebook.data.web.HttpResponse.json(200, "{}", mapOf("Set-Cookie" to "a=b")))
        val text = out.toString()
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue("Content-Length: 2\r\n" in text)
        assertTrue("Set-Cookie: a=b\r\n" in text)
        assertTrue(text.endsWith("\r\n\r\n{}"))
    }

    @Test fun `page and strings open without a key, the rest needs one`() {
        assertEquals("<html>page</html>", routes.handle(req("GET", "/", header = false)).text())
        assertEquals("""{"title":"Editor"}""", routes.handle(req("GET", "/api/strings")).text())
        assertEquals(401, routes.handle(req("GET", "/api/session")).status)
        assertEquals(401, routes.handle(req("GET", "/api/session", cookie = "made-up")).status)
    }

    @Test fun `approved on the tablet, the browser gets a key and comes in next time without asking`() {
        val key = approvedKey()
        val session = routes.handle(req("GET", "/api/session", cookie = key))
        assertEquals(200, session.status)
        assertTrue("\"name\":\"Chrome · Windows\"" in session.text())
        assertTrue("\"app\":\"Tablet\"" in session.text())
        // only the hash is saved, never the key; another app start reads the same list
        assertTrue(key !in store.saved!!)
        assertEquals(200, WebEditorRoutes(WebEditorAuth(store) { clock }, { "" }, { emptyMap() }, { "Tablet" }).handle(req("GET", "/api/session", cookie = key)).status)
    }

    @Test fun `denied, the browser is told and gets no key`() {
        val id = Regex("\"id\":\"([^\"]+)\"").find(routes.handle(req("POST", "/api/ask", """{"name":"X"}""")).text())!!.groupValues[1]
        assertEquals(202, routes.handle(req("GET", "/api/ask?id=$id")).status)   // still waiting
        auth.answer(id, false)
        val r = routes.handle(req("GET", "/api/ask?id=$id"))
        assertEquals(403, r.status)
        assertNull(r.headers["Set-Cookie"])
        assertTrue(auth.devices.value.isEmpty())
    }

    @Test fun `removed on the tablet, the key stops working right away`() {
        val key = approvedKey()
        auth.remove(auth.devices.value.single().id)
        assertEquals(401, routes.handle(req("GET", "/api/session", cookie = key)).status)
    }

    @Test fun `at most three waiting requests, and they expire`() {
        repeat(3) { assertEquals(200, routes.handle(req("POST", "/api/ask", """{"name":"PC $it"}""")).status) }
        assertEquals(429, routes.handle(req("POST", "/api/ask", """{"name":"PC 4"}""")).status)
        clock += WebEditorAuth.REQUEST_TTL_MS + 1
        assertEquals(200, routes.handle(req("POST", "/api/ask", """{"name":"PC 5"}""")).status)
        assertEquals(1, auth.requests.value.size)
    }

    @Test fun `api without the Stagebook header or reached by a site name is refused`() {
        val key = approvedKey()
        assertEquals(403, routes.handle(req("GET", "/api/session", cookie = key, header = false)).status)
        assertEquals(403, routes.handle(req("GET", "/api/session", cookie = key, host = "evil.example:8090")).status)
        assertEquals(403, routes.handle(req("GET", "/", host = "evil.example")).status)
        assertTrue(WebEditorRoutes.hostIsAddress("[fe80::1]:8090"))
    }

    @Test fun `essential mode hides the editor section`() {
        val essential = br.app.stagebook.data.model.Look(bandOn = false, internetOn = false, playerMode = false, extrasOn = false)
        assertEquals(false, SettingsSection.WEB_EDITOR.shown(essential))
        assertEquals(true, SettingsSection.WEB_EDITOR.shown(br.app.stagebook.data.model.Look()))
    }

    @Test fun `with no access for 5 minutes the editor is idle, only an approved computer keeps it on`() {
        val timer = br.app.stagebook.data.web.IdleTimer(br.app.stagebook.data.web.WebEditorServer.IDLE_MS) { clock }
        val r = WebEditorRoutes(auth, { "" }, { emptyMap() }, { "Tablet" }, onAccess = timer::touch)
        val key = approvedKey()
        clock += 4 * 60_000L
        assertEquals(401, r.handle(req("GET", "/api/ping")).status)   // a stranger's ping doesn't count
        clock += 2 * 60_000L
        assertEquals(true, timer.expired())
        timer.touch()
        clock += 4 * 60_000L
        assertEquals(204, r.handle(req("GET", "/api/ping", cookie = key)).status)   // the open page, every minute
        clock += 4 * 60_000L
        assertEquals(false, timer.expired())
    }

    @Test fun `a big upload is refused before its body unless it comes from an approved computer`() {
        val key = approvedKey()
        assertEquals(401, routes.gate(req("POST", "/api/backup/inspect"), big = true))
        assertEquals(403, routes.gate(req("POST", "/api/backup/inspect", cookie = key, header = false), big = true))
        assertEquals(null, routes.gate(req("POST", "/api/backup/inspect", cookie = key), big = true))
        assertEquals(403, routes.gate(req("GET", "/", host = "evil.example"), big = false))
    }

    @Test fun `the request shows a code, one per address, and a denied address waits`() {
        val first = auth.ask("Chrome · Windows", "192.168.0.20")!!
        assertEquals(4, first.code.length)
        assertEquals(first.id, auth.ask("Chrome · Windows", "192.168.0.20")!!.id)   // asking again: the same request
        auth.answer(first.id, false)
        assertEquals(null, auth.ask("Chrome · Windows", "192.168.0.20"))
        clock += WebEditorAuth.DENIED_MS + 1
        assertTrue(auth.ask("Chrome · Windows", "192.168.0.20") != null)
    }

    @Test fun `the key cookie lasts 90 days and each visit renews it`() {
        val key = approvedKey()
        val cookie = routes.handle(req("GET", "/api/session", cookie = key)).headers.getValue("Set-Cookie")
        assertTrue(cookie, "${WebEditorRoutes.COOKIE}=$key" in cookie && "Max-Age=${90L * 24 * 3600}" in cookie && "HttpOnly" in cookie)
    }
}
