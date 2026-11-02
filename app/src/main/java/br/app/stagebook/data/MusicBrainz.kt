package br.app.stagebook.data

import br.app.stagebook.domain.TextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
        runCatching {
            val q = "recording:\"${esc(title)}\"" + if (artist.isBlank()) "" else " AND artist:\"${esc(artist)}\""
            val url = URL(endpoint + "?fmt=json&limit=25&query=" + URLEncoder.encode(q, "UTF-8"))
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "StagebookApp/0.2 (uso pessoal; app de letras para banda)")
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode != 200) null else best(c.inputStream.bufferedReader().readText(), title, artist)
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    companion object {
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
