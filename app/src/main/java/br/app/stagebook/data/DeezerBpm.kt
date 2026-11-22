package br.app.stagebook.data

import br.app.stagebook.domain.Chords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BPM suggested by Deezer's public API (free, no account or key), when the import didn't bring one (user's decision,
 * 29/10). Only the song's title and artist go out. The number is computed by machine (it can be double or half): it's a
 * suggestion in Song details, which the musician checks. If the track isn't found or has no BPM (0), nothing is filled
 * in.
 */
@Singleton
class DeezerBpm @Inject constructor() {
    internal var endpoint = "https://api.deezer.com/"   // tests swap it for a local server
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    /** What Deezer knows of the track: its BPM and its length in seconds (each 0 when it has none; user, 22/11). */
    data class Facts(val bpm: Int = 0, val seconds: Int = 0)

    /** The BPM within [ms] at most (a stuck network doesn't hold up the import); null with no answer or no data. */
    suspend fun lookupWithin(title: String, artist: String, ms: Long): Int? = factsWithin(title, artist, ms)?.bpm?.takeIf { it > 0 }

    /** The track's BPM and length within [ms] at most, from the same two requests; null with no answer or no track. */
    suspend fun factsWithin(title: String, artist: String, ms: Long): Facts? {
        if (title.isBlank()) return null
        val job = io.async { lookup(title, artist) { isActive } }
        return try { withTimeoutOrNull(ms) { job.await() } } finally { job.cancel() }
    }

    private fun lookup(title: String, artist: String, alive: () -> Boolean): Facts? {
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val id = get("${endpoint}search?q=$q&limit=$SEARCH_LIMIT", alive)?.let { pickTrack(it, title, artist) } ?: return null
        return get("${endpoint}track/$id", alive)?.let { Facts(bpmOf(it) ?: 0, secondsOf(it) ?: 0) }?.takeIf { it.bpm > 0 || it.seconds > 0 }
    }

    /** The track's length in seconds; null if it has none or it's unreasonable (over three hours). */
    internal fun secondsOf(trackJson: String): Int? = runCatching {
        val s = json.parseToJsonElement(trackJson).jsonObject["duration"]?.jsonPrimitive?.longOrNull ?: return null
        s.toInt().takeIf { it in 1..Chords.MAX_DURATION_S }
    }.getOrNull()

    /**
     * The search track that is the song (user, 22/11): the title without the version Deezer adds ("(Remastered 2009)",
     * "Ao Vivo"), the studio version first, the artist one of a duo or with a small typo ([TrackPick]); null if none is.
     */
    internal fun pickTrack(searchJson: String, title: String, artist: String): Long? = runCatching {
        val data = json.parseToJsonElement(searchJson).jsonObject["data"]?.jsonArray ?: return null
        val found = data.map { it.jsonObject }.mapNotNull { t ->
            val id = t["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            br.app.stagebook.domain.TrackPick.Result(id, t.str("title"), t["artist"]?.jsonObject?.str("name").orEmpty())
        }
        br.app.stagebook.domain.TrackPick.best(found, title, artist)
    }.getOrNull()

    /** The track's BPM, rounded; null if it has none (0) or it's unreasonable. */
    internal fun bpmOf(trackJson: String): Int? = runCatching {
        val bpm = json.parseToJsonElement(trackJson).jsonObject["bpm"]?.jsonPrimitive?.doubleOrNull ?: return null
        Math.round(bpm).toInt().takeIf { it in Chords.MIN_TEMPO..Chords.MAX_TEMPO }
    }.getOrNull()

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content.orEmpty()

    /** The answer, trying again after a pause when the server was busy (429, 503, "quota" in a 200) or the connection broke. */
    private fun get(url: String, alive: () -> Boolean): String? {
        for (attempt in 0 until ATTEMPTS) {
            // whoever asked gave up (cancel does not interrupt a blocked thread): no more tries, no sleeping for nobody
            if (!alive()) return null
            if (attempt > 0) Thread.sleep(pauseMs * attempt)
            val r = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                try {
                    when (c.responseCode) {
                        200 -> c.inputStream.bufferedReader().readText().let { if (it.contains("\"Quota limit exceeded\"")) Busy else it }
                        429, 503 -> Busy
                        else -> null
                    }
                } finally { c.disconnect() }
            }.getOrElse { if (it is java.io.IOException) Busy else null }
            if (r !== Busy) return r as String?
        }
        return null
    }

    private object Busy
    internal var pauseMs = 700L   // the tests shorten it

    private companion object { const val TIMEOUT_MS = 6_000; const val ATTEMPTS = 3; const val SEARCH_LIMIT = 10 }
}
