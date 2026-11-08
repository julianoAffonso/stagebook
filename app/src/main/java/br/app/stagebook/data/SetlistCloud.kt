package br.app.stagebook.data

import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.BandCrypto
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.ShareCode
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
 * Setlist (and songs) over the internet, with no server of ours: the QR package (`Q1` + deflate) and each song (`S1` +
 * deflate), encrypted with the code's key, go to an ntfy.sh channel (free public service; rules checked on 24/10:
 * messages up to 4 KB, kept 12 h, "best effort"). The receiver types the code; the app fetches the channel, decrypts
 * and opens the same review as the QR.
 */
@Singleton
class SetlistCloud @Inject constructor(private val repository: LibraryRepository, private val identity: DeviceIdentity) {
    /**
     * Song that goes along with the setlist (the ChordPro text, with chords: the receiver shows it according to their
     * profile).
     */
    @Serializable
    data class SharedSong(val title: String, val author: String = "", val year: String = "", val text: String = "") {
        /** Only lyrics/chords in plain text and song-sized (see [br.app.stagebook.domain.BandContent]). */
        fun allowed() = br.app.stagebook.domain.BandContent.songOk(title, author, year, text)
    }

    /** [skipped]: songs too big for one message (rare), which didn't go. [from]: the band name it went with (for the link). */
    data class Sent(val code: String, val songs: Int, val skipped: Int, val from: String = "")

    sealed interface Received {
        /** [from]: the sender (their device), to join "the band" on accepting. */
        // [others]: more setlists in the same send (several setlists shared at once, user 01/11)
        data class Ok(val text: String, val songs: List<SharedSong> = emptyList(), val from: BandProtocol.Hello? = null, val others: List<String> = emptyList()) : Received
        data object NotFound : Received   // wrong code or past the 12 h
        data object Offline : Received
        /** Came from a newer Stagebook (a format this app doesn't know yet): update to receive. */
        data class Newer(val app: String) : Received
        /**
         * An invite to the band (user, 07/11): only who invites, to accept. [used]: someone else's "ok" is already on
         * the channel, and an invite lets in only the first one.
         */
        data class Invite(val from: BandProtocol.Hello, val used: Boolean = false) : Received
    }

    /**
     * Sends the setlist (and, with [withSongs], each song in its own message, before the setlist: the receiver never
     * sees the setlist without the songs). Returns the code, or null if it failed (no internet, service down, setlist
     * too big).
     */
    /**
     * A setlist's package: the list (the same as the QR) and, with [withSongs], the songs. Works for the internet and
     * the band.
     */
    fun setlistPackage(setId: String, withSongs: Boolean): Pair<ByteArray, List<SharedSong>>? {
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return null
        val packed = repository.qrPayload(setId) ?: return null
        return packed to if (withSongs) set.items.mapNotNull(lib::song).map { it.shared() } else emptyList()
    }

    /**
     * Loose songs (from the Songs tab), with no setlist: the list goes in the same format as a setlist, **with no
     * name**, which is how the receiver knows they're just songs for the library.
     */
    fun songsPackage(songIds: List<String>): Pair<ByteArray, List<SharedSong>>? {
        val songs = songIds.mapNotNull(repository.library.value::song)
        if (songs.isEmpty()) return null
        val list = (listOf(QrPayload.HEADER, songs.size.toString(), "") + songs.map { listOf(it.title, it.author).joinToString("\t") { s -> s.replace('\n', ' ').replace('\t', ' ').trim() }.trimEnd('\t') })
            .joinToString("\n")
        return QrPayload.pack(list) to songs.map { it.shared() }
    }

    private fun br.app.stagebook.data.model.Song.shared() = SharedSong(title, author, year, text)

    suspend fun send(setId: String, withSongs: Boolean): Sent? = setlistPackage(setId, withSongs)?.let { (p, s) -> sendPackage(p, s) }

