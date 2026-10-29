package com.juliano.stagebook.data

import com.juliano.stagebook.domain.BandProtocol
import com.juliano.stagebook.domain.BandCrypto
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
class SetlistCloud @Inject constructor(private val repository: LibraryRepository, private val identity: DeviceIdentity) {
    /** Música que vai junto com o setlist (o texto em ChordPro, com a cifra: quem recebe mostra conforme o perfil dele). */
    @Serializable
    data class SharedSong(val title: String, val author: String = "", val year: String = "", val text: String = "") {
        /** Só letra/cifra em texto puro e de tamanho de música (ver [com.juliano.stagebook.domain.BandContent]). */
        fun allowed() = com.juliano.stagebook.domain.BandContent.songOk(title, author, year, text)
    }

    /** [skipped]: músicas grandes demais para uma mensagem (raro), que não foram. */
    data class Sent(val code: String, val songs: Int, val skipped: Int)

    sealed interface Received {
        /** [from]: quem enviou (o aparelho dele), para virar "da banda" ao aceitar. */
        data class Ok(val text: String, val songs: List<SharedSong> = emptyList(), val from: BandProtocol.Hello? = null) : Received
        data object NotFound : Received   // código errado ou já passou das 12 h
        data object Offline : Received
        /** Veio de uma versão mais nova do Stagebook (formato que este app ainda não conhece): atualizar para receber. */
        data class Newer(val app: String) : Received
    }

    /**
     * Envia o setlist (e, com [withSongs], cada música numa mensagem própria, antes do setlist: quem recebe nunca vê o
     * setlist sem as músicas). Devolve o código, ou null se não deu (sem internet, serviço fora, setlist grande demais).
     */
    /** O pacote de um setlist: a lista (o mesmo do QR) e, com [withSongs], as músicas. Vale para a internet e a banda. */
    fun setlistPackage(setId: String, withSongs: Boolean): Pair<ByteArray, List<SharedSong>>? {
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return null
        val packed = repository.qrPayload(setId) ?: return null
        return packed to if (withSongs) set.items.mapNotNull(lib::song).map { it.shared() } else emptyList()
    }

    /**
     * Músicas avulsas (da aba Músicas), sem setlist: a lista vai no mesmo formato do setlist, **sem nome** — é assim que
     * quem recebe sabe que são só músicas para a biblioteca.
     */
    fun songsPackage(songIds: List<String>): Pair<ByteArray, List<SharedSong>>? {
        val songs = songIds.mapNotNull(repository.library.value::song)
        if (songs.isEmpty()) return null
        val list = (listOf(QrPayload.HEADER, songs.size.toString(), "") + songs.map { listOf(it.title, it.author).joinToString("\t") { s -> s.replace('\n', ' ').replace('\t', ' ').trim() }.trimEnd('\t') })
            .joinToString("\n")
        return QrPayload.pack(list) to songs.map { it.shared() }
    }

    private fun com.juliano.stagebook.data.model.Song.shared() = SharedSong(title, author, year, text)

    suspend fun send(setId: String, withSongs: Boolean): Sent? = setlistPackage(setId, withSongs)?.let { (p, s) -> sendPackage(p, s) }

    suspend fun sendSongs(songIds: List<String>): Sent? = songsPackage(songIds)?.let { (p, s) -> sendPackage(p, s) }

