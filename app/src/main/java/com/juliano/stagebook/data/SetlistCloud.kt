package com.juliano.stagebook.data

import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.domain.ShareCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Setlist (e as músicas) pela internet, sem servidor nosso: o pacote do QR (`L3` + deflate) e cada música (`S1` +
 * deflate), cifrados com a chave do código, vão para um canal do ntfy.sh (serviço público grátis; regras conferidas em 24/10: mensagem até 4 KB, guardada 12 h, "melhor esforço").
 * Quem recebe digita o código; o app busca no canal, decifra e abre a mesma conferência do QR.
 */
@Singleton
class SetlistCloud @Inject constructor(private val repository: LibraryRepository) {
    /** Música que vai junto com o setlist (o texto em ChordPro, com a cifra: quem recebe mostra conforme o perfil dele). */
    @Serializable
    data class SharedSong(val title: String, val author: String = "", val year: String = "", val text: String = "")

    /** [skipped]: músicas grandes demais para uma mensagem (raro), que não foram. */
    data class Sent(val code: String, val songs: Int, val skipped: Int)

    sealed interface Received {
        data class Ok(val text: String, val songs: List<SharedSong> = emptyList()) : Received
        data object NotFound : Received   // código errado ou já passou das 12 h
        data object Offline : Received
    }

    /**
     * Envia o setlist (e, com [withSongs], cada música numa mensagem própria, antes do setlist: quem recebe nunca vê o
     * setlist sem as músicas). Devolve o código, ou null se não deu (sem internet, serviço fora, setlist grande demais).
     */
    suspend fun send(setId: String, withSongs: Boolean): Sent? {
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return null
        val packed = repository.qrPayload(setId) ?: return null
        return sendPackage(packed, if (withSongs) set.items.mapNotNull(lib::song) else emptyList())
    }

    /**
     * Músicas avulsas (da aba Músicas), sem setlist: a lista vai no mesmo formato do setlist, **sem nome** — é assim que
     * quem recebe sabe que são só músicas para a biblioteca.
     */
    suspend fun sendSongs(songIds: List<String>): Sent? {
        val songs = songIds.mapNotNull(repository.library.value::song)
        if (songs.isEmpty()) return null
        val list = (listOf(QrPayload.HEADER, songs.size.toString(), "") + songs.map { listOf(it.title, it.author).joinToString("\t") { s -> s.replace('\n', ' ').replace('\t', ' ').trim() }.trimEnd('\t') })
            .joinToString("\n")
        return sendPackage(QrPayload.pack(list), songs)
    }

    // cada música numa mensagem própria e a lista por último: quem recebe nunca vê a lista sem as músicas
    private suspend fun sendPackage(packed: ByteArray, songs: List<com.juliano.stagebook.data.model.Song>): Sent? = withContext(Dispatchers.IO) {
        val code = ShareCode.generate()
        val keys = ShareCode.keys(code)
        var sent = 0
        var skipped = 0
        for (song in songs) {
            val body = ShareCode.encrypt(keys.key, pack(SONG_MARK, json.encodeToString(SharedSong(song.title, song.author, song.year, song.text))))
            when {
                body.length > MAX_MESSAGE -> skipped++
                post(keys.topic, body) -> sent++
                else -> return@withContext null
            }
        }
        val body = ShareCode.encrypt(keys.key, packed)
        if (body.length > MAX_MESSAGE || !post(keys.topic, body)) return@withContext null
        Sent(code, sent, skipped)
    }

    private fun post(topic: String, body: String): Boolean = runCatching {
        val c = URL("$SERVER/$topic").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            c.responseCode == 200
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)

    suspend fun receive(input: String): Received = withContext(Dispatchers.IO) {
        val code = ShareCode.normalize(input) ?: return@withContext Received.NotFound
        val keys = ShareCode.keys(code)
        val lines = runCatching {
            val c = URL("$SERVER/${keys.topic}/json?poll=1&since=all").openConnection() as HttpURLConnection
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (c.responseCode != 200) null else c.inputStream.bufferedReader().readLines()
            } finally {
                c.disconnect()
            }
        }.getOrNull() ?: return@withContext Received.Offline
        // só o que decifra com este código (o canal é público: pode ter lixo de outra pessoa); o setlist mais novo
        val plain = messages(lines).mapNotNull { ShareCode.decrypt(keys.key, it) }
        val text = plain.asReversed().firstNotNullOfOrNull { QrPayload.unpack(it) }
        val songs = plain.mapNotNull { unpack(SONG_MARK, it) }.mapNotNull { runCatching { json.decodeFromString<SharedSong>(it) }.getOrNull() }
        if (text == null) Received.NotFound else Received.Ok(text, songs)
    }

    companion object {
        private const val SERVER = "https://ntfy.sh"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_MESSAGE = 4_000          // o ntfy.sh aceita até 4.096 bytes por mensagem
        private const val USER_AGENT = "StagebookApp/0.2 (setlist da banda)"
        private val json = Json { ignoreUnknownKeys = true }
        private val SONG_MARK = byteArrayOf('S'.code.toByte(), '1'.code.toByte())

        /** [mark] + deflate do texto (o mesmo jeito do pacote do QR, com outra marca para as músicas). */
        fun pack(mark: ByteArray, text: String): ByteArray {
            val d = Deflater(Deflater.BEST_COMPRESSION, true)
            return try {
                d.setInput(text.toByteArray(Charsets.UTF_8))
                d.finish()
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (!d.finished()) out.write(buf, 0, d.deflate(buf))
                mark + out.toByteArray()
            } finally {
                d.end()
            }
        }

        fun unpack(mark: ByteArray, bytes: ByteArray): String? {
            if (bytes.size <= mark.size || bytes[0] != mark[0] || bytes[1] != mark[1]) return null
            val i = Inflater(true)
            return try {
                i.setInput(bytes, mark.size, bytes.size - mark.size)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (!i.finished()) {
                    val n = i.inflate(buf)
                    if (n == 0 && (i.needsInput() || i.needsDictionary())) return null
                    out.write(buf, 0, n)
                    if (out.size() > MAX_SONG_TEXT) return null
                }
                out.toString(Charsets.UTF_8.name())
            } catch (_: DataFormatException) {
                null
            } finally {
                i.end()
            }
        }

        private const val MAX_SONG_TEXT = 256 * 1024

        /** As mensagens (texto) de uma resposta do ntfy em JSON por linha. */
        fun messages(lines: List<String>): List<String> = lines.mapNotNull { l ->
            runCatching {
                val o = json.parseToJsonElement(l).jsonObject
                if (o["event"]?.jsonPrimitive?.content == "message") o["message"]?.jsonPrimitive?.content else null
            }.getOrNull()
        }
    }
}