    suspend fun sendSongs(songIds: List<String>): Sent? = songsPackage(songIds)?.let { (p, s) -> sendPackage(p, s) }

    /** Several setlists in one send (one code): each list, and each song only once even if in more than one. */
    suspend fun sendMany(setIds: List<String>, withSongs: Boolean): Sent? {
        val packages = setIds.mapNotNull { setlistPackage(it, withSongs) }
        if (packages.isEmpty()) return null
        val songs = packages.flatMap { it.second }.distinctBy { br.app.stagebook.domain.TextKey.of(it.title) + "\t" + br.app.stagebook.domain.TextKey.of(it.author) }
        return sendPackages(packages.map { it.first }, songs)
    }

    private suspend fun sendPackage(packed: ByteArray, songs: List<SharedSong>): Sent? = sendPackages(listOf(packed), songs)

    // each song in its own message and the lists last: the receiver never sees a list without the songs
    private suspend fun sendPackages(lists: List<ByteArray>, songs: List<SharedSong>): Sent? = withContext(Dispatchers.IO) {
        val code = ShareCode.generate()
        val keys = ShareCode.keys(code)
        var sent = 0
        var skipped = 0
        // the sender goes along (encrypted): whoever accepts saves this device as "band"
        if (!post(keys.topic, ShareCode.encrypt(keys.key, pack(ID_MARK, json.encodeToString(identity.hello()))))) return@withContext null
        for (song in songs) {
            val body = ShareCode.encrypt(keys.key, pack(SONG_MARK, json.encodeToString(song)))
            when {
                body.length > MAX_MESSAGE -> skipped++
                post(keys.topic, body) -> sent++
                else -> return@withContext null
            }
        }
        for (packed in lists) {
            val body = ShareCode.encrypt(keys.key, packed)
            if (body.length > MAX_MESSAGE || !post(keys.topic, body)) return@withContext null
        }
        Sent(code, sent, skipped, identity.name.value)
    }

    /** Essential mode (T19): "Internet" off, nothing leaves from here (sends, inbox, receipts, requests, 3 words). */
    private val internetOn get() = repository.library.value.look.internetOn

