package br.app.stagebook.data.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * What the editor on the computer answers (T37), without sockets: the server reads the request and writes what comes
 * back from here, so the tests talk to it directly.
 *
 * Protection: every /api call needs the `X-Stagebook` header (a page from another site can't send it without asking
 * the browser first, and nobody here answers that), the Host has to be an address, not a name (a site can't point its
 * name at the tablet), and everything except asking to come in needs the cookie of an approved computer.
 */
class WebEditorRoutes(
    private val auth: WebEditorAuth,
    private val page: () -> String,
    private val strings: () -> Map<String, String>,
    private val deviceName: () -> String,
    private val api: (HttpRequest, WebEditorAuth.Device) -> HttpResponse? = { _, _ -> null },
    private val onAccess: () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Before any body is read: the address check for everything, and for a big upload (backup, backing track) the
     * Stagebook header and an approved computer's key. A stranger can't make the tablet write a file (audit 30/10).
     */
    fun gate(req: HttpRequest, big: Boolean): Int? = when {
        !hostIsAddress(req.headers["host"].orEmpty()) -> 403
        big && req.headers["x-stagebook"] != "1" -> 403
        big && auth.device(req.cookie(COOKIE)) == null -> 401
        else -> null
    }

    fun handle(req: HttpRequest): HttpResponse {
        if (!hostIsAddress(req.headers["host"].orEmpty())) return HttpResponse.status(403)
        if (!req.path.startsWith("/api/")) {
            return if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) HttpResponse.html(page()) else HttpResponse.status(404)
        }
        if (req.headers["x-stagebook"] != "1") return HttpResponse.status(403)
        when (req.path) {
            "/api/strings" -> if (req.method == "GET") return HttpResponse.json(200, JsonObject(strings().mapValues { JsonPrimitive(it.value) }).toString())
            "/api/ask" -> return ask(req)
        }
        val device = auth.device(req.cookie(COOKIE)) ?: return HttpResponse.status(401)
        auth.touch(device.id)   // its key's 90 days start again
        // only an approved computer keeps the editor on (a stranger pinging it would keep it on all show; audit 30/10)
        onAccess()
        if (req.path == "/api/ping") return HttpResponse.status(204)
        if (req.path == "/api/session" && req.method == "GET") {
            // each visit renews the cookie, so a computer in use never expires
            return HttpResponse.json(
                200, buildJsonObject { put("name", device.name); put("app", deviceName()) }.toString(),
                mapOf("Set-Cookie" to cookie(req.cookie(COOKIE).orEmpty())),
            )
        }
        return api(req, device) ?: HttpResponse.status(404)
    }

    // asking to come in: POST creates the request on the tablet, GET (with its id) waits for the answer
    private fun ask(req: HttpRequest): HttpResponse = when (req.method) {
        "POST" -> {
            val name = runCatching { json.parseToJsonElement(req.text()).jsonObject["name"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
            // the code shows on the page and on the tablet: the musician allows only if they match
            auth.ask(name, req.remote)?.let { HttpResponse.json(200, buildJsonObject { put("id", it.id); put("code", it.code) }.toString()) }
                // refused for now: a denied address is told so, and in how many minutes it may ask again (it used to read "too many
                // requests, try again in a minute", the wrong reason and the wrong time; help review 16/11)
                ?: auth.deniedLeft(req.remote).takeIf { it > 0 }?.let { left ->
                    HttpResponse.json(429, buildJsonObject { put("denied", true); put("minutes", ((left + 59_999) / 60_000).toInt()) }.toString())
                }
                ?: HttpResponse.status(429)
        }
        "GET" -> when (val p = auth.poll(req.query["id"].orEmpty())) {
            WebEditorAuth.Poll.Waiting -> HttpResponse.json(202, "{}")
            WebEditorAuth.Poll.Denied -> HttpResponse.status(403)
            WebEditorAuth.Poll.Unknown -> HttpResponse.status(404)
            is WebEditorAuth.Poll.Approved -> HttpResponse.json(
                200, "{}",
                mapOf("Set-Cookie" to cookie(p.key)),
            )
        }
        else -> HttpResponse.status(405)
    }

    companion object {
        const val COOKIE = "stagebook_key"
        // an approved computer stays approved (user, 15/11: asking again at every session made the approval pointless):
        // the cookie lasts [COOKIE_DAYS] days and each visit renews it; removing the computer in Settings ends it at once
        // (only the key's hash is kept on the tablet). The key still travels over plain HTTP on the local network.
        const val COOKIE_DAYS = 90
        private fun cookie(key: String) = "$COOKIE=$key; Path=/; Max-Age=${COOKIE_DAYS * 24 * 3600}; HttpOnly; SameSite=Strict"

        /** "192.168.0.10:8090" yes; "evil.example:8090" no. */
        fun hostIsAddress(host: String): Boolean {
            val name = host.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
            return name.isNotEmpty() && (name.all { it.isDigit() || it == '.' } || ':' in name)
        }
    }
}
