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
class SetlistCloud @Inject constructor(
    private val repository: LibraryRepository,
    private val identity: DeviceIdentity,
    // the songs' PDFs: read to leave, checked and saved on arrival. Without a store (some tests) no PDF goes or comes
    private val pdfs: PdfStore? = null,
    private val inspector: PdfInspector = PdfInspector(),
) {
    /**
     * Song that goes along with the setlist (the ChordPro text, with chords: the receiver shows it according to their
     * profile).
     */
    @Serializable
    data class SharedSong(
        val title: String, val author: String = "", val year: String = "", val text: String = "",
        // the song's PDF. [pdf]: its bytes in base64, only on the wire between band devices (never kept, never in the
        // inbox); [pdfName]: its own name, to show. [pdfFile]: the file in THIS device's PdfStore: on the sender, what to
        // read at send time; on the receiver, what was checked and saved. The wire never carries it (see [forWire]).
        // [pdfProblem]: a [PdfProblem] name, when what came was left out; the inbox says why
        val pdf: String = "", val pdfName: String = "", val pdfFile: String = "", val pdfProblem: String = "",
        // over the internet (user, 22/11): where the server keeps its PDF, compressed and encrypted with the send's key (an
        // attachment that lasts a few hours). Fetched and checked on arrival; never kept
        val pdfAt: String = "",
    ) {
        /** Only lyrics/chords in plain text and song-sized (see [br.app.stagebook.domain.BandContent]). */
        fun allowed() = br.app.stagebook.domain.BandContent.songOk(title, author, year, text)

        /** The same song with nothing about a PDF (what the internet and the QR carry, for now). */
        fun forWire(): SharedSong = if (pdf.isEmpty() && pdfName.isEmpty() && pdfFile.isEmpty() && pdfProblem.isEmpty() && pdfAt.isEmpty()) this
        else copy(pdf = "", pdfName = "", pdfFile = "", pdfProblem = "", pdfAt = "")

        /** Which problem [pdfProblem] names, if any. */
        val problem: br.app.stagebook.data.PdfProblem? get() = br.app.stagebook.data.PdfProblem.entries.firstOrNull { it.name == pdfProblem }
    }

    /** [skipped]: songs too big for one message (rare), which didn't go. [from]: the band name it went with (for the link). */
    data class Sent(val code: String, val songs: Int, val skipped: Int, val from: String = "", val pdfs: Int = 0)

    sealed interface Received {
        /** [from]: the sender (their device), to join "the band" on accepting. */
        // [others]: more setlists in the same send (several setlists shared at once, user 01/11)
        // [breaks]: each setlist's breaks, by its text; a setlist that isn't there said nothing about breaks (an older app)
        data class Ok(
            val text: String, val songs: List<SharedSong> = emptyList(), val from: BandProtocol.Hello? = null, val others: List<String> = emptyList(),
            val breaks: Map<String, List<Int>> = emptyMap(),
        ) : Received
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
        // the breaks go after the list (see SharedBreaks), counted over the songs the list really carries
        val gone = set.items.indices.filter { lib.song(set.items[it]) == null }.toSet()
        val breaks = br.app.stagebook.domain.SetlistBreaks.afterRemoving(set.breaks, set.items.size, gone)
        return br.app.stagebook.domain.SharedBreaks.pack(packed, breaks) to if (withSongs) set.items.mapNotNull(lib::song).map { it.shared() } else emptyList()
    }

    /**
     * Loose songs (from the Songs tab), with no setlist: the list goes in the same format as a setlist, **with no
     * name**, which is how the receiver knows they're just songs for the library.
     */
    fun songsPackage(songIds: List<String>): Pair<ByteArray, List<SharedSong>>? = looseSongs(songIds.mapNotNull(repository.library.value::song))


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

    /**
     * The PDFs of [songs] that go with a send by code: all of them, or none. They go only when they fit the internet's budget
     * (the send's dialog says the numbers before, see [br.app.stagebook.domain.BandContent.internetPdfsFit]); otherwise the
     * songs go without. Song to its PDF file.
     */
    internal fun pdfsFor(songs: List<SharedSong>): Map<SharedSong, java.io.File> {
        val store = pdfs ?: return emptyMap()
        val files = songs.filter { it.pdfFile.isNotEmpty() }.mapNotNull { s -> store.file(s.pdfFile).takeIf { it.isFile }?.let { s to it } }.toMap()
        return if (files.isNotEmpty() && br.app.stagebook.domain.BandContent.internetPdfsFit(files.values.map { it.length() })) files else emptyMap()
    }

    /** The sizes of the PDFs [songIds] carry, for the send's dialog to count the messages (see BandContent.internetFits). */
    fun pdfSizes(songIds: Collection<String>): List<Long> {
        val store = pdfs ?: return emptyList()
        val lib = repository.library.value
        return songIds.mapNotNull(lib::song).filter { it.pdf.isNotEmpty() }.mapNotNull { store.file(it.pdf).takeIf { f -> f.isFile }?.length() }
    }

    // each song in its own message, then its PDF in chunks, and the lists last: the receiver never sees a list without the songs
    private suspend fun sendPackages(lists: List<ByteArray>, songs: List<SharedSong>): Sent? = withContext(Dispatchers.IO) {
        val code = ShareCode.generate()
        val keys = ShareCode.keys(code)
        var sent = 0
        var skipped = 0
        val withPdf = pdfsFor(songs)
        var attached = 0
        // the sender goes along (encrypted): whoever accepts saves this device as "band". A send with PDFs is a version 5 one
        // (they go as attachments, which an older app wouldn't fetch)
        if (!post(keys.topic, ShareCode.encrypt(keys.key, pack(ID_MARK, json.encodeToString(identity.hello(if (withPdf.isNotEmpty()) BandProtocol.ATTACH_VERSION else lists.maxOfOrNull { BandProtocol.packageVersion(it) } ?: BandProtocol.PACKAGE_BASE)))))) return@withContext null
        for (song in songs) {
            // its PDF first, compressed and encrypted, as an attachment; a PDF the server didn't take leaves the song without it
            val link = withPdf[song]?.let { attach(keys.topic, keys.key, it) }
            val wire = song.forWire().let { w -> if (link != null) w.copy(pdfAt = link, pdfName = song.pdfName.take(br.app.stagebook.domain.BandContent.MAX_PDF_NAME)) else w }
            val body = ShareCode.encrypt(keys.key, pack(SONG_MARK, json.encodeToString(wire)))
            when {
                body.length > MAX_MESSAGE -> skipped++
                post(keys.topic, body) -> { sent++; if (link != null) attached++ }
                else -> return@withContext null
            }
        }
        for (packed in lists) {
            val body = ShareCode.encrypt(keys.key, packed)
            if (body.length > MAX_MESSAGE || !post(keys.topic, body)) return@withContext null
        }
        Sent(code, sent, skipped, identity.name.value, attached)
    }

    /** Essential mode (T19): "Internet" off, nothing leaves from here (sends, inbox, receipts, requests, 3 words). */
    private val internetOn get() = repository.library.value.look.internetOn

    /** The tests' own network: what goes out and what comes back, with no server. */
    internal var postHook: ((topic: String, body: String) -> Boolean)? = null
    internal var getHook: ((url: String) -> List<String>?)? = null

    private fun post(topic: String, body: String): Boolean = postHook?.invoke(topic, body) ?: if (!internetOn) false else runCatching {
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
        read(messages(lines).mapNotNull { ShareCode.decrypt(keys.key, it) }) { url -> fetchAttachment(url, keys.key) }
    }

    /**
     * What one receive may download in attachments (audit 22/11). A download that fails or doesn't decrypt costs a whole
     * attachment too, and an address is asked for once: before, only what decrypted was counted, so a send made by hand
     * with 200 songs pointing at big files made the device download gigabytes.
     */
    internal class AttachBudget(private var left: Long) {
        private val asked = HashSet<String>()
        fun allows(url: String): Boolean = left > 0 && asked.add(url)
        fun charged(bytes: ByteArray?): ByteArray? { left -= bytes?.size?.toLong() ?: MAX_ATTACHMENT.toLong(); return bytes }
    }

    /** What a send's decrypted messages are: a setlist with its songs, an invite, or nothing readable. */
    internal fun read(plain: List<ByteArray>, fetch: (String) -> ByteArray? = { null }): Received {
        // only setlists and songs in plain text (BandContent): the rest is discarded
        val packs = plain.mapNotNull { p -> QrPayload.unpack(p)?.let { it to p } }.filter { br.app.stagebook.domain.BandContent.listOk(it.first) }.distinctBy { it.first }
        val lists = packs.map { it.first }
        val breaks = packs.mapNotNull { (list, p) -> br.app.stagebook.domain.SharedBreaks.read(p, list)?.let { list to it } }.toMap()
        val text = lists.firstOrNull()
        val pdfsCame = assemblePdfs(plain)
        val songs = plain.mapNotNull { unpack(SONG_MARK, it) }.mapNotNull { runCatching { json.decodeFromString<SharedSong>(it) }.getOrNull() }
            .filter { it.allowed() }.take(br.app.stagebook.domain.BandContent.MAX_SONGS)
            // what the sender says about files on this device is never believed; the PDF is whatever came in chunks
            .let { list -> val budget = AttachBudget(br.app.stagebook.domain.BandContent.INTERNET_PDF_BYTES)
                list.map { s -> s.forWire().let { clean ->
                    when {
                        // an attachment: fetched, decrypted and checked like any PDF; gone from the server, the song comes without it
                        s.pdfAt.isNotEmpty() -> if (!budget.allows(s.pdfAt)) clean.copy(pdfProblem = br.app.stagebook.data.PdfProblem.TOO_BIG.name)
                            else budget.charged(fetch(s.pdfAt))?.let { bytes -> takePdf(clean, bytes, s.pdfName) } ?: clean.copy(pdfProblem = br.app.stagebook.data.PdfProblem.EXPIRED.name)
                        // a send of version 4: the PDF came in chunks
                        else -> pdfsCame[songKey(s.title, s.author)]?.let { (bytes, name) -> takePdf(clean, bytes, name) } ?: clean
                    }
                } }
            }
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
            else -> Received.Ok(text, songs, from, lists.drop(1), breaks)
        }
    }

    /** One chunk of a song's PDF in a send by code: [k] says which song (see [songKey]), [i] of [n], [d] in base64. */
    @Serializable
    private data class PdfChunk(val k: String, val i: Int, val n: Int, val d: String, val name: String = "")

    // the PDFs that arrived complete, by song key: bytes and the name the sender gave; an incomplete or oversized one is nothing
    private fun assemblePdfs(plain: List<ByteArray>): Map<String, Pair<ByteArray, String>> {
        val chunks = plain.mapNotNull { unpack(PDF_MARK, it) }.mapNotNull { runCatching { json.decodeFromString<PdfChunk>(it) }.getOrNull() }
        // a real send has at most MAX_INTERNET_MESSAGES messages in all, chunks included: more than that is not a send of ours
        // (anyone with the 3 words can post to the channel), and a flood of chunks is not put together
        val maxChunks = br.app.stagebook.domain.BandContent.MAX_INTERNET_MESSAGES
        if (chunks.size > maxChunks) return emptyMap()
        return chunks.groupBy { it.k }.mapNotNull { (key, parts) ->
            val n = parts.first().n
            val ordered = parts.filter { it.n == n }.distinctBy { it.i }.sortedBy { it.i }
            if (n !in 1..maxChunks || ordered.size != n || ordered.indices.any { ordered[it].i != it }) return@mapNotNull null
            val bytes = runCatching { ordered.fold(java.io.ByteArrayOutputStream()) { acc, c -> acc.also { it.write(java.util.Base64.getDecoder().decode(c.d)) } }.toByteArray() }.getOrNull()
                ?.takeIf { it.size <= br.app.stagebook.domain.BandContent.MAX_PDF_BYTES } ?: return@mapNotNull null
            key to (bytes to parts.first().name)
        }.toMap()
    }

    // a PDF that came by code: checked and saved under a generated name, or left out with the reason
    private fun takePdf(song: SharedSong, bytes: ByteArray, name: String): SharedSong {
        val store = pdfs ?: return song
        return when (val saved = store.saveCheckedNow(java.util.UUID.randomUUID().toString(), bytes, inspector)) {
            is PdfAttach.Saved -> song.copy(pdfFile = saved.name, pdfName = name.substringAfterLast('/').substringAfterLast('\\').filter { !it.isISOControl() }.trim().take(br.app.stagebook.domain.BandContent.MAX_PDF_NAME).ifEmpty { "PDF" })
            is PdfAttach.Refused -> song.copy(pdfProblem = saved.problem.name)
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
        val breaks: Map<String, List<Int>> = emptyMap(),   // each package's breaks, by its id (not there = it said nothing)
    )

    /** What a whole package from a pair's inbox holds; [text] is [NEWER] when a newer app sent it. */
    internal class Opened(val text: String, val songs: List<SharedSong>, val breaks: List<Int>?)

    /** A package for a pair's inbox, before splitting into chunks and encrypting. */
    internal fun pairPackage(list: ByteArray, songs: List<SharedSong>, links: Map<SharedSong, String> = emptyMap()): ByteArray {
        // each PDF that went as an attachment ([links], see [sendToPair]) rides as where it is; a package with them is a
        // version 5 one, so an older app says "newer version" instead of dropping them in silence
        val carried = songs.map { s -> links[s]?.let { at -> s.forWire().copy(pdfAt = at, pdfName = s.pdfName.take(br.app.stagebook.domain.BandContent.MAX_PDF_NAME)) } ?: s.forWire() }
        val v = if (links.isNotEmpty()) BandProtocol.ATTACH_VERSION else BandProtocol.packageVersion(list)
        return pack(PAIR_MARK, json.encodeToString(Pack(java.util.Base64.getEncoder().encodeToString(list), carried, v)))
    }

    /** The songs' PDF files that go in one pair package: each within the PDF limit, all together within [BandContent.PAIR_PDF_BYTES]. */
    internal fun pairPdfs(songs: List<SharedSong>): Map<SharedSong, java.io.File> {
        val store = pdfs ?: return emptyMap()
        var left = br.app.stagebook.domain.BandContent.PAIR_PDF_BYTES
        val out = LinkedHashMap<SharedSong, java.io.File>()
        for (s in songs) {
            val f = s.pdfFile.takeIf { it.isNotEmpty() }?.let(store::file)?.takeIf { it.isFile } ?: continue
            if (f.length() <= left) { out[s] = f; left -= f.length() }
        }
        return out
    }

    internal fun openPairPackage(bytes: ByteArray, fetch: (String) -> ByteArray? = { null }): Opened? {
        // a whole package (setlist and every song) passes the single-song cap: 30 songs already did (audit 01/11)
        val pkg = unpack(PAIR_MARK, bytes, MAX_PACKAGE)?.let { runCatching { json.decodeFromString<Pack>(it) }.getOrNull() } ?: return null
        if (pkg.v > BandProtocol.VERSION) return Opened(NEWER, emptyList(), null)   // the inbox says "update"
        if (pkg.list.isEmpty()) return null   // the "update" notice that rides with a request: nothing to open
        val packed = runCatching { java.util.Base64.getDecoder().decode(pkg.list) }.getOrNull() ?: return null
        val text = QrPayload.unpack(packed) ?: return null
        // what the sender says about files on this device is never believed: the PDF is whatever came as bytes, checked as any
        // (the PDFs of one package share the budget the sender keeps to: a package with more is not one of ours, and a file is not
        // kept for a song that will be turned away anyway)
        val budget = AttachBudget(br.app.stagebook.domain.BandContent.PAIR_PDF_BYTES)
        var inline = OLD_PAIR_PDF_BYTES   // a version-4 package carried its PDFs inside, 60 KB at most
        val songs = pkg.songs.take(br.app.stagebook.domain.BandContent.MAX_SONGS).map { s ->
            val clean = s.forWire()
            if (!s.allowed()) return@map clean
            // an attachment (version 5): fetched, decrypted with the pair key and checked; gone from the server, it says so
            if (s.pdfAt.isNotEmpty()) {
                if (!budget.allows(s.pdfAt)) return@map clean.copy(pdfProblem = br.app.stagebook.data.PdfProblem.TOO_BIG.name)
                val got = budget.charged(fetch(s.pdfAt)) ?: return@map clean.copy(pdfProblem = br.app.stagebook.data.PdfProblem.EXPIRED.name)
                return@map takePdf(clean, got, s.pdfName)
            }
            if (s.pdf.isEmpty()) return@map clean
            // by the text's length first: nothing is decoded that could not fit
            if (s.pdf.length.toLong() * 3 / 4 > inline) return@map clean.copy(pdfProblem = br.app.stagebook.data.PdfProblem.TOO_BIG.name)
            val bytes = runCatching { java.util.Base64.getDecoder().decode(s.pdf) }.getOrNull()
                ?: return@map clean.copy(pdfProblem = br.app.stagebook.data.PdfProblem.NOT_PDF.name)
            inline -= bytes.size
            takePdf(clean, bytes, s.pdfName)
        }
        return Opened(text, songs, br.app.stagebook.domain.SharedBreaks.read(packed, text))
    }

    @Serializable
    private data class Pack(val list: String, val songs: List<SharedSong> = emptyList(), val v: Int = BandProtocol.PACKAGE_BASE)

    /** [recipientId]'s inbox channel for the pair: derived from the pair key (only the two know which it is). */
    fun pairTopic(pairKey: ByteArray, recipientId: String): String =
        "sb" + java.security.MessageDigest.getInstance("SHA-256").apply { update(pairKey); update("inbox|$recipientId".toByteArray()) }
            .digest().copyOf(16).joinToString("") { "%02x".format(it) }

    /**
     * Sends a package (fixed [id]: the resend uses the same one) to a band device's inbox; true if every chunk went.
     */
    suspend fun sendToPair(pairKey: ByteArray, recipientId: String, list: ByteArray, songs: List<SharedSong>, id: String): Boolean = withContext(Dispatchers.IO) {
        val topic = pairTopic(pairKey, recipientId)
        // the PDFs within the package's budget go first, as attachments (a resend, 12 h later, puts them up again: the
        // server had dropped them); one the server didn't take leaves its song without it
        val links = pairPdfs(songs).mapNotNull { (s, f) -> attach(topic, pairKey, f)?.let { s to it } }.toMap()
        val whole = pairPackage(list, songs, links)
        val parts = whole.toList().chunked(CHUNK).map { it.toByteArray() }
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
        val topic = pairTopic(pairKey, recipientId)
        // an app before the request ignores a "req" chunk without a word; beside it goes an empty package marked version 3,
        // which that app shows as "sent something from a newer Stagebook, update the app". This app drops it (no list)
        val notice = Chunk("req-" + Integer.toHexString(body.hashCode()), 0, 1, java.util.Base64.getEncoder().encodeToString(updateNotice()))
        // what counts is the request: with it delivered, a failed notice must not make the person ask again
        val ok = post(topic, ShareCode.encrypt(pairKey, json.encodeToString(Chunk("", k = "req", d = body)).toByteArray()))
        runCatching { post(topic, ShareCode.encrypt(pairKey, json.encodeToString(notice).toByteArray())) }
        ok
    }

    /** A package with nothing in it and version 3: only an older app does anything with it (says "update"). */
    internal fun updateNotice(): ByteArray = pack(PAIR_MARK, json.encodeToString(Pack("", emptyList(), BandProtocol.BREAKS_VERSION)))

    /** What arrived in my inbox from a pair, since [since] (null = the last 12 h); null with no internet. */
    suspend fun fetchPair(pairKey: ByteArray, since: String?, known: (packageId: String) -> Boolean = { false }): PairFetch? = withContext(Dispatchers.IO) {
        val lines = get("$SERVER/${pairTopic(pairKey, identity.id)}/json?poll=1&since=${since ?: "12h"}") ?: return@withContext null
        val msgs = messagesWithIds(lines)
        val all = msgs.mapNotNull { (_, m) -> ShareCode.decrypt(pairKey, m)?.let { runCatching { json.decodeFromString<Chunk>(it.decodeToString()) }.getOrNull() } }
        val chunks = all.filter { it.k == "pkg" }
        val breaks = HashMap<String, List<Int>>()
        val packages = chunks.groupBy { it.p }.entries.mapNotNull { (pid, parts) ->
            val n = parts.first().n
            val ordered = parts.distinctBy { it.i }.sortedBy { it.i }
            if (ordered.size != n) return@mapNotNull null
            // a malformed chunk is an unreadable package, not an error that stops this member's sync and the next ones
            val bytes = runCatching { ordered.fold(ByteArray(0)) { acc, c -> acc + java.util.Base64.getDecoder().decode(c.d) } }.getOrNull() ?: return@mapNotNull null
            // a package already taken in (a resend, 12 h later) isn't opened again: its PDFs were downloaded and written
            // to the disk just to be thrown away (audit 22/11). It still comes back by its id, so the receipt goes again
            if (known(pid)) return@mapNotNull Triple(pid, "", emptyList<SharedSong>())
            val pkg = openPairPackage(bytes) { url -> fetchAttachment(url, pairKey) } ?: return@mapNotNull null
            pkg.breaks?.let { breaks[pid] = it }
            Triple(pid, pkg.text, pkg.songs)
        }
        // requests for missing songs (T12)
        val requests = all.filter { it.k == "req" }.mapNotNull { c ->
            runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(BandLink.Wanted.serializer()), String(java.util.Base64.getDecoder().decode(c.d))) }.getOrNull()
        }
        PairFetch(packages, all.filter { it.k == "rcpt" }.map { it.p }, all.any { it.k == "welcome" }, msgs.lastOrNull()?.first ?: since, requests, breaks)
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

    // ---- PDFs as attachments of the server (user, 22/11) ----

    /** The tests' own server for attachments: what is put up (topic, body) gives its address; an address gives the body. */
    internal var attachHook: ((topic: String, body: ByteArray) -> String?)? = null
    internal var fetchHook: ((url: String) -> ByteArray?)? = null

    /**
     * Puts the PDF [file] up as an attachment of the server, next to [topic]: compressed when it gets much smaller, and
     * encrypted with [key] (the server and whoever finds the address see nothing). Its address, or null when it didn't go.
     */
    private fun attach(topic: String, key: ByteArray, file: java.io.File): String? {
        // what goes is the stored file: it was made smaller when it came in. One from before that is made smaller now, in
        // its place, this once (user, 22/11)
        runCatching { pdfs?.compactOnce(file, inspector) }
        val bytes = try { file.readBytes() } catch (e: java.io.IOException) { return null }
        val body = ShareCode.encrypt(key, bytes).toByteArray()
        attachHook?.let { hook -> return hook(attachTopic(topic), body)?.takeIf(::isAttachment) }
        val at = if (!internetOn) null else runCatching {
            val c = URL("$SERVER/${attachTopic(topic)}").openConnection() as HttpURLConnection
            c.requestMethod = "PUT"
            c.doOutput = true
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = ATTACH_TIMEOUT_MS
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Filename", "stagebook.bin")
            c.setFixedLengthStreamingMode(body.size)
            try {
                c.outputStream.use { it.write(body) }
                if (c.responseCode != 200) null
                else json.parseToJsonElement(c.inputStream.use { it.readTextCapped(MAX_SERVICE_ANSWER) }).jsonObject["attachment"]?.jsonObject?.get("url")?.jsonPrimitive?.content
            } finally { c.disconnect() }
        }.getOrNull()
        return at?.takeIf(::isAttachment)
    }

    /** The PDF at [url], decrypted with [key]; null when it is gone (past the server's hours), too big or not ours. */
    private fun fetchAttachment(url: String, key: ByteArray): ByteArray? {
        if (!isAttachment(url)) return null   // anyone can post on the channel: only the server's own files are fetched
        val hook = fetchHook
        val body = if (hook != null) hook(url) else if (!internetOn) null else runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = ATTACH_TIMEOUT_MS
            c.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (c.responseCode != 200 || c.contentLengthLong > MAX_ATTACHMENT) null
                else c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_ATTACHMENT) return@use null
                    }
                    out.toByteArray()
                }
            } finally { c.disconnect() }
        }.getOrNull()
        if (body == null || body.size > MAX_ATTACHMENT) return null
        return ShareCode.decrypt(key, body.decodeToString())?.takeIf { it.size <= br.app.stagebook.domain.BandContent.MAX_PDF_BYTES }
    }

    private fun isAttachment(url: String) = url.startsWith("$SERVER/file/") && url.length <= MAX_ATTACH_URL && url.none { it.isWhitespace() }

    // the attachments go next to the channel, not on it: its messages stay only the send's own
    private fun attachTopic(topic: String) = (topic + "-f").take(64)

    private fun get(url: String): List<String>? = getHook?.let { return it(url) } ?: if (!internetOn) null else runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode != 200) null else c.inputStream.use { it.readTextCapped(MAX_POLL_ANSWER) }.lines().filter { it.isNotEmpty() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    companion object {
        // the PDF goes as its file's name: the bytes are read only when a package leaves for the band (BandLink)
        private fun br.app.stagebook.data.model.Song.shared() = SharedSong(title, author, year, text, pdfName = if (pdf.isEmpty()) "" else pdfName, pdfFile = pdf)

        /** [songs] as loose songs: a list with no name (how the receiver knows they aren't a setlist) and their texts. */
        fun looseSongs(songs: List<br.app.stagebook.data.model.Song>): Pair<ByteArray, List<SharedSong>>? {
            if (songs.isEmpty()) return null
            return QrPayload.pack(looseList(songs.map { it.title to it.author })) to songs.map { it.shared() }
        }

        /** The list of loose songs (title to artist), in the QR's format with no setlist name. */
        fun looseList(songs: List<Pair<String, String>>): String =
            (listOf(QrPayload.HEADER, songs.size.toString(), "") + songs.map { (title, artist) -> listOf(title, artist).joinToString("\t") { s -> s.replace('\n', ' ').replace('\t', ' ').trim() }.trimEnd('\t') })
                .joinToString("\n")

        private const val SERVER = "https://ntfy.sh"

        /** Which song a PDF chunk belongs to: a short hash of the title and artist, as the sender dedupes its songs (see sendMany). */
        internal fun songKey(title: String, author: String): String =
            "%08x".format((br.app.stagebook.domain.TextKey.of(title) + "\t" + br.app.stagebook.domain.TextKey.of(author)).hashCode())

        /** The invite's format: 1 = the 3 words open it (still read), 2 = it also needs the secret from its link. */
        const val INVITE_VERSION = 2
        private const val TIMEOUT_MS = 10_000
        private const val ATTACH_TIMEOUT_MS = 60_000   // a PDF of some MB on a slow phone line
        // an attachment is the PDF encrypted (base64): 4/3 of the largest PDF and some framing
        // a topic's messages at once: the 60 of a send, or a member's package in chunks; well under this
        private const val MAX_POLL_ANSWER = 32 * 1024 * 1024
        private const val MAX_ATTACHMENT = br.app.stagebook.domain.BandContent.MAX_PDF_BYTES * 4 / 3 + 64 * 1024
        private const val MAX_ATTACH_URL = 200
        private const val OLD_PAIR_PDF_BYTES = 60_000L
        private const val MAX_MESSAGE = 4_000          // ntfy.sh takes up to 4,096 bytes per message
        private const val USER_AGENT = "StagebookApp/0.2 (setlist da banda)"
        private val json = Json { ignoreUnknownKeys = true }
        private const val MAX_REQUEST = 200   // a setlist never goes over this
        private val SONG_MARK = byteArrayOf('S'.code.toByte(), '1'.code.toByte())
        private val PDF_MARK = byteArrayOf('F'.code.toByte(), '1'.code.toByte())    // a chunk of a song's PDF
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