    private fun post(topic: String, body: String): Boolean = if (!internetOn) false else runCatching {
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

    /**
     * An invite to the band over the internet (user, 07/11), carrying only who invites (and the invite mark). Its code
     * has a secret beyond the 3 words (see [ShareCode.generateInvite]): the channel is only found with the link.
     * Whoever accepts joins the band and sends back the usual "ok". Lasts the service's 12 h.
     */
    suspend fun sendInvite(): String? = withContext(Dispatchers.IO) {
        val code = ShareCode.generateInvite()
        val keys = ShareCode.keys(code)
        val sent = invite(identity.hello()).all { post(keys.topic, ShareCode.encrypt(keys.key, it)) }
        code.takeIf { sent }
    }

    /** An invite's messages, before encrypting: who invites, then the mark. */
    // the mark carries a word: an empty deflate reads back as nothing. "invite" is version 1 (3 words only), "invite:N" the
    // later ones (2: the code has a secret)
    internal fun invite(from: BandProtocol.Hello, version: Int = INVITE_VERSION): List<ByteArray> =
        listOf(pack(ID_MARK, json.encodeToString(from)), pack(INVITE_MARK, if (version <= 1) "invite" else "invite:$version"))

    suspend fun receive(input: String): Received = withContext(Dispatchers.IO) {
        val code = ShareCode.normalize(input) ?: return@withContext Received.NotFound
        val keys = ShareCode.keys(code)
        if (!internetOn) return@withContext Received.Offline
        val lines = get("$SERVER/${keys.topic}/json?poll=1&since=all") ?: return@withContext Received.Offline
        // only what decrypts with this code (the channel is public: there may be junk from someone else); every setlist
        // in the order sent (several setlists can go in one send)
        read(messages(lines).mapNotNull { ShareCode.decrypt(keys.key, it) })
    }

    /** What a send's decrypted messages are: a setlist with its songs, an invite, or nothing readable. */
    internal fun read(plain: List<ByteArray>): Received {
        // only setlists and songs in plain text (BandContent): the rest is discarded
        val lists = plain.mapNotNull { QrPayload.unpack(it) }.filter(br.app.stagebook.domain.BandContent::listOk).distinct()
        val text = lists.firstOrNull()
        val songs = plain.mapNotNull { unpack(SONG_MARK, it) }.mapNotNull { runCatching { json.decodeFromString<SharedSong>(it) }.getOrNull() }
            .filter { it.allowed() }.take(br.app.stagebook.domain.BandContent.MAX_SONGS)
        val from = plain.firstNotNullOfOrNull { unpack(ID_MARK, it) }?.let(::hello)
        val inviteBodies = plain.mapNotNull { unpack(INVITE_MARK, it) }
        val invite = inviteBodies.isNotEmpty()
        val inviteVersion = inviteBodies.maxOfOrNull { it.substringAfter(':', "1").toIntOrNull() ?: 1 } ?: 1
        return when {
            from != null && from.v > BandProtocol.VERSION -> Received.Newer(from.app)
            invite && inviteVersion > INVITE_VERSION -> Received.Newer(from?.app.orEmpty())   // a format this app doesn't know
            // my own invite (the link tapped in a chat by who sent it) has nobody to let in: it is nothing to accept
            text == null && invite && from != null && from.id == identity.id -> Received.NotFound
            text == null && invite && from != null -> Received.Invite(
                from,
                // mine doesn't count: opening the same link again after accepting
                used = plain.mapNotNull { unpack(ACK_MARK, it) }.mapNotNull(::hello).any { it.id != identity.id && it.id != from.id },
            )
            text == null -> Received.NotFound
            else -> Received.Ok(text, songs, from, lists.drop(1))
        }
    }

    // identity checked: the id must match the key (nobody passes as another device)
    private fun hello(text: String): BandProtocol.Hello? = runCatching { json.decodeFromString<BandProtocol.Hello>(text) }.getOrNull()
        ?.takeIf { BandCrypto.publicKey(it.key) != null && BandCrypto.idOf(it.key) == it.id && it.name.length <= BandProtocol.MAX_NAME }

    /**
     * Whoever received through the words and accepted sends back an "ok" with their identity, on the same channel
     * (joins the band on both sides).
     */
    suspend fun ack(input: String): Boolean = withContext(Dispatchers.IO) {
        val code = ShareCode.normalize(input) ?: return@withContext false
        val keys = ShareCode.keys(code)
        post(keys.topic, ShareCode.encrypt(keys.key, ackMessage(identity.hello())))
    }

    /** An "ok" before encrypting: who accepted. */
    internal fun ackMessage(from: BandProtocol.Hello): ByteArray = pack(ACK_MARK, json.encodeToString(from))

    /** The "ok"s that arrived for one of my sends (who accepted); empty with no internet. */
    suspend fun acks(code: String): List<BandProtocol.Hello> = withContext(Dispatchers.IO) {
        val keys = ShareCode.keys(code)
        val lines = get("$SERVER/${keys.topic}/json?poll=1&since=all") ?: return@withContext emptyList()
        messages(lines).mapNotNull { ShareCode.decrypt(keys.key, it) }.mapNotNull { unpack(ACK_MARK, it) }.mapNotNull(::hello)
            .filter { it.id != identity.id }.distinctBy { it.id }
    }

    // ---- each band pair's inbox, over the internet ----

    /**
     * A message in a pair's inbox: a package chunk ([k] = "pkg"; up to ~2 KB, ntfy takes 4 KB and encryption/Base64 add
     * size), a package receipt ("rcpt", [p] = the package) or the "welcome" from someone who started trusting
     * ("welcome").
     */
    @Serializable
    private data class Chunk(val p: String, val i: Int = 0, val n: Int = 1, val d: String = "", val k: String = "pkg")

    /**
     * What arrived in a pair's inbox: packages (id, list, songs), receipts for my sends and whether they welcomed me.
     */
    class PairFetch(
        val packages: List<Triple<String, String, List<SharedSong>>>,
        val receipts: List<String>,
        val welcome: Boolean,
        val last: String?,
        val requests: List<List<BandLink.Wanted>> = emptyList(),   // "send me these songs" (T12)
    )

    @Serializable
    private data class Pack(val list: String, val songs: List<SharedSong> = emptyList(), val v: Int = BandProtocol.VERSION)

    /** [recipientId]'s inbox channel for the pair: derived from the pair key (only the two know which it is). */
    fun pairTopic(pairKey: ByteArray, recipientId: String): String =
        "sb" + java.security.MessageDigest.getInstance("SHA-256").apply { update(pairKey); update("inbox|$recipientId".toByteArray()) }
            .digest().copyOf(16).joinToString("") { "%02x".format(it) }

    /**
     * Sends a package (fixed [id]: the resend uses the same one) to a band device's inbox; true if every chunk went.
     */
    suspend fun sendToPair(pairKey: ByteArray, recipientId: String, list: ByteArray, songs: List<SharedSong>, id: String): Boolean = withContext(Dispatchers.IO) {
        val whole = pack(PAIR_MARK, json.encodeToString(Pack(java.util.Base64.getEncoder().encodeToString(list), songs)))
        val parts = whole.toList().chunked(CHUNK).map { it.toByteArray() }
        val topic = pairTopic(pairKey, recipientId)
        parts.withIndex().all { (i, part) ->
            val chunk = Chunk(id, i, parts.size, java.util.Base64.getEncoder().encodeToString(part))
            post(topic, ShareCode.encrypt(pairKey, json.encodeToString(chunk).toByteArray()))
        }
    }

    /** A package receipt, in the sender's inbox (so they stop resending). */
    suspend fun receipt(pairKey: ByteArray, senderId: String, packageId: String) = withContext(Dispatchers.IO) {
        post(pairTopic(pairKey, senderId), ShareCode.encrypt(pairKey, json.encodeToString(Chunk(packageId, k = "rcpt")).toByteArray()))
    }

    /** "You're in my band now", in the other one's inbox (their "ok" arrived; they stop repeating). */
    suspend fun welcome(pairKey: ByteArray, recipientId: String) = withContext(Dispatchers.IO) {
        post(pairTopic(pairKey, recipientId), ShareCode.encrypt(pairKey, json.encodeToString(Chunk("", k = "welcome")).toByteArray()))
    }

    /** Asks the pair for the missing songs (T12): goes to their inbox and they answer with a songs package. */
    suspend fun requestFromPair(pairKey: ByteArray, recipientId: String, wanted: List<BandLink.Wanted>) = withContext(Dispatchers.IO) {
        val body = java.util.Base64.getEncoder().encodeToString(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(BandLink.Wanted.serializer()), wanted.take(MAX_REQUEST)).toByteArray())
        post(pairTopic(pairKey, recipientId), ShareCode.encrypt(pairKey, json.encodeToString(Chunk("", k = "req", d = body)).toByteArray()))
    }

