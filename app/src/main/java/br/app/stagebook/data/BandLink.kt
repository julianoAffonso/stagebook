package br.app.stagebook.data

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.stateIn
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.BandCrypto
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.Immutable
import br.app.stagebook.domain.BandProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The band on the local network (venue Wi-Fi or a phone's hotspot; no internet): announces this device, finds the
 * others and handles join requests. Uses as little of the tablet as possible (user's request, 25/10):
 * - **presence beacon + server** (almost nothing: a ~100-byte broadcast every 3 s, a thread waiting for the others'
 *   and the server waiting): app in front, local network and (a saved band member, or a searching screen open);
 * - **search** (the multicast lock, the Wi-Fi waking for each packet): only while a screen needs it
 *   ([setScreenOpen]) and **never on stage**, except the short "Look for the band" burst.
 */
@Singleton
class BandLink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: DeviceIdentity,
    private val trust: BandTrust,
    private val stage: StageGate,
    private val inbox: BandInbox,
    private val repository: LibraryRepository,
) {
    /** What goes in a band package: the list (in QR format, in Base64) and the songs. */
    @kotlinx.serialization.Serializable
    private data class Package(val list: String, val songs: List<SetlistCloud.SharedSong> = emptyList(), val v: Int = BandProtocol.VERSION)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    /** A device with Stagebook on the same network. */
    @Immutable
    data class Peer(val id: String, val name: String, val host: InetAddress, val port: Int, val v: Int = BandProtocol.VERSION, val app: String = "")

    /** Someone asking to join the band through this device (the user answers Allow / Don't allow). */
    @Immutable
    class Request(val id: String, val name: String, internal val answer: CompletableDeferred<Boolean>)

    enum class Join { WAITING, ACCEPTED, DENIED, BUSY, VERSION, FAILED }

    private val scope = backgroundScope(Dispatchers.IO)   // errors logged, never a crash on stage
    private val sessions = kotlinx.coroutines.sync.Semaphore(SESSIONS)   // connections served at once

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests: StateFlow<List<Request>> = _requests

    /** Requests this device made, by the other one's id. */
    private val _joins = MutableStateFlow<Map<String, Join>>(emptyMap())
    val joins: StateFlow<Map<String, Join>> = _joins

    private val _running = MutableStateFlow(false)
    /** Announced on the network (the server waiting). Searching for the others is separate and shorter. */
    val running: StateFlow<Boolean> = _running

    private var foreground = false
    private var searchUsers = 0   // open screens that need to find the others (Settings → Band, Share sheet)
    private val screenOpen get() = searchUsers > 0
    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var multicast: WifiManager.MulticastLock? = null

    /** The app came to the front / left it (MainActivity). In front, it follows Wi-Fi turning on and off. */
    fun setForeground(on: Boolean) {
        foreground = on
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (on && netCallback == null && cm != null) {
            netCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) { _wifi.value = onWifi(); update() }
                override fun onLost(network: android.net.Network) { _wifi.value = onWifi(); update() }
            }.also { runCatching { cm.registerDefaultNetworkCallback(it) } }
        } else if (!on) {
            netCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
            netCallback = null
        }
        _wifi.value = onWifi()
        update()
    }

    private var netCallback: ConnectivityManager.NetworkCallback? = null

    private val _wifi = MutableStateFlow(false)
    /** On Wi-Fi now (the screen shows "connect to Wi-Fi" without it). */
    val wifi: StateFlow<Boolean> = _wifi

    /** A screen that needs to find the others opened (true) or closed (false): Settings → Band, the Share sheet. */
    @Synchronized
    fun setScreenOpen(on: Boolean) { searchUsers = (searchUsers + if (on) 1 else -1).coerceAtLeast(0); update() }

    /** Band members on the network now (for "Band on the network (N)"). */
    val onlineMembers: StateFlow<List<Peer>> = kotlinx.coroutines.flow.combine(_peers, trust.members) { peers, members ->
        val ids = members.map { it.id }.toSet()
        peers.filter { it.id in ids && it.v == BandProtocol.VERSION }   // another version doesn't receive: it stays in the queue
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Who checks a 3 words "ok" that arrived over the network ([BandInternet], which keeps my sends). */
    @Volatile var codeAckHandler: ((BandProtocol.Hello, String) -> Boolean)? = null

    /** Sends to a device on the network (package encrypted with the pair key, or a CODE_ACK); true if it stored it. */
    suspend fun sendTo(peer: Peer, body: String, kind: String = BandProtocol.Kind.PACKAGE): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val s = dial(peer)
            BandSession(identity.hello()).send(s, peer.id, body, kind)
        }.getOrDefault(false)
    }

    /** The package (list + songs) encrypted for a member, ready for [sendTo]. */
    fun packageFor(memberId: String, list: ByteArray, songs: List<SetlistCloud.SharedSong>): String? {
        val key = pairKeyWith(memberId) ?: return null
        return ShareCode.encrypt(key, packageBytes(list, songs))
    }

    private fun packageBytes(list: ByteArray, songs: List<SetlistCloud.SharedSong>): ByteArray =
        json.encodeToString(Package.serializer(), Package(java.util.Base64.getEncoder().encodeToString(list), songs)).toByteArray()

    /**
     * Searches for the band briefly (on app open with something pending: sends with no receipt, an unconfirmed "ok"),
     * to catch up with whoever is on the same Wi-Fi. Never on stage (the search already respects that).
     */
    fun burst(ms: Long = BURST_MS, evenOnStage: Boolean = false) {
        if (evenOnStage) { onStageSearch.incrementAndGet(); update() } else setScreenOpen(true)
        scope.launch {
            kotlinx.coroutines.delay(ms)
            if (evenOnStage) { onStageSearch.updateAndGet { (it - 1).coerceAtLeast(0) }; update() } else setScreenOpen(false)
        }
    }

    // only "Lead the band" searches on stage, and always briefly (burst): find whoever came back to the Wi-Fi
    // changed from the main thread and from IO: atomic, or a lost update kept the search (and the multicast lock) on
    // during the show (audit 30/10)
    private val onStageSearch = java.util.concurrent.atomic.AtomicInteger()

    /** Sends a setlist or songs to everyone in the band on the network now; returns the ids of who got it. */
    suspend fun send(list: ByteArray, songs: List<SetlistCloud.SharedSong>): Set<String> = kotlinx.coroutines.withContext(Dispatchers.IO) io@{
        val plain = packageBytes(list, songs)
        onlineMembers.value.map { peer ->
            this@io.async {
                val key = pairKeyWith(peer.id) ?: return@async null
                val ok = runCatching {
                    val s = dial(peer)
                    BandSession(identity.hello()).send(s, peer.id, ShareCode.encrypt(key, plain))
                }.getOrDefault(false)
                if (ok) peer.id else null
            }
        }.mapNotNull { it.await() }.toSet()
    }

    /** A song asked of the band (title and artist, as in the QR). */
    @kotlinx.serialization.Serializable
    data class Wanted(val title: String, val artist: String = "")

    // a connection to a device on the Wi-Fi, with the usual timeout (it was written out in five places)
    private fun dial(peer: Peer): Socket = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }

    // pair key with a member (null if they're no longer in the band or the saved key doesn't open)
    private fun pairKeyWith(id: String): ByteArray? {
        val member = trust.member(id) ?: return null
        val theirs = BandCrypto.publicKey(member.key) ?: return null
        return BandCrypto.pairKey(identity.privateKey, identity.id, theirs, member.id)
    }

    /**
     * Asks everyone in the band on the network now who has the [wanted] songs; collects the answers (the first to
     * arrive for each title). Nobody on the other side has to tap anything.
     */
    suspend fun askMissing(wanted: List<Wanted>): List<SetlistCloud.SharedSong> = kotlinx.coroutines.withContext(Dispatchers.IO) io@{
        if (wanted.isEmpty()) return@io emptyList()
        val plain = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Wanted.serializer()), wanted).toByteArray()
        val answers = onlineMembers.value.map { peer ->
            this@io.async {
                val key = pairKeyWith(peer.id) ?: return@async emptyList()
                runCatching {
                    val s = dial(peer)
                    val body = BandSession(identity.hello()).ask(s, peer.id, ShareCode.encrypt(key, plain)) ?: return@async emptyList()
                    val bytes = ShareCode.decrypt(key, body) ?: return@async emptyList()
                    json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SetlistCloud.SharedSong.serializer()), bytes.decodeToString())
                        .filter { it.allowed() }.take(br.app.stagebook.domain.BandContent.MAX_SONGS)
                }.getOrDefault(emptyList())
            }
        }.flatMap { it.await() }
        // more than one device has the same song: the version with chords wins (in the lyrics only profile it's kept,
        // hidden)
        answers.groupBy { br.app.stagebook.domain.TextKey.of(it.title) + "|" + br.app.stagebook.domain.TextKey.of(it.author) }
            .values.map { versions -> versions.maxBy { if (br.app.stagebook.domain.Chords.hasChords(it.text)) 1 else 0 } }
    }

    /** A follower gets the leader's message and answers what's missing here (null = not following). */
    @Volatile var conductHandler: ((BandProtocol.Hello, br.app.stagebook.domain.Conduct.Msg) -> br.app.stagebook.domain.Conduct.Lack?)? = null

    /**
     * "Lead the band": sends [msg] to the band members online, all at once, and collects what each one is missing (id →
     * missing; whoever didn't answer is left out of the map and the screen treats them as "didn't answer").
     */
    suspend fun conduct(msg: br.app.stagebook.domain.Conduct.Msg): Map<String, br.app.stagebook.domain.Conduct.Lack> =
        kotlinx.coroutines.withContext(Dispatchers.IO) io@{
            val plain = br.app.stagebook.domain.Conduct.encode(msg).toByteArray()
            onlineMembers.value.map { peer ->
                this@io.async {
                    val key = pairKeyWith(peer.id) ?: return@async null.also { Log.w(TAG, "conduct: no pair key for ${peer.name}") }
                    // a send that fails is logged (it used to vanish: the leader only saw "didn't answer", 31/10)
                    runCatching {
                        val s = dial(peer)
                        val body = BandSession(identity.hello()).ask(s, peer.id, ShareCode.encrypt(key, plain), BandProtocol.Kind.CONDUCT)
                            ?: return@async null.also { Log.w(TAG, "conduct: no answer from ${peer.name} (${peer.host}:${peer.port})") }
                        val bytes = ShareCode.decrypt(key, body) ?: return@async null.also { Log.w(TAG, "conduct: answer from ${peer.name} not readable") }
                        peer.id to json.decodeFromString(br.app.stagebook.domain.Conduct.Lack.serializer(), bytes.decodeToString())
                    }.onFailure { Log.w(TAG, "conduct to ${peer.name} (${peer.host}:${peer.port}) failed: $it") }.getOrNull()
                }
            }.mapNotNull { it.await() }.toMap()
        }

    private fun onConduct(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null.also { Log.w(TAG, "conduct from ${from.name}: not in the band here") }
        val msg = ShareCode.decrypt(key, body)?.let { br.app.stagebook.domain.Conduct.decode(it.decodeToString()) }
            ?: return null.also { Log.w(TAG, "conduct from ${from.name}: message not readable") }
        val lack = conductHandler?.invoke(from, msg) ?: return null.also { Log.w(TAG, "conduct from ${from.name}: not handled") }
        return ShareCode.encrypt(key, json.encodeToString(br.app.stagebook.domain.Conduct.Lack.serializer(), lack).toByteArray())
    }

    /** The songs here that answer a request (same rule as the QR: title, with the artist as tiebreaker). */
    fun songsFor(wanted: List<Wanted>): List<String> = found(wanted).map { it.id }.distinct()

    private fun found(wanted: List<Wanted>): List<br.app.stagebook.data.model.Song> {
        val library = repository.library.value.songs
        return wanted.take(MAX_ASK).mapNotNull { w ->
            (br.app.stagebook.domain.SetlistMatcher.match(QrPayload.Entry(w.title, w.artist), library) as? br.app.stagebook.domain.QrMatch.Found)?.song
        }
    }

    // a band member asked: answer with the ones here (same rule as the QR: title, with the artist as tiebreaker)
    private fun answer(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null
        val plain = ShareCode.decrypt(key, body) ?: return null
        val wanted = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Wanted.serializer()), plain.decodeToString()) }.getOrNull() ?: return null
        val found = found(wanted).map { SetlistCloud.SharedSong(it.title, it.author, it.year, it.text) }
        return ShareCode.encrypt(key, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SetlistCloud.SharedSong.serializer()), found).toByteArray())
    }

    // package from a band member: decrypt with the pair key and keep it in the inbox
    private fun receive(from: BandProtocol.Hello, body: String): Boolean {
        val member = trust.member(from.id) ?: return false
        val key = pairKeyWith(member.id) ?: return false
        val plain = ShareCode.decrypt(key, body) ?: return false
        val pkg = runCatching { json.decodeFromString(Package.serializer(), plain.decodeToString()) }.getOrNull() ?: return false
        val text = QrPayload.unpack(java.util.Base64.getDecoder().decode(pkg.list)) ?: return false
        if (QrPayload.parse(text) == null) return false
        inbox.add(member.id, from.name.ifBlank { member.name }, text, pkg.songs)
        return true
    }

    @Synchronized
    fun update() {
        // essential mode (T19): "Band on the network" off, nothing announces, searches or answers
        val want = repository.library.value.look.bandOn && foreground && onWifi() && (screenOpen || trust.members.value.isNotEmpty())
        if (want && !_running.value) start() else if (!want && _running.value) stop()
        updateSearch()
    }

    /** Is there a local network (Wi-Fi, cable or a hotspot)? The same rule as the screens ([Connectivity.onLocalNetwork]). */
    fun onWifi(): Boolean = Connectivity.onLocalNetwork(context)

    private var searching = false

    // the search turns on and off by itself, without touching the announcement: a screen that needs it open and off
    // stage
    private fun updateSearch() {
        val want = _running.value && ((screenOpen && !stage.onStage.value) || onStageSearch.get() > 0)
        if (want && !searching) startSearch() else if (!want && searching) stopSearch()
    }

    private fun startSearch() {
        searching = true
        multicast = context.applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock("stagebook-band")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun stopSearch() {
        searching = false
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
    }

    private fun start() {
        _running.value = true
        val socket = runCatching { ServerSocket(0) }.getOrElse { Log.w(TAG, "porta: $it"); _running.value = false; return }
        server = socket
        serverJob = scope.launch {
            val session = BandSession(identity.hello())
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                // at most SESSIONS at once: a flood of connections from the Wi-Fi doesn't take every IO thread
                if (!sessions.tryAcquire()) { runCatching { client.close() }; continue }
                launch {
                    try {
                        session.serve(
                            client, trust::isMember, ::ask, { h -> trust.add(h.id, h.name, h.key); update() },
                            busy = { stage.onStage.value }, onPackage = ::receive, onAsk = ::answer,
                            onCodeAck = { h, body -> codeAckHandler?.invoke(h, body) ?: false }, onConduct = ::onConduct,
                        )
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "band session failed: $e")
                    } finally {
                        sessions.release()
                    }
                }
            }
        }
        beaconJob = scope.launch { beacon(socket.localPort) }
    }

    private fun stop() {
        stopSearch()
        _running.value = false
        runCatching { server?.close() }
        server = null
        serverJob?.cancel()
        beaconJob?.cancel()
        runCatching { beaconSocket?.close() }
        beaconSocket = null
        beaconSeen.clear()
        _peers.value = emptyList()
    }


    // ---- presence beacon (02/11, device test N-B-08): how band devices find each other ----
    // Android's discovery (NSD) found nobody over a Samsung hotspot (the bar case: the phone sharing its network), so it
    // was replaced, one way only (user: no two behaviours): each device says "here I am" every few seconds as a UDP
    // broadcast on every local network it's on, hotspot included, and listens to the others' while the band runs (on
    // stage too: the conductor needs the list). Lighter than NSD. Only who and where: joining and sending still need
    // the band's keys. Whoever stops sending it leaves the list after [BEACON_GONE_MS].
    private var beaconJob: kotlinx.coroutines.Job? = null
    @Volatile private var beaconSocket: java.net.DatagramSocket? = null
    private val beaconSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()   // id → last beacon

    private suspend fun beacon(port: Int) = kotlinx.coroutines.coroutineScope {
        val socket = runCatching {
            java.net.DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(BEACON_PORT)) }
        }.getOrElse { Log.w(TAG, "beacon: $it"); return@coroutineScope }
        // the socket is always closed here when the beacon ends: stop() can run before this line, and the socket opened
        // after it stayed open (audit 02/11 #7)
        try {
            beaconSocket = socket
            if (!_running.value) return@coroutineScope
            launch {
                val buf = ByteArray(512)
                while (isActive) {
                    val packet = java.net.DatagramPacket(buf, buf.size)
                    runCatching { socket.receive(packet) }.getOrElse { break }
                    val parts = String(packet.data, 0, packet.length, Charsets.UTF_8).split('\n')
                    if (parts.size < 6 || parts[0] != BEACON_TAG) continue
                    val id = parts[1]
                    val peerPort = parts[2].toIntOrNull() ?: continue
                    // a packet read after the band stopped doesn't bring a device back to the list
                    if (id == identity.id || id.length > 64 || !_running.value) continue
                    val v = parts[3].toIntOrNull() ?: BandProtocol.VERSION
                    val peer = Peer(id, parts[5].take(BandProtocol.MAX_NAME), packet.address, peerPort, v, parts[4].take(20))
                    beaconSeen[id] = System.currentTimeMillis()
                    _peers.update { list -> if (list.any { it == peer }) list else (list.filterNot { it.id == id } + peer).sortedBy { it.name.lowercase() } }
                }
            }
            while (isActive) {
                val msg = listOf(BEACON_TAG, identity.id, port, BandProtocol.VERSION, identity.appVersion, identity.name.value.replace('\n', ' '))
                    .joinToString("\n").toByteArray(Charsets.UTF_8)
                for (target in broadcastTargets()) runCatching { socket.send(java.net.DatagramPacket(msg, msg.size, target, BEACON_PORT)) }
                // whoever stopped sending it leaves the list
                val now = System.currentTimeMillis()
                val gone = beaconSeen.filterValues { now - it > BEACON_GONE_MS }.keys
                if (gone.isNotEmpty()) { gone.forEach(beaconSeen::remove); _peers.update { list -> list.filterNot { it.id in gone } } }
                kotlinx.coroutines.delay(BEACON_EVERY_MS)
            }
        } finally {
            runCatching { socket.close() }
            if (beaconSocket === socket) beaconSocket = null
        }
    }

    /**
     * The broadcast address of every local IPv4 network this device is on (Wi-Fi, hotspot, cable): the band beacon and
     * Now Playing go to each, since 255.255.255.255 leaves only by the main network (4G on a phone sharing its hotspot).
     */
    fun broadcastTargets(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
            .mapNotNull { it.broadcast }
            .distinct()
    }.getOrDefault(emptyList())

    private suspend fun ask(hello: BandProtocol.Hello): Boolean {
        val req = Request(hello.id, hello.name, CompletableDeferred())
        _requests.update { it.filterNot { r -> r.id == hello.id } + req }
        val ok = withTimeoutOrNull(BandSession.ANSWER_TIMEOUT_MS - 5_000L) { req.answer.await() } ?: false
        _requests.update { it.filterNot { r -> r === req } }
        return ok
    }

    /** The user answered a join request. */
    fun answer(request: Request, accept: Boolean) { request.answer.complete(accept) }

    /** Asks to join the band through device [peer] (the user over there taps Allow). */
    fun requestJoin(peer: Peer) {
        if (_joins.value[peer.id] == Join.WAITING) return
        _joins.update { it + (peer.id to Join.WAITING) }
        scope.launch {
            val r = runCatching {
                val s = dial(peer)
                BandSession(identity.hello()).join(s) { h -> trust.add(h.id, h.name, h.key) }
            }.getOrDefault(BandSession.JoinResult.FAILED)
            _joins.update { it + (peer.id to when (r) { BandSession.JoinResult.ACCEPTED -> Join.ACCEPTED; BandSession.JoinResult.DENIED -> Join.DENIED; BandSession.JoinResult.BUSY -> Join.BUSY; BandSession.JoinResult.VERSION -> Join.VERSION; else -> Join.FAILED }) }
            update()
        }
    }

    /** Removes the device from the band (applies right away). */
    fun remove(id: String) { trust.remove(id); _joins.update { it - id }; update() }

    private companion object {
        const val TAG = "stagebook"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val BURST_MS = 30_000L
        const val MAX_ASK = 200   // a setlist never goes over this; guards against an odd request
        const val SESSIONS = 8
        const val BEACON_TAG = "stagebook-band-1"
        const val BEACON_PORT = 47_810
        const val BEACON_EVERY_MS = 3_000L
        const val BEACON_GONE_MS = 12_000L
    }

    // went on stage: the search stops right away; left it, it comes back if the screen still needs it. Placed after all
    // fields: the collection runs on another thread and can call update() before the rest of the class exists
    init {
        scope.launch { stage.onStage.collect { update() } }
        scope.launch { repository.library.map { it.look.bandOn }.distinctUntilChanged().collect { update() } }
    }
}
