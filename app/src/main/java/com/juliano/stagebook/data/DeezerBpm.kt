package com.juliano.stagebook.data

import com.juliano.stagebook.domain.Chords
import com.juliano.stagebook.domain.SongMatch
import com.juliano.stagebook.domain.TextKey
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
 * BPM sugerido pela API pública do Deezer (gratuita, sem conta nem chave), quando a importação não trouxe (decisão do
 * usuário, 29/10). Só vão o título e o artista da música. O número é calculado por máquina (pode vir o dobro ou a metade):
 * é uma sugestão nos Dados da música, que o músico confere. Sem achar a faixa ou sem BPM (0), não preenche nada.
 */
@Singleton
class DeezerBpm @Inject constructor() {
    internal var endpoint = "https://api.deezer.com/"   // os testes trocam por um servidor local
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    /** O BPM em [ms] no máximo (a rede travada não segura a importação); null sem resposta ou sem dado. */
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

    /** A faixa da busca que é a música (mesmo título; com artista, ele desempata); null se nenhuma bate. */
    internal fun pickTrack(searchJson: String, title: String, artist: String): Long? = runCatching {
        val data = json.parseToJsonElement(searchJson).jsonObject["data"]?.jsonArray ?: return null
        data.map { it.jsonObject }.firstOrNull { t ->
            TextKey.of(t.str("title")) == TextKey.of(title) &&
                (artist.isBlank() || SongMatch.sameArtistKey(TextKey.of(t["artist"]?.jsonObject?.str("name").orEmpty()), TextKey.of(artist)))
        }?.get("id")?.jsonPrimitive?.longOrNull
    }.getOrNull()

    /** O BPM da faixa, arredondado; null se não tem (0) ou se sai do razoável. */
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
