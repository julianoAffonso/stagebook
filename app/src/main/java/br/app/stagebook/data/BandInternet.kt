package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import br.app.stagebook.domain.BandCrypto
import br.app.stagebook.domain.BandProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The band over the internet (no server of ours: the same ntfy.sh as the 3 words, all encrypted), with **resend until
 * it arrives**:
 * - whoever accepts a send through the 3 words joins the band and sends back an "ok"; the sender keeps the "ok" and
 *   starts trusting them too (the same trust as the local network) and answers "welcome", so the other stops repeating;
 * - between band devices, each pair has its own inbox; the receiver sends back a receipt; with no receipt, the sender
 *   resends once the service has deleted it (12 h);
 * - when they meet on the same Wi-Fi (rehearsal, show), whatever is pending goes through right away.
 *
 * **Battery** (user's request): nothing runs with the app closed; on open, it tries once; the Wi-Fi search to catch up
 * lasts 30 s and only happens with something pending (never on stage); resending only after 12 h; each pending item
 * gives up after 30 days or 5 resends.
 */
@Singleton
class BandInternet @Inject constructor(
    @ApplicationContext context: Context,
    private val cloud: SetlistCloud,
    private val trust: BandTrust,
    private val identity: DeviceIdentity,
    private val inbox: BandInbox,
    private val connectivity: Connectivity,
    private val link: BandLink,
) {
    /** One of my sends to a member's inbox, until the receipt arrives. */
    @Serializable
    private data class Out(val id: String, val to: String, val list: String, val songs: List<SetlistCloud.SharedSong>, val at: Long, val sentAt: Long, val tries: Int = 1)

    /** One of my sends through the 3 words (to collect the "ok" of whoever accepts). */
    @Serializable
    private data class MyCode(val code: String, val until: Long)

    /** A send through the words that I accepted: my "ok" keeps repeating until the sender says welcome. */
    @Serializable
    private data class Accepted(val code: String, val from: String, val until: Long)

    private val prefs = context.getSharedPreferences("band_net", Context.MODE_PRIVATE)
    // the queue carries every song sent, once per member: a file of its own instead of the prefs (audit 01/11)
    private val outFile = java.io.File(context.filesDir, OUT_FILE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = backgroundScope(Dispatchers.IO)   // errors logged, never a crash on stage
    private val lock = Mutex()

    init {
        // the words' "ok" arriving over the local network: checked against my saved sends
        link.codeAckHandler = { hello, body ->
            val h = cloud.checkAckBody(body, myCodes().map { it.code })
            if (h != null && h.id == hello.id) { trust.add(h.id, h.name, h.key); true } else false
        }
        // a band member showed up on the Wi-Fi: deliver whatever is pending for them (without waiting for the service)
        scope.launch { link.onlineMembers.collect { peers -> if (peers.isNotEmpty()) settleOnLan(peers) } }
    }

    private fun <T> load(key: String, ser: kotlinx.serialization.KSerializer<T>): List<T> =
        runCatching { json.decodeFromString(ListSerializer(ser), prefs.getString(key, null) ?: return emptyList()) }.getOrDefault(emptyList())

    private fun <T> save(key: String, ser: kotlinx.serialization.KSerializer<T>, list: List<T>) =
        prefs.edit { putString(key, json.encodeToString(ListSerializer(ser), list)) }

    private fun loadOut(): List<Out> {
        // a queue saved by an older version, in the prefs: moves to the file once
        if (!outFile.exists()) prefs.getString(KEY_OUT, null)?.let { old ->
            if (runCatching { outFile.replaceAtomically(old.toByteArray()) }.getOrDefault(false)) prefs.edit { remove(KEY_OUT) }
        }
        return runCatching { json.decodeFromString(ListSerializer(Out.serializer()), outFile.readText()) }.getOrDefault(emptyList())
    }

    private fun saveOut(list: List<Out>) {
        runCatching { outFile.replaceAtomically(json.encodeToString(ListSerializer(Out.serializer()), list).toByteArray()) }
            .onFailure { android.util.Log.w("BandInternet", "couldn't save the send queue", it) }
    }

    private fun now() = System.currentTimeMillis()
    private fun outbox() = loadOut().filter { now() - it.at < KEEP_MS && it.tries <= MAX_TRIES }
    private fun myCodes() = load(KEY_CODES, MyCode.serializer()).filter { it.until > now() }
    private fun accepted() = load(KEY_ACCEPTED, Accepted.serializer()).filter { it.until > now() }

    private fun pairKey(member: BandTrust.Member): ByteArray? =
        BandCrypto.publicKey(member.key)?.let { BandCrypto.pairKey(identity.privateKey, identity.id, it, member.id) }

    /**
     * One of my sends through the 3 words: kept for 30 days to collect the "ok"s of whoever accepts (also on the local
     * network).
     */
    fun remember(code: String) {
        // under the same lock as the sync: the sync writes its lists back at the end and would drop this (audit 30/10)
        scope.launch { lock.withLock { save(KEY_CODES, MyCode.serializer(), myCodes() + MyCode(code, now() + KEEP_MS)) } }
    }

    /**
     * I accepted a send through the words: the sender joins the band; my "ok" goes now and repeats until they confirm.
     */
    fun accepted(code: String, from: BandProtocol.Hello?) {
        from ?: return
        trust.add(from.id, from.name, from.key)
        // the "ok" waits for a running sync instead of being overwritten by its end (lost when the first ack failed)
        scope.launch {
            lock.withLock { save(KEY_ACCEPTED, Accepted.serializer(), accepted().filterNot { it.code == code } + Accepted(code, from.id, now() + KEEP_MS)) }
            cloud.ack(code)
        }
    }

    /**
     * The codes worth asking the internet about: the send stays on the server 12 h (nobody accepts after that) and an
     * "ok" another 12 h, so older codes only cost one request each, every sync, holding the lock (audit 01/11). The
     * "ok" on the local network still counts for the whole 30 days.
     */
    internal fun recentCodes(codes: List<Pair<String, Long>>, now: Long): List<String> =
        codes.filter { (_, until) -> now - (until - KEEP_MS) < ACK_WINDOW_MS }.map { it.first }

    /** The "ok"s for one of my sends: whoever accepted joins the band (and gets a welcome). Returns the new names. */
    suspend fun checkAcks(code: String): List<String> = cloud.acks(code).filterNot { trust.isMember(it.id) }.map { h ->
        trust.add(h.id, h.name, h.key)
        trust.member(h.id)?.let(::pairKey)?.let { cloud.welcome(it, h.id) }
        h.name
    }

    /**
     * On app open: a single pass (internet, if available) and, with something pending, 30 s of Wi-Fi search to catch
     * up.
     */
    fun onAppOpen() = sync(force = false)

    /**
     * "Sync with the band" (Import menu): the same pass, right away without waiting to reopen the app; the Wi-Fi search
     * runs even with nothing pending (there may be news from someone nearby).
     */
    fun syncNow() = sync(force = true)

    private fun sync(force: Boolean) {
        scope.launch {
            lock.withLock {
                val pending = outbox().isNotEmpty() || accepted().isNotEmpty()
                if (trust.members.value.isNotEmpty() && connectivity.isOnline()) runCatching { syncInternet() }
                if ((pending || force) && trust.members.value.isNotEmpty()) link.burst()
            }
        }
    }

    private suspend fun syncInternet() {
        recentCodes(myCodes().map { it.code to it.until }, now()).forEach { runCatching { checkAcks(it) } }
        accepted().forEach { runCatching { cloud.ack(it.code) } }   // repeats the "ok" until the welcome arrives
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        var out = outbox()
        var acc = accepted()
        val since = mutableMapOf<String, String>()   // each member's cursor, written once at the end (audit 01/11)
        try {
            trust.members.value.forEach { m ->
                val key = pairKey(m) ?: return@forEach
                val got = cloud.fetchPair(key, prefs.getString("$KEY_SINCE${m.id}", null)) ?: return@forEach
                // they asked for songs missing from the setlist I sent (T12): answers with the ones I have, in a songs
                // package
                got.requests.forEach { wanted ->
                    val ids = link.songsFor(wanted)
                    if (ids.isNotEmpty()) cloud.songsPackage(ids)?.let { (list, songs) -> cloud.sendToPair(key, m.id, list, songs, java.util.UUID.randomUUID().toString()) }
                }
                got.packages.forEach { (pid, text, songs) ->
                    // a repeat (a resend that arrived twice) doesn't come in again; the receipt goes anyway
                    if (seen.add("${m.id}|$pid")) inbox.add(m.id, m.name, text, songs)
                    cloud.receipt(key, m.id, pid)
                }
                out = out.filterNot { it.to == m.id && it.id in got.receipts }
                if (got.welcome) acc = acc.filterNot { it.from == m.id }
                got.last?.let { since[m.id] = it }
            }
            // no receipt and the service already deleted it (12 h): send again, with the same id
            out = out.map { o ->
                if (now() - o.sentAt < RESEND_AFTER_MS) return@map o
                val m = trust.member(o.to) ?: return@map o
                val key = pairKey(m) ?: return@map o
                val ok = cloud.sendToPair(key, o.to, java.util.Base64.getDecoder().decode(o.list), o.songs, o.id)
                if (ok) o.copy(sentAt = now(), tries = o.tries + 1) else o
            }
        } finally {
            // also when a member's fetch fails halfway: what already arrived keeps its cursor and its "seen"
            saveOut(out)
            prefs.edit {
                putString(KEY_ACCEPTED, json.encodeToString(ListSerializer(Accepted.serializer()), acc))
                putStringSet(KEY_SEEN, seen.toList().takeLast(MAX_SEEN).toSet())
                since.forEach { (id, last) -> putString("$KEY_SINCE$id", last) }
            }
        }
    }

    // on the same Wi-Fi: sends with no receipt go directly (and leave the queue); my pending "ok" too
    private suspend fun settleOnLan(peers: List<BandLink.Peer>) = lock.withLock {
        val ids = peers.associateBy { it.id }
        var out = outbox()
        out = out.filterNot { o ->
            val peer = ids[o.to] ?: return@filterNot false
            val body = link.packageFor(o.to, java.util.Base64.getDecoder().decode(o.list), o.songs) ?: return@filterNot false
            link.sendTo(peer, body)
        }
        val acc = accepted().filterNot { a ->
            val peer = ids[a.from] ?: return@filterNot false
            val body = cloud.ackBody(a.code) ?: return@filterNot false
            link.sendTo(peer, body, BandProtocol.Kind.CODE_ACK)
        }
        saveOut(out)
        save(KEY_ACCEPTED, Accepted.serializer(), acc)
    }

    /**
     * Sends to band members not in [skip] (already got it over Wi-Fi), keeping it until the receipt; returns how many
     * it went to.
     */
    suspend fun sendToBand(list: ByteArray, songs: List<SetlistCloud.SharedSong>, skip: Set<String>): Int = lock.withLock {
        val targets = trust.members.value.filterNot { it.id in skip }
        val listB64 = java.util.Base64.getEncoder().encodeToString(list)
        val sent = targets.mapNotNull { m ->
            val key = pairKey(m) ?: return@mapNotNull null
            val id = UUID.randomUUID().toString().take(8)
            val ok = cloud.sendToPair(key, m.id, list, songs, id)
            // even if it didn't go now (no internet), it stays in the queue: goes on app open or when they meet on the
            // Wi-Fi
            Out(id, m.id, listB64, songs, now(), if (ok) now() else 0L) to ok
        }
        saveOut(outbox() + sent.map { it.first })
        sent.count { it.second }
    }

    /** How many sends are waiting for a receipt (for the screen, if needed). */
    fun pendingCount() = outbox().size

    /**
     * Asks the setlist's sender (a band member) online for the missing songs (T12). They answer when their app syncs,
     * and the songs arrive in "Received from the band". false = not in the band or no internet.
     */
    suspend fun requestSongs(memberId: String, wanted: List<BandLink.Wanted>): Boolean {
        val m = trust.member(memberId) ?: return false
        val key = pairKey(m) ?: return false
        return runCatching { cloud.requestFromPair(key, m.id, wanted) }.getOrDefault(false)
    }

    private companion object {
        const val KEY_OUT = "outbox"   // older versions: the queue in the prefs (moved to [OUT_FILE])
        const val OUT_FILE = "band_outbox.json"
        const val KEY_CODES = "codes"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_SINCE = "since_"
        const val KEY_SEEN = "seen"
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000      // gives up after 30 days
        const val ACK_WINDOW_MS = 26L * 60 * 60 * 1000      // 12 h of the send + 12 h of the "ok" + a margin
        const val RESEND_AFTER_MS = 12L * 60 * 60 * 1000   // the service keeps it 12 h: no resending before that
        const val MAX_TRIES = 5
        const val MAX_SEEN = 300
    }
}