    /** What arrived in my inbox from a pair, since [since] (null = the last 12 h); null with no internet. */
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
            // a whole package (setlist and every song) passes the single-song cap: 30 songs already did (audit 01/11)
            val pkg = unpack(PAIR_MARK, bytes, MAX_PACKAGE)?.let { runCatching { json.decodeFromString<Pack>(it) }.getOrNull() } ?: return@mapNotNull null
            if (pkg.v > BandProtocol.VERSION) return@mapNotNull Triple(pid, NEWER, emptyList())   // the inbox says "update"
            val text = QrPayload.unpack(java.util.Base64.getDecoder().decode(pkg.list)) ?: return@mapNotNull null
            Triple(pid, text, pkg.songs)
        }
        // requests for missing songs (T12)
        val requests = all.filter { it.k == "req" }.mapNotNull { c ->
            runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(BandLink.Wanted.serializer()), String(java.util.Base64.getDecoder().decode(c.d))) }.getOrNull()
        }
        PairFetch(packages, all.filter { it.k == "rcpt" }.map { it.p }, all.any { it.k == "welcome" }, msgs.lastOrNull()?.first ?: since, requests)
    }

    /** The words' "ok", encrypted (to also deliver over the local network, when the two meet). */
    fun ackBody(input: String): String? {
        val code = ShareCode.normalize(input) ?: return null
        return ShareCode.encrypt(ShareCode.keys(code).key, ackMessage(identity.hello()))
    }

    /**
     * Checks an "ok" that arrived over the local network against my sends ([codes]): the identity of who accepted, or
     * null.
     */
    fun checkAckBody(body: String, codes: List<String>): BandProtocol.Hello? = codes.firstNotNullOfOrNull { code ->
        ShareCode.decrypt(ShareCode.keys(code).key, body)?.let { unpack(ACK_MARK, it) }?.let(::hello)
    }

    private fun get(url: String): List<String>? = if (!internetOn) null else runCatching {
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

        /** The invite's format: 1 = the 3 words open it (still read), 2 = it also needs the secret from its link. */
        const val INVITE_VERSION = 2
        private const val TIMEOUT_MS = 10_000
        private const val MAX_MESSAGE = 4_000          // ntfy.sh takes up to 4,096 bytes per message
        private const val USER_AGENT = "StagebookApp/0.2 (setlist da banda)"
        private val json = Json { ignoreUnknownKeys = true }
        private const val MAX_REQUEST = 200   // a setlist never goes over this
        private val SONG_MARK = byteArrayOf('S'.code.toByte(), '1'.code.toByte())
        private val ID_MARK = byteArrayOf('I'.code.toByte(), '1'.code.toByte())     // sender (identity)
        private val ACK_MARK = byteArrayOf('A'.code.toByte(), '1'.code.toByte())    // "ok" from someone who accepted
        private val PAIR_MARK = byteArrayOf('P'.code.toByte(), '1'.code.toByte())   // package from a pair's inbox
        private val INVITE_MARK = byteArrayOf('V'.code.toByte(), '1'.code.toByte())   // an invite to the band, nothing else
        private const val CHUNK = 2_000
        /** Marks, in the inbox, a send from a newer version (can't be read: the review asks to update). */
        const val NEWER = "\u0000newer"

        /** The messages with each one's id (to continue from where it stopped). */
        fun messagesWithIds(lines: List<String>): List<Pair<String, String>> = lines.mapNotNull { l ->
            runCatching {
                val o = json.parseToJsonElement(l).jsonObject
                if (o["event"]?.jsonPrimitive?.content != "message") return@runCatching null
                val id = o["id"]?.jsonPrimitive?.content ?: return@runCatching null
                id to (o["message"]?.jsonPrimitive?.content ?: return@runCatching null)
            }.getOrNull()
        }

        /** [mark] + deflate of the text (the same way as the QR package, with another mark for songs). */
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

        /** The text packed with [mark], or null if it isn't one or it opens to more than [max] bytes. */
        fun unpack(mark: ByteArray, bytes: ByteArray, max: Int = MAX_SONG_TEXT): String? {
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
                    if (out.size() > max) return null
                }
                out.toString(Charsets.UTF_8.name())
            } catch (_: DataFormatException) {
                null
            } finally {
                i.end()
            }
        }

        private const val MAX_SONG_TEXT = 256 * 1024
        const val MAX_PACKAGE = 16 * 1024 * 1024

        /** The messages (text) of an ntfy answer in JSON per line. */
        fun messages(lines: List<String>): List<String> = messagesWithIds(lines).map { it.second }
    }
}
