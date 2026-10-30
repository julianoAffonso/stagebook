package br.app.stagebook.data

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.stateIn
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.BandCrypto
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.Immutable
import br.app.stagebook.domain.BandProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * - **announcement + server** (almost nothing: Android answers and the server sits waiting): app in front, local
 *   network and (a saved band member, or a searching screen open);
 * - **search + multicast lock** (the costly part: network queries and the Wi-Fi waking for each packet): only while a
 *   screen needs it ([setScreenOpen]) and **never on stage**.
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val nsd get() = context.getSystemService(NsdManager::class.java)

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
            val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
            BandSession(identity.hello()).send(s, peer.id, body, kind)
        }.getOrDefault(false)
    }

    /** The package (list + songs) encrypted for a member, ready for [sendTo]. */
    fun packageFor(memberId: String, list: ByteArray, songs: List<SetlistCloud.SharedSong>): String? {
        val key = pairKeyWith(memberId) ?: return null
        val plain = json.encodeToString(Package.serializer(), Package(java.util.Base64.getEncoder().encodeToString(list), songs)).toByteArray()
        return ShareCode.encrypt(key, plain)
    }

    /**
     * Searches for the band briefly (on app open with something pending: sends with no receipt, an unconfirmed "ok"),
     * to catch up with whoever is on the same Wi-Fi. Never on stage (the search already respects that).
     */
    fun burst(ms: Long = BURST_MS, evenOnStage: Boolean = false) {
        if (evenOnStage) { onStageSearch++; update() } else setScreenOpen(true)
        scope.launch {
            kotlinx.coroutines.delay(ms)
            if (evenOnStage) { onStageSearch = (onStageSearch - 1).coerceAtLeast(0); update() } else setScreenOpen(false)
        }
    }

    // only "Lead the band" searches on stage, and always briefly (burst): find whoever came back to the Wi-Fi
    @Volatile private var onStageSearch = 0

    /** Sends a setlist or songs to everyone in the band on the network now; returns the ids of who got it. */
    suspend fun send(list: ByteArray, songs: List<SetlistCloud.SharedSong>): Set<String> = kotlinx.coroutines.withContext(Dispatchers.IO) io@{
        val plain = json.encodeToString(Package.serializer(), Package(java.util.Base64.getEncoder().encodeToString(list), songs)).toByteArray()
        onlineMembers.value.map { peer ->
            this@io.async {
                val member = trust.member(peer.id) ?: return@async null
                val theirs = BandCrypto.publicKey(member.key) ?: return@async null
                val key = BandCrypto.pairKey(identity.privateKey, identity.id, theirs, member.id)
                val ok = runCatching {
                    val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
                    BandSession(identity.hello()).send(s, peer.id, ShareCode.encrypt(key, plain))
                }.getOrDefault(false)
                if (ok) peer.id else null
            }
        }.mapNotNull { it.await() }.toSet()
    }

    /** A song asked of the band (title and artist, as in the QR). */
    @kotlinx.serialization.Serializable
    data class Wanted(val title: String, val artist: String = "")

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
                    val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
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
                    val key = pairKeyWith(peer.id) ?: return@async null
                    runCatching {
                        val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
                        val body = BandSession(identity.hello()).ask(s, peer.id, ShareCode.encrypt(key, plain), BandProtocol.Kind.CONDUCT)
                            ?: return@async null
                        val bytes = ShareCode.decrypt(key, body) ?: return@async null
                        peer.id to json.decodeFromString(br.app.stagebook.domain.Conduct.Lack.serializer(), bytes.decodeToString())
                    }.getOrNull()
                }
            }.mapNotNull { it.await() }.toMap()
        }

    private fun onConduct(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null
        val msg = ShareCode.decrypt(key, body)?.let { br.app.stagebook.domain.Conduct.decode(it.decodeToString()) } ?: return null
        val lack = conductHandler?.invoke(from, msg) ?: return null
        return ShareCode.encrypt(key, json.encodeToString(br.app.stagebook.domain.Conduct.Lack.serializer(), lack).toByteArray())
    }

    /** The songs here that answer a request (same rule as the QR: title, with the artist as tiebreaker). */
    fun songsFor(wanted: List<Wanted>): List<String> {
        val library = repository.library.value.songs
        return wanted.take(MAX_ASK).mapNotNull { w ->
            (br.app.stagebook.domain.SetlistMatcher.match(QrPayload.Entry(w.title, w.artist), library) as? br.app.stagebook.domain.QrMatch.Found)?.song?.id
        }.distinct()
    }

    // a band member asked: answer with the ones here (same rule as the QR: title, with the artist as tiebreaker)
    private fun answer(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null
        val plain = ShareCode.decrypt(key, body) ?: return null
        val wanted = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Wanted.serializer()), plain.decodeToString()) }.getOrNull() ?: return null
        val library = repository.library.value.songs
        val found = wanted.take(MAX_ASK).mapNotNull { w ->
            (br.app.stagebook.domain.SetlistMatcher.match(QrPayload.Entry(w.title, w.artist), library) as? br.app.stagebook.domain.QrMatch.Found)?.song
        }.map { SetlistCloud.SharedSong(it.title, it.author, it.year, it.text) }
        return ShareCode.encrypt(key, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SetlistCloud.SharedSong.serializer()), found).toByteArray())
    }

    // package from a band member: decrypt with the pair key and keep it in the inbox
    private fun receive(from: BandProtocol.Hello, body: String): Boolean {
        val member = trust.member(from.id) ?: return false
        val theirs = BandCrypto.publicKey(member.key) ?: return false
        val key = BandCrypto.pairKey(identity.privateKey, identity.id, theirs, member.id)
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

    /**
     * Is there a local network? On Wi-Fi (or cable), or **running a hotspot**: the phone sharing internet at the bar
     * gets internet over 4G, but the others' network goes through one of its interfaces (wlan/ap/swlan) with a local
     * address.
     */
    fun onWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) return true
        return hotspotUp()
    }

    private fun hotspotUp(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { nif ->
            nif.isUp && !nif.isLoopback && HOTSPOT.any { nif.name.startsWith(it) } &&
                nif.inetAddresses.toList().any { it is Inet4Address && it.isSiteLocalAddress }
        }
    }.getOrDefault(false)

    private var searching = false

    // the search turns on and off by itself, without touching the announcement: a screen that needs it open and off
    // stage
    private fun updateSearch() {
        val want = _running.value && ((screenOpen && !stage.onStage.value) || onStageSearch > 0)
        if (want && !searching) startSearch() else if (!want && searching) stopSearch()
    }

    private fun startSearch() {
        searching = true
        multicast = context.applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock("stagebook-band")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        discover()
    }

    private fun stopSearch() {
        searching = false
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        synchronized(toResolve) { toResolve.clear() }
    }

    private fun start() {
        _running.value = true
        val socket = runCatching { ServerSocket(0) }.getOrElse { Log.w(TAG, "porta: $it"); _running.value = false; return }
        server = socket
        serverJob = scope.launch {
            val session = BandSession(identity.hello())
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch {
                    runCatching {
                        session.serve(
                            client, trust::isMember, ::ask, { h -> trust.add(h.id, h.name, h.key); update() },
                            busy = { stage.onStage.value }, onPackage = ::receive, onAsk = ::answer,
                            onCodeAck = { h, body -> codeAckHandler?.invoke(h, body) ?: false }, onConduct = ::onConduct,
                        )
                    }
                }
            }
        }
        register(socket.localPort)
    }

    private fun stop() {
        stopSearch()
        _running.value = false
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        server = null
        serverJob?.cancel()
        _peers.value = emptyList()
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Stagebook"
            serviceType = BandProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute(ATTR_ID, identity.id)
            setAttribute(ATTR_NAME, identity.name.value)
            setAttribute(ATTR_V, BandProtocol.VERSION.toString())
            setAttribute(ATTR_APP, identity.appVersion)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { Log.w(TAG, "anúncio falhou: $error") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // Android's NSD resolves one service at a time: a simple queue
    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private fun discover() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) { Log.w(TAG, "procura falhou: $error") }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { synchronized(toResolve) { toResolve.addLast(info) }; resolveNext() }
            override fun onServiceLost(info: NsdServiceInfo) {
                // without the id here: whoever had this service name goes away; the next announcement brings back
                // whoever is still there
                _peers.update { list -> list.filterNot { names[it.id] == info.serviceName } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(BandProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private val names = mutableMapOf<String, String>()   // id → announced service name

    @Suppress("DEPRECATION")   // resolveService: the replacement (registerServiceInfoCallback) is Android 14 only
    private fun resolveNext() {
        val next = synchronized(toResolve) { if (resolving) null else toResolve.removeFirstOrNull()?.also { resolving = true } } ?: return
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) { done() }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val id = info.attributes[ATTR_ID]?.decodeToString()
                    val name = info.attributes[ATTR_NAME]?.decodeToString().orEmpty().take(BandProtocol.MAX_NAME)
                    val host = info.host
                    if (id != null && id != identity.id && host != null) {
                        names[id] = info.serviceName
                        val v = info.attributes[ATTR_V]?.decodeToString()?.toIntOrNull() ?: BandProtocol.VERSION
                        val app = info.attributes[ATTR_APP]?.decodeToString().orEmpty().take(20)
                        _peers.update { list -> (list.filterNot { it.id == id } + Peer(id, name, host, info.port, v, app)).sortedBy { it.name.lowercase() } }
                    }
                    done()
                }

                fun done() { synchronized(toResolve) { resolving = false }; resolveNext() }
            })
        }.onFailure { synchronized(toResolve) { resolving = false } }
    }

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
                val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
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
        const val ATTR_ID = "id"
        const val ATTR_NAME = "name"
        const val ATTR_V = "v"
        const val ATTR_APP = "app"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val BURST_MS = 30_000L
        const val MAX_ASK = 200   // a setlist never goes over this; guards against an odd request
        val HOTSPOT = listOf("wlan", "ap", "swlan", "softap")   // hotspot interface names on Android devices
    }

    // went on stage: the search stops right away; left it, it comes back if the screen still needs it. Placed after all
    // fields: the collection runs on another thread and can call update() before the rest of the class exists
    init {
        scope.launch { stage.onStage.collect { update() } }
        scope.launch { repository.library.map { it.look.bandOn }.distinctUntilChanged().collect { update() } }
    }
}