    // cada música numa mensagem própria e a lista por último: quem recebe nunca vê a lista sem as músicas
    private suspend fun sendPackage(packed: ByteArray, songs: List<SharedSong>): Sent? = withContext(Dispatchers.IO) {
        val code = ShareCode.generate()
        val keys = ShareCode.keys(code)
        var sent = 0
        var skipped = 0
        // quem envia vai junto (cifrado): quem aceitar guarda este aparelho como "da banda"
        if (!post(keys.topic, ShareCode.encrypt(keys.key, pack(ID_MARK, json.encodeToString(identity.hello()))))) return@withContext null
        for (song in songs) {
            val body = ShareCode.encrypt(keys.key, pack(SONG_MARK, json.encodeToString(song)))
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
        // só setlist e músicas em texto puro (BandContent): o resto é descartado
        val text = plain.asReversed().firstNotNullOfOrNull { QrPayload.unpack(it) }?.takeIf(com.juliano.stagebook.domain.BandContent::listOk)
        val songs = plain.mapNotNull { unpack(SONG_MARK, it) }.mapNotNull { runCatching { json.decodeFromString<SharedSong>(it) }.getOrNull() }
            .filter { it.allowed() }.take(com.juliano.stagebook.domain.BandContent.MAX_SONGS)
        val from = plain.firstNotNullOfOrNull { unpack(ID_MARK, it) }?.let(::hello)
        when {
            from != null && from.v > BandProtocol.VERSION -> Received.Newer(from.app)
            text == null -> Received.NotFound
            else -> Received.Ok(text, songs, from)
        }
    }

    // identidade conferida: o id tem de ser o da chave (ninguém se passa por outro aparelho)
    private fun hello(text: String): BandProtocol.Hello? = runCatching { json.decodeFromString<BandProtocol.Hello>(text) }.getOrNull()
        ?.takeIf { BandCrypto.publicKey(it.key) != null && BandCrypto.idOf(it.key) == it.id && it.name.length <= BandProtocol.MAX_NAME }

    /** Quem recebeu pelas palavras e aceitou devolve um "ok" com a identidade dele, no mesmo canal (vira da banda dos dois lados). */
    suspend fun ack(input: String): Boolean = withContext(Dispatchers.IO) {
        val code = ShareCode.normalize(input) ?: return@withContext false
        val keys = ShareCode.keys(code)
        post(keys.topic, ShareCode.encrypt(keys.key, pack(ACK_MARK, json.encodeToString(identity.hello()))))
    }

    /** Os "ok" que chegaram para um envio meu (quem aceitou); vazio sem internet. */
    suspend fun acks(code: String): List<BandProtocol.Hello> = withContext(Dispatchers.IO) {
        val keys = ShareCode.keys(code)
        val lines = get("$SERVER/${keys.topic}/json?poll=1&since=all") ?: return@withContext emptyList()
        messages(lines).mapNotNull { ShareCode.decrypt(keys.key, it) }.mapNotNull { unpack(ACK_MARK, it) }.mapNotNull(::hello)
            .filter { it.id != identity.id }.distinctBy { it.id }
    }

    // ---- caixa de entrada de cada par da banda, pela internet ----

    /**
     * Uma mensagem da caixa de um par: um pedaço de pacote ([k] = "pkg"; até ~2 KB, o ntfy aceita 4 KB e a cifra/Base64
     * aumentam), o recibo de um pacote ("rcpt", [p] = o pacote) ou o "bem-vindo" de quem passou a confiar ("welcome").
     */
    @Serializable
    private data class Chunk(val p: String, val i: Int = 0, val n: Int = 1, val d: String = "", val k: String = "pkg")

    /** O que chegou na caixa de um par: pacotes (id, lista, músicas), recibos dos meus envios e se ele me deu as boas-vindas. */
    class PairFetch(val packages: List<Triple<String, String, List<SharedSong>>>, val receipts: List<String>, val welcome: Boolean, val last: String?)

    @Serializable
    private data class Pack(val list: String, val songs: List<SharedSong> = emptyList(), val v: Int = BandProtocol.VERSION)

    /** Canal da caixa de entrada de [recipientId] para o par: sai da chave do par (só os dois sabem qual é). */
    fun pairTopic(pairKey: ByteArray, recipientId: String): String =
        "sb" + java.security.MessageDigest.getInstance("SHA-256").apply { update(pairKey); update("inbox|$recipientId".toByteArray()) }
            .digest().copyOf(16).joinToString("") { "%02x".format(it) }

    /** Manda um pacote ([id] fixo: o reenvio usa o mesmo) para a caixa de um aparelho da banda; true se todos os pedaços foram. */
    suspend fun sendToPair(pairKey: ByteArray, recipientId: String, list: ByteArray, songs: List<SharedSong>, id: String): Boolean = withContext(Dispatchers.IO) {
        val whole = pack(PAIR_MARK, json.encodeToString(Pack(java.util.Base64.getEncoder().encodeToString(list), songs)))
        val parts = whole.toList().chunked(CHUNK).map { it.toByteArray() }
        val topic = pairTopic(pairKey, recipientId)
        parts.withIndex().all { (i, part) ->
            val chunk = Chunk(id, i, parts.size, java.util.Base64.getEncoder().encodeToString(part))
            post(topic, ShareCode.encrypt(pairKey, json.encodeToString(chunk).toByteArray()))
        }
    }

    /** Recibo de um pacote, na caixa de quem mandou (para ele parar de reenviar). */
    suspend fun receipt(pairKey: ByteArray, senderId: String, packageId: String) = withContext(Dispatchers.IO) {
        post(pairTopic(pairKey, senderId), ShareCode.encrypt(pairKey, json.encodeToString(Chunk(packageId, k = "rcpt")).toByteArray()))
    }

    /** "Agora você é da minha banda", na caixa do outro (o "ok" dele chegou; ele para de repetir). */
    suspend fun welcome(pairKey: ByteArray, recipientId: String) = withContext(Dispatchers.IO) {
        post(pairTopic(pairKey, recipientId), ShareCode.encrypt(pairKey, json.encodeToString(Chunk("", k = "welcome")).toByteArray()))
    }

    /** O que chegou na minha caixa vinda de um par, desde [since] (null = as últimas 12 h); null sem internet. */
    suspend fun fetchPair(pairKey: ByteArray, since: String?): PairFetch? = withContext(Dispatchers.IO) {
        val lines = get("$SERVER/${pairTopic(pairKey, identity.id)}/json?poll=1&since=${since ?: "12h"}") ?: return@withContext null
        val msgs = messagesWithIds(lines)
        val all = msgs.mapNotNull { (_, m) -> ShareCode.decrypt(pairKey, m)?.let { runCatching { json.decodeFromString<Chunk>(it.decodeToString()) }.getOrNull() } }
        val chunks = all.filter { it.k == "pkg" }
        val packages = chunks.groupBy { it.p }.entries.mapNotNull { (pid, parts) ->
            val n = parts.first().n
            val ordered = parts.distinctBy { it.i }.sortedBy { it.i }
            if (ordered.size != n) return@mapNotNull null
            val bytes = ordered.fold(ByteArray(0)) { acc, c -> acc + java.util.Base64.getDecoder().decode(c.d) }
            val pkg = unpack(PAIR_MARK, bytes)?.let { runCatching { json.decodeFromString<Pack>(it) }.getOrNull() } ?: return@mapNotNull null
            if (pkg.v > BandProtocol.VERSION) return@mapNotNull Triple(pid, NEWER, emptyList())   // a caixa avisa "atualize"
            val text = QrPayload.unpack(java.util.Base64.getDecoder().decode(pkg.list)) ?: return@mapNotNull null
            Triple(pid, text, pkg.songs)
        }
        PairFetch(packages, all.filter { it.k == "rcpt" }.map { it.p }, all.any { it.k == "welcome" }, msgs.lastOrNull()?.first ?: since)
    }

    /** O "ok" das palavras cifrado (para entregar também pela rede local, quando os dois se encontram). */
    fun ackBody(input: String): String? {
        val code = ShareCode.normalize(input) ?: return null
        return ShareCode.encrypt(ShareCode.keys(code).key, pack(ACK_MARK, json.encodeToString(identity.hello())))
    }

    /** Confere um "ok" que chegou pela rede local contra os meus envios ([codes]): a identidade de quem aceitou, ou null. */
    fun checkAckBody(body: String, codes: List<String>): BandProtocol.Hello? = codes.firstNotNullOfOrNull { code ->
        ShareCode.decrypt(ShareCode.keys(code).key, body)?.let { unpack(ACK_MARK, it) }?.let(::hello)
    }

    private fun get(url: String): List<String>? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().readLines()
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    companion object {
        private const val SERVER = "https://ntfy.sh"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_MESSAGE = 4_000          // o ntfy.sh aceita até 4.096 bytes por mensagem
        private const val USER_AGENT = "StagebookApp/0.2 (setlist da banda)"
        private val json = Json { ignoreUnknownKeys = true }
        private val SONG_MARK = byteArrayOf('S'.code.toByte(), '1'.code.toByte())
        private val ID_MARK = byteArrayOf('I'.code.toByte(), '1'.code.toByte())     // quem enviou (identidade)
        private val ACK_MARK = byteArrayOf('A'.code.toByte(), '1'.code.toByte())    // "ok" de quem aceitou
        private val PAIR_MARK = byteArrayOf('P'.code.toByte(), '1'.code.toByte())   // pacote da caixa de um par
        private const val CHUNK = 2_000
        /** Marca, na caixa, de um envio de versão mais nova (não dá para ler: a conferência pede para atualizar). */
        const val NEWER = "\u0000newer"

        /** As mensagens com o id de cada uma (para continuar de onde parou). */
        fun messagesWithIds(lines: List<String>): List<Pair<String, String>> = lines.mapNotNull { l ->
            runCatching {
                val o = json.parseToJsonElement(l).jsonObject
                if (o["event"]?.jsonPrimitive?.content != "message") return@runCatching null
                val id = o["id"]?.jsonPrimitive?.content ?: return@runCatching null
                id to (o["message"]?.jsonPrimitive?.content ?: return@runCatching null)
            }.getOrNull()
        }

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
