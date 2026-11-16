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
import java.util.UUID
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
    // the songs' PDFs: read to leave, checked and saved on arrival (defaults only for the tests that don't care)
    private val pdfs: PdfStore = PdfStore(context),
    private val inspector: PdfInspector = PdfInspector(),
) {
    /** What goes in a band package: the list (in QR format, in Base64) and the songs. */
    @kotlinx.serialization.Serializable
    private data class Package(val list: String, val songs: List<SetlistCloud.SharedSong> = emptyList(), val v: Int = BandProtocol.PACKAGE_BASE)

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

    @Volatile private var foreground = false   // written by the UI thread, read by the collector's
    private var searchUsers = 0   // open screens that need to find the others (Settings → Band, Share sheet)
    private val screenOpen get() = searchUsers > 0
    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var multicast: WifiManager.MulticastLock? = null

    /** Someone is in this device's band (the saved list, online or not): without it there is nobody to lead. */
    fun hasSavedMembers(): Boolean = trust.members.value.isNotEmpty()

    /** The stage is open (a show): nothing automatic runs then ([BandInternet] waits for it to close). */
    val stageOpen: StateFlow<Boolean> get() = stage.onStage

    /** The app came to the front / left it (MainActivity). In front, it follows Wi-Fi turning on and off. */
    fun setForeground(on: Boolean) {
        foreground = on
        syncNetCallback()
        _wifi.value = onWifi()
        update()
    }

    // the Wi-Fi callback wakes the app on every network change: only in front AND with the band on (nothing to follow
    // otherwise). Always worked out from the state NOW and idempotent (register only if not registered, unregister only
    // if registered), so a switch off and on too quick for the collector to see both, a state flow conflates, still
    // ends right. True = it registered or unregistered now.
    @Synchronized
    internal fun syncNetCallback(): Boolean {
        val want = foreground && repository.library.value.look.bandOn
        if (want == (netCallback != null)) return false
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (want) {
            if (cm == null) return false
            netCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) { _wifi.value = onWifi(); update() }
                override fun onLost(network: android.net.Network) { _wifi.value = onWifi(); update() }
            }.also { runCatching { cm.registerDefaultNetworkCallback(it) } }
        } else {
            netCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
            netCallback = null
        }
        return true
    }

    /** The band switch the collector has dealt with last (the tests wait for it to have seen a change). */
    @Volatile internal var seenOn: Boolean? = null
    @Volatile internal var collectError: Throwable? = null   // the last error of the band switch's collector (tests)
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

    internal fun packageBytes(list: ByteArray, songs: List<SetlistCloud.SharedSong>): ByteArray {
        val (wire, carriesPdf) = withPdfBytes(songs)
        return json.encodeToString(Package.serializer(), Package(java.util.Base64.getEncoder().encodeToString(list), wire, BandProtocol.packageVersion(list, carriesPdf))).toByteArray()
    }

    /**
     * The songs as they go on the wire: each PDF read from its file into base64, while the package's PDFs stay within
     * [br.app.stagebook.domain.BandContent.PDF_PACKAGE_BYTES] (the rest of the songs go without their PDF). True when at
     * least one PDF goes, which is what raises the package's version.
     */
    internal fun withPdfBytes(songs: List<SetlistCloud.SharedSong>): Pair<List<SetlistCloud.SharedSong>, Boolean> {
        var budget = br.app.stagebook.domain.BandContent.PDF_PACKAGE_BYTES
        var any = false
        val wire = songs.map { s ->
            val file = s.pdfFile.takeIf { it.isNotEmpty() }?.let(pdfs::file)?.takeIf { it.isFile }
            val size = file?.length() ?: 0L
            if (file == null || size > br.app.stagebook.domain.BandContent.MAX_PDF_BYTES || size > budget) s.forWire()
            else {
                budget -= size
                any = true
                s.forWire().copy(pdf = java.util.Base64.getEncoder().encodeToString(file.readBytes()), pdfName = s.pdfName)
            }
        }
        return wire to any
    }

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

    /**
     * A song asked of the band (title and artist, as in the QR) and which copy of it: [kind] is an [br.app.stagebook.domain.AskKind]
     * ("" = as it is). An older app doesn't send it and ignores it.
     */
    @kotlinx.serialization.Serializable
    data class Wanted(val title: String, val artist: String = "", val kind: String = "")

    // a connection to a device on the Wi-Fi, with the usual timeout (it was written out in five places)
    private fun dial(peer: Peer): Socket = dialSocket(Socket(), peer.host, peer.port, CONNECT_TIMEOUT_MS)

    // pair key with a member (null if they're no longer in the band or the saved key doesn't open)
    private fun pairKeyWith(id: String): ByteArray? {
        val member = trust.member(id) ?: return null
        val theirs = BandCrypto.publicKey(member.key) ?: return null
        return BandCrypto.pairKey(identity.privateKey, identity.id, theirs, member.id)
    }

    /** A song a band member answered with, and who it was. */
    class Answer(val song: SetlistCloud.SharedSong, val fromId: String, val fromName: String)

    /**
     * Asks everyone in the band on the network now who has the [wanted] songs; collects the answers (one per title).
     * Nobody on the other side has to tap anything.
     */
    suspend fun askMissing(wanted: List<Wanted>): List<SetlistCloud.SharedSong> = askBand(wanted).map { it.song }

    /** [askMissing], also saying who each song came from. */
    suspend fun askBand(wanted: List<Wanted>): List<Answer> = kotlinx.coroutines.withContext(Dispatchers.IO) io@{
        if (wanted.isEmpty()) return@io emptyList()
        val plain = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Wanted.serializer()), wanted).toByteArray()
        val answers = onlineMembers.value.map { peer ->
            this@io.async {
                val key = pairKeyWith(peer.id) ?: return@async emptyList()
                runCatching {
                    val s = dial(peer)
                    val body = BandSession(identity.hello()).ask(s, peer.id, ShareCode.encrypt(key, plain)) ?: return@async emptyList()
                    readAnswer(ShareCode.decrypt(key, body) ?: return@async emptyList()).map { Answer(it, peer.id, peer.name) }
                }.getOrDefault(emptyList())
            }
        }.flatMap { it.await() }
        pick(answers)
    }

    /** What a member answered, already decrypted: only what may travel, and no more than a setlist holds. */
    internal fun readAnswer(plain: ByteArray): List<SetlistCloud.SharedSong> =
        runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SetlistCloud.SharedSong.serializer()), plain.decodeToString()) }
            .getOrDefault(emptyList()).filter { it.allowed() }.take(br.app.stagebook.domain.BandContent.MAX_SONGS)
            // what the other device says about files on ITS side is never believed; a PDF that came as bytes is checked and saved
            // here like one in a package (header, size, password, pages)
            .let { takePdfs(it) }

    /**
     * One answer per song. More than one device has the same song: the version with chords wins (in the lyrics only
     * profile it's kept, hidden).
     */
    internal fun pick(answers: List<Answer>): List<Answer> =
        answers.groupBy { br.app.stagebook.domain.TextKey.of(it.song.title) + "|" + br.app.stagebook.domain.TextKey.of(it.song.author) }
            .values.map { versions -> versions.maxBy { if (br.app.stagebook.domain.Chords.hasChords(it.song.text)) 1 else 0 } }

    /**
     * Sends loose [songs] to one band member, only if that device is on this network now (never over the internet).
     * It gets them as any package from the band; true if it stored them.
     */
    suspend fun sendSongsTo(memberId: String, songs: List<br.app.stagebook.data.model.Song>): Boolean {
        val peer = onlineMembers.value.firstOrNull { it.id == memberId } ?: return false
        val (list, shared) = SetlistCloud.looseSongs(songs) ?: return false
        return sendTo(peer, packageFor(memberId, list, shared) ?: return false)
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
    fun songsFor(wanted: List<Wanted>): List<String> = found(wanted).map { it.second.id }.distinct()

    // the songs here that answer each request, and only when they are the copy asked for (a PDF asked for and none here, or
    // chords asked for and the song has only lyrics, is no answer: the one who asked hears "nobody has it")
    private fun found(wanted: List<Wanted>): List<Pair<Wanted, br.app.stagebook.data.model.Song>> {
        val library = repository.library.value.songs
        return wanted.take(MAX_ASK).mapNotNull { w ->
            val song = br.app.stagebook.domain.SetlistMatcher.matchExact(QrPayload.Entry(w.title, w.artist), library) ?: return@mapNotNull null
            val pdfHere = song.pdf.isNotEmpty() && pdfs.file(song.pdf).isFile
            if (br.app.stagebook.domain.AskKind.has(song, w.kind, pdfHere)) w to song else null
        }
    }

    /** A band member asked for songs and was answered ([BandConduct]: the leader hears the one it leads is getting them). */
    @Volatile var askedHandler: ((fromId: String, wanted: List<Wanted>) -> Unit)? = null

    // a band member asked: answer with the ones here (same rule as the QR: title, with the artist as tiebreaker)
    private fun answer(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null
        val plain = ShareCode.decrypt(key, body) ?: return null
        val wanted = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Wanted.serializer()), plain.decodeToString()) }.getOrNull() ?: return null
        val found = shapedAnswer(wanted)
        if (found.isNotEmpty()) askedHandler?.invoke(from.id, wanted)
        return ShareCode.encrypt(key, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SetlistCloud.SharedSong.serializer()), found).toByteArray())
    }

    /** What a request is answered with, as it goes on the wire: each song as the copy asked for, the PDFs' bytes inside. */
    internal fun shapedAnswer(wanted: List<Wanted>): List<SetlistCloud.SharedSong> {
        val shaped = found(wanted).map { (w, s) ->
            // the copy asked for: lyrics without the chords, the chart as it is, or the PDF with its pages (checked on the other side)
            SetlistCloud.SharedSong(s.title, s.author, s.year, br.app.stagebook.domain.AskKind.textFor(s.text, w.kind), pdfName = if (w.kind == br.app.stagebook.domain.AskKind.PDF) s.pdfName else "", pdfFile = if (w.kind == br.app.stagebook.domain.AskKind.PDF) s.pdf else "")
        }
        // the PDFs share the budget of one package; a song whose file doesn't fit goes without it (and the asker says so)
        return withPdfBytes(shaped).first
    }

    /**
     * Who takes songs of a band package straight into the library ([BandConduct], during a show, and only what the
     * rule there lets in): gets who sent, the list's text and the songs, and returns the songs left for the inbox.
     */
    @Volatile var showIntake: ((fromId: String, fromName: String, text: String, songs: List<SetlistCloud.SharedSong>) -> List<SetlistCloud.SharedSong>)? = null

    // package from a band member: decrypt with the pair key and keep it in the inbox
    private fun receive(from: BandProtocol.Hello, body: String): Boolean {
        val member = trust.member(from.id) ?: return false
        val key = pairKeyWith(member.id) ?: return false
        val plain = ShareCode.decrypt(key, body) ?: return false
        return accept(member.id, from.name.ifBlank { member.name }, plain)
    }

    /** A package from band member [fromId], already decrypted: kept in the inbox, but for what [showIntake] took. */
    internal fun accept(fromId: String, fromName: String, plain: ByteArray): Boolean {
        val pkg = openPackage(plain) ?: return false
        // the PDFs are checked and saved here, before anything else sees the songs (only a member's package gets this far)
        val songs = takePdfs(pkg.songs.take(br.app.stagebook.domain.BandContent.MAX_SONGS))   // a package of thousands of songs is not opened one by one
        val rest = showIntake?.invoke(fromId, fromName, pkg.text, songs) ?: songs
        // every song went straight in: nothing is left to review
        if (rest.isEmpty() && songs.isNotEmpty()) return true
        inbox.add(fromId, fromName, pkg.text, rest, pkg.breaks)
        return true
    }

    /**
     * Each PDF that came ([SetlistCloud.SharedSong.pdf], base64) is checked ([PdfInspector]: header, 8 MB, readable, no
     * password, 300 pages) and saved under a generated name; the song then carries only that file's name. One that fails
     * is discarded and the song comes without it, with the reason in [SetlistCloud.SharedSong.pdfProblem]. What a sender
     * says about this device's files is never believed.
     */
    internal fun takePdfs(songs: List<SetlistCloud.SharedSong>): List<SetlistCloud.SharedSong> {
        var budget = br.app.stagebook.domain.BandContent.PDF_PACKAGE_BYTES
        val maxChars = (br.app.stagebook.domain.BandContent.MAX_PDF_BYTES + 2) / 3 * 4
        return songs.map { s ->
            val clean = s.copy(pdf = "", pdfFile = "", pdfProblem = "", pdfName = "")
            if (s.pdf.isEmpty()) return@map clean
            val bytes = if (s.pdf.length > maxChars) null else runCatching { java.util.Base64.getDecoder().decode(s.pdf) }.getOrNull()
            val problem = when {
                s.pdf.length > maxChars || (bytes != null && bytes.size > budget) -> PdfProblem.TOO_BIG
                bytes == null -> PdfProblem.NOT_PDF
                else -> null
            }
            if (problem != null || bytes == null) return@map clean.copy(pdfProblem = (problem ?: PdfProblem.NOT_PDF).name)
            when (val saved = pdfs.saveCheckedNow(UUID.randomUUID().toString(), bytes, inspector)) {
                is PdfAttach.Saved -> { budget -= bytes.size; clean.copy(pdfFile = saved.name, pdfName = shownPdfName(s.pdfName)) }
                is PdfAttach.Refused -> clean.copy(pdfProblem = saved.problem.name)
            }
        }
    }

    // a name the sender wrote, to show: plain text, no folders, short
    private fun shownPdfName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').filter { !it.isISOControl() }.trim().take(br.app.stagebook.domain.BandContent.MAX_PDF_NAME).ifEmpty { "PDF" }

    /** Keeps loose [songs] from a band member in the inbox, to be reviewed like anything the band sends. */
    internal fun keep(fromId: String, fromName: String, songs: List<SetlistCloud.SharedSong>) =
        inbox.add(fromId, fromName, SetlistCloud.looseList(songs.map { it.title to it.author }), songs)

    /** What a band package holds, already decrypted: the list's text, the songs and the list's breaks (null = it says none). */
    internal class Opened(val text: String, val songs: List<SetlistCloud.SharedSong>, val breaks: List<Int>?)

    internal fun openPackage(plain: ByteArray): Opened? {
        val pkg = runCatching { json.decodeFromString(Package.serializer(), plain.decodeToString()) }.getOrNull() ?: return null
        if (pkg.v > BandProtocol.VERSION) return null   // a newer format: not guessed (the network already says "update" in the Hello)
        val packed = runCatching { java.util.Base64.getDecoder().decode(pkg.list) }.getOrNull() ?: return null
        val text = QrPayload.unpack(packed) ?: return null
        if (QrPayload.parse(text) == null) return null
        return Opened(text, pkg.songs, br.app.stagebook.domain.SharedBreaks.read(packed, text))
    }

    @Synchronized
    fun update() {
        syncNetCallback()   // from the state now: a change of the band switch the collector never saw (a state flow conflates) is caught here
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
        scope.launch {
            // the callback is reconciled on every emission (cheap, and the state read is the current one); the rest only
            // when the switch changed or the callback did
            var last: Boolean? = null
            repository.library.map { it.look.bandOn }.collect { on ->
                // an error in one pass must not end the collector: the next change would find nobody listening
                try {
                    val changed = syncNetCallback()
                    if (changed || on != last) { last = on; _wifi.value = onWifi(); update() }
                    seenOn = on
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    collectError = e
                    Log.w(TAG, "band switch: $e")
                }
            }
        }
    }
}

/**
 * Connects [socket] to a device on the Wi-Fi. A connect that fails (timeout, refused) closes the socket before the
 * error goes up: the callers swallow it, and the descriptor stayed open until the GC (audit 14/11).
 */
internal fun dialSocket(socket: Socket, host: InetAddress, port: Int, timeoutMs: Int = 5_000): Socket {
    try {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
    } catch (e: Throwable) {
        runCatching { socket.close() }
        throw e
    }
    return socket
}
