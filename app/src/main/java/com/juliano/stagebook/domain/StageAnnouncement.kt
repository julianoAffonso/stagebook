package com.juliano.stagebook.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Now Playing (T23): o "grito" do palco para programas de live e de projeção: a música da vez num JSON pequeno, mandado em broadcast UDP na rede
 * local, na porta [PORT], a cada troca de música e de [REPEAT_MS] em [REPEAT_MS] (quem ligar o programa depois também
 * recebe). Formato aberto e documentado em `docs/now-playing.md`, para OBS, Resolume e afins. Só o nome da música, nunca a letra.
 */
@Serializable
data class StageAnnouncement(
    val app: String = "stagebook",
    val v: Int = VERSION,
    val title: String,
    val artist: String = "",
    val set: String = "",
    val index: Int = 0,      // posição no setlist, a partir de 1 (0 = música avulsa)
    val count: Int = 0,      // músicas no setlist
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
