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

    fun handle(req: HttpRequest): HttpResponse {
        if (!hostIsAddress(req.headers["host"].orEmpty())) return HttpResponse.status(403)
        onAccess()   // any access from the computer keeps the editor on (the open page pings every minute)
        if (!req.path.startsWith("/api/")) {
            return if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) HttpResponse.html(page()) else HttpResponse.status(404)
        }
        if (req.headers["x-stagebook"] != "1") return HttpResponse.status(403)
        when (req.path) {
            "/api/ping" -> return HttpResponse.status(204)
            "/api/strings" -> if (req.method == "GET") return HttpResponse.json(200, JsonObject(strings().mapValues { JsonPrimitive(it.value) }).toString())
            "/api/ask" -> return ask(req)
        }
        val device = auth.device(req.cookie(COOKIE)) ?: return HttpResponse.status(401)
        if (req.path == "/api/session" && req.method == "GET") {
            return HttpResponse.json(200, buildJsonObject { put("name", device.name); put("app", deviceName()) }.toString())
        }
        return api(req, device) ?: HttpResponse.status(404)
    }

    // asking to come in: POST creates the request on the tablet, GET (with its id) waits for the answer
    private fun ask(req: HttpRequest): HttpResponse = when (req.method) {
        "POST" -> {
            val name = runCatching { json.parseToJsonElement(req.text()).jsonObject["name"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
            auth.ask(name)?.let { HttpResponse.json(200, buildJsonObject { put("id", it.id) }.toString()) } ?: HttpResponse.status(429)
        }
        "GET" -> when (val p = auth.poll(req.query["id"].orEmpty())) {
            WebEditorAuth.Poll.Waiting -> HttpResponse.json(202, "{}")
            WebEditorAuth.Poll.Denied -> HttpResponse.status(403)
            WebEditorAuth.Poll.Unknown -> HttpResponse.status(404)
            is WebEditorAuth.Poll.Approved -> HttpResponse.json(
                200, "{}",
                mapOf("Set-Cookie" to "$COOKIE=${p.key}; Path=/; Max-Age=$TEN_YEARS; HttpOnly; SameSite=Strict"),
            )
        }
        else -> HttpResponse.status(405)
    }

    companion object {
        const val COOKIE = "stagebook_key"
        private const val TEN_YEARS = 10L * 365 * 24 * 3600

        /** "192.168.0.10:8090" yes; "evil.example:8090" no. */
        fun hostIsAddress(host: String): Boolean {
            val name = host.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
            return name.isNotEmpty() && (name.all { it.isDigit() || it == '.' } || ':' in name)
        }
    }
}
