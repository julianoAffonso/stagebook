package br.app.stagebook.data

import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.SongMatch
import br.app.stagebook.domain.TextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    /** The BPM within [ms] at most (a stuck network doesn't hold up the import); null with no answer or no data. */
    suspend fun lookupWithin(title: String, artist: String, ms: Long): Int? {
        if (title.isBlank()) return null
        val job = io.async { lookup(title, artist) }
        return try { withTimeoutOrNull(ms) { job.await() } } finally { job.cancel() }
    }

    private fun lookup(title: String, artist: String): Int? {
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val id = get("${endpoint}search?q=$q&limit=5")?.let { pickTrack(it, title, artist) } ?: return null
        return get("${endpoint}track/$id")?.let(::bpmOf)
    }

    /** The search track that is the song (same title; with an artist, it breaks the tie); null if none matches. */
    internal fun pickTrack(searchJson: String, title: String, artist: String): Long? = runCatching {
        val data = json.parseToJsonElement(searchJson).jsonObject["data"]?.jsonArray ?: return null
        data.map { it.jsonObject }.firstOrNull { t ->
            TextKey.of(t.str("title")) == TextKey.of(title) &&
                (artist.isBlank() || SongMatch.sameArtistKey(TextKey.of(t["artist"]?.jsonObject?.str("name").orEmpty()), TextKey.of(artist)))
        }?.get("id")?.jsonPrimitive?.longOrNull
    }.getOrNull()

    /** The track's BPM, rounded; null if it has none (0) or it's unreasonable. */
    internal fun bpmOf(trackJson: String): Int? = runCatching {
        val bpm = json.parseToJsonElement(trackJson).jsonObject["bpm"]?.jsonPrimitive?.doubleOrNull ?: return null
        Math.round(bpm).toInt().takeIf { it in Chords.MIN_TEMPO..Chords.MAX_TEMPO }
    }.getOrNull()

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content.orEmpty()

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }.getOrNull()

    private companion object { const val TIMEOUT_MS = 6_000 }
}
