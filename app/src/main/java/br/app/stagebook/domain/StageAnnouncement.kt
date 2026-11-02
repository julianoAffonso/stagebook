package br.app.stagebook.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Now Playing (T23): the stage's "shout" to live and projection apps: the current song in a small JSON, sent as a UDP
 * broadcast on the local network, on port [PORT], on every song change and every [REPEAT_MS] (whoever starts their app
 * later also gets it). Open format documented in `docs/now-playing.md`, for OBS, Resolume and the like. Only the song
 * name, never the lyrics.
 */
@Serializable
data class StageAnnouncement(
    val app: String = "stagebook",
    val v: Int = VERSION,
    val title: String,
    val artist: String = "",
    val set: String = "",
    val index: Int = 0,      // position in the setlist, from 1 (0 = loose song)
    val count: Int = 0,      // songs in the setlist
    val key: String = "",
    val bpm: Int = 0,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val VERSION = 1
        const val PORT = 47800
        const val REPEAT_MS = 10_000L
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        fun decode(text: String): StageAnnouncement? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.app == "stagebook" }
    }
}
