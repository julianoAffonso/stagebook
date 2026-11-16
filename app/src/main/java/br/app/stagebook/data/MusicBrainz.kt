package br.app.stagebook.data

import br.app.stagebook.domain.TextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills in artist and year from MusicBrainz (open database, no account). The year is the recording's first release, not
 * a compilation's.
 */
@Singleton
class MusicBrainz @Inject constructor() {
    data class Info(val title: String, val artist: String, val year: String)

    internal var endpoint = "https://musicbrainz.org/ws/2/recording/"   // tests swap it for a local server
    // blocking network calls (DNS, connect) ignore cancellation: they run loose here, and the waiter gives up on time
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // MusicBrainz lets one request through per second and answers the rest with 503: songs imported one after the other
    // lost their year to it, silently (user, 15/11, "Camila, Camila"). One at a time, a second apart, and a 503 is tried again.
    private val gate = Mutex()
    private var lastAt = 0L
    internal var spacingMs = MIN_SPACING_MS   // the tests shorten it

    /** Like [lookup], but returns null within [ms] even with a stuck network (the request goes on and is discarded). */
    suspend fun lookupWithin(title: String, artist: String, ms: Long): Info? {
        val job = io.async { lookup(title, artist) }
        return try {
            withTimeoutOrNull(ms) { job.await() }
        } finally {
            job.cancel()
        }
    }

    suspend fun lookup(title: String, artist: String): Info? = withContext(Dispatchers.IO) {
        gate.withLock {
            // the first try, and more after a growing pause when the server said "too many" (503), "slow down" (429) or
            // the connection broke or timed out: the first try of a cold connection failed often (user, 15/11)
            for (attempt in 0 until ATTEMPTS) {
                val wait = lastAt + spacingMs * (attempt + 1) - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastAt = System.currentTimeMillis()
                val r = ask(title, artist)
                if (r !is Asked.Busy) return@withLock (r as? Asked.Found)?.info
            }
            null
        }
    }

    private sealed interface Asked {
        data class Found(val info: Info?) : Asked
        data object Busy : Asked
    }

    private fun ask(title: String, artist: String): Asked =
        runCatching {
            val q = "recording:\"${esc(title)}\"" + if (artist.isBlank()) "" else " AND artist:\"${esc(artist)}\""
            val url = URL(endpoint + "?fmt=json&limit=25&query=" + URLEncoder.encode(q, "UTF-8"))
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "StagebookApp/0.2 (uso pessoal; app de letras para banda)")
            c.setRequestProperty("Accept", "application/json")
            try {
                when (c.responseCode) {
                    200 -> Asked.Found(best(c.inputStream.bufferedReader().readText(), title, artist))
                    503, 429 -> Asked.Busy
                    else -> Asked.Found(null)
                }
            } finally {
                c.disconnect()
            }
        }.getOrElse { if (it is java.io.IOException) Asked.Busy else Asked.Found(null) }

    companion object {
        const val MIN_SPACING_MS = 1_100L
        const val ATTEMPTS = 3

        private fun esc(s: String) = s.replace("\\", " ").replace("\"", " ")

        /** Same title (ignoring accents/case), compatible artist and the oldest release. */
        fun best(body: String, title: String, artist: String): Info? {
            val recordings = Json.parseToJsonElement(body).jsonObject["recordings"] as? JsonArray ?: return null
            val kt = TextKey.of(title)
            val ka = TextKey.of(artist)
            return recordings.mapNotNull { el ->
                val r = el.jsonObject
                if ((r["score"]?.jsonPrimitive?.intOrNull ?: 0) < 80) return@mapNotNull null
                val name = r.str("title")
                if (TextKey.of(name) != kt) return@mapNotNull null
                val art = artistOf(r)
                val kArt = TextKey.of(art)
                if (ka.isNotEmpty() && !kArt.contains(ka) && !ka.contains(kArt)) return@mapNotNull null
                val y = r.str("first-release-date").take(4).toIntOrNull() ?: 9999
                y to Info(name, art, if (y == 9999) "" else y.toString())
            }.minByOrNull { it.first }?.second
        }

        private fun JsonObject.str(key: String) = (this[key]?.jsonPrimitive?.takeIf { it.isString })?.content.orEmpty()

        private fun artistOf(r: JsonObject): String {
            val credits = r["artist-credit"]?.jsonArray ?: return ""
            return credits.joinToString("") { c ->
                val o = c.jsonObject
                o.str("name") + o.str("joinphrase")
            }.trim()
        }
    }
}
