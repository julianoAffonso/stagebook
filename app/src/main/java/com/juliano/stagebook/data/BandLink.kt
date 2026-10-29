package com.juliano.stagebook.data

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.stateIn
import com.juliano.stagebook.domain.ShareCode
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.domain.BandCrypto
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.Immutable
import com.juliano.stagebook.domain.BandProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
 * A banda na rede local (Wi-Fi do lugar ou o ponto de acesso de um celular; sem internet): anuncia este aparelho, acha os
 * outros e atende os pedidos de entrada. Gasta o mínimo do tablet (pedido do usuário, 25/10):
 * - **anúncio + servidor** (quase nada: o Android responde e o servidor fica parado esperando): app na frente, rede local
 *   e (alguém da banda guardado, ou uma tela que procura aberta);
 * - **procura + multicast lock** (o que gasta: perguntas na rede e o Wi-Fi acordando para cada pacote): só enquanto uma
 *   tela precisa dela ([setScreenOpen]) e **nunca no palco**.
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
    /** O que vai num pacote da banda: a lista (no formato do QR, em Base64) e as músicas. */
    @kotlinx.serialization.Serializable
    private data class Package(val list: String, val songs: List<SetlistCloud.SharedSong> = emptyList(), val v: Int = BandProtocol.VERSION)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    /** Um aparelho com o Stagebook na mesma rede. */
    @Immutable
    data class Peer(val id: String, val name: String, val host: InetAddress, val port: Int, val v: Int = BandProtocol.VERSION, val app: String = "")

    /** Alguém pedindo para entrar na banda por este aparelho (o usuário responde Autorizar / Não autorizar). */
    @Immutable
    class Request(val id: String, val name: String, internal val answer: CompletableDeferred<Boolean>)

    enum class Join { WAITING, ACCEPTED, DENIED, BUSY, VERSION, FAILED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests: StateFlow<List<Request>> = _requests

    /** Pedidos que este aparelho fez, por id do outro. */
    private val _joins = MutableStateFlow<Map<String, Join>>(emptyMap())
    val joins: StateFlow<Map<String, Join>> = _joins

    private val _running = MutableStateFlow(false)
    /** Anunciado na rede (o servidor esperando). A procura dos outros é à parte e mais curta. */
    val running: StateFlow<Boolean> = _running

    private var foreground = false
    private var searchUsers = 0   // telas abertas que precisam achar os outros (Ajustes → Banda, folha Compartilhar)
    private val screenOpen get() = searchUsers > 0
    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val nsd get() = context.getSystemService(NsdManager::class.java)

    /** O app foi para a frente / saiu dela (MainActivity). Na frente, acompanha o Wi-Fi ligar e desligar. */
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
    /** No Wi-Fi agora (a tela mostra "conecte no Wi-Fi" sem ele). */
    val wifi: StateFlow<Boolean> = _wifi

    /** Uma tela que precisa achar os outros abriu (true) ou fechou (false): Ajustes → Banda, a folha Compartilhar. */
    @Synchronized
    fun setScreenOpen(on: Boolean) { searchUsers = (searchUsers + if (on) 1 else -1).coerceAtLeast(0); update() }

    /** Quem é da banda e está na rede agora (para "Banda na rede (N)"). */
    val onlineMembers: StateFlow<List<Peer>> = kotlinx.coroutines.flow.combine(_peers, trust.members) { peers, members ->
        val ids = members.map { it.id }.toSet()
        peers.filter { it.id in ids && it.v == BandProtocol.VERSION }   // outra versão não recebe: fica na fila
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Quem confere um "ok" das 3 palavras que chegou pela rede (a [BandInternet], que guarda os meus envios). */
    @Volatile var codeAckHandler: ((BandProtocol.Hello, String) -> Boolean)? = null

    /** Manda para um aparelho da rede (pacote cifrado com a chave do par, ou um CODE_ACK); true se ele guardou. */
    suspend fun sendTo(peer: Peer, body: String, kind: String = BandProtocol.Kind.PACKAGE): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
            BandSession(identity.hello()).send(s, peer.id, body, kind)
        }.getOrDefault(false)
    }

    /** O pacote (lista + músicas) cifrado para um membro, pronto para [sendTo]. */
    fun packageFor(memberId: String, list: ByteArray, songs: List<SetlistCloud.SharedSong>): String? {
        val key = pairKeyWith(memberId) ?: return null
        val plain = json.encodeToString(Package.serializer(), Package(java.util.Base64.getEncoder().encodeToString(list), songs)).toByteArray()
        return ShareCode.encrypt(key, plain)
    }

    /**
     * Procura a banda por pouco tempo (ao abrir o app com algo pendente: envios sem recibo, "ok" sem confirmação), para
     * acertar com quem estiver no mesmo Wi-Fi. Nunca no palco (a procura já respeita isso).
     */
    fun burst(ms: Long = BURST_MS, evenOnStage: Boolean = false) {
        if (evenOnStage) { onStageSearch++; update() } else setScreenOpen(true)
        scope.launch {
            kotlinx.coroutines.delay(ms)
            if (evenOnStage) { onStageSearch = (onStageSearch - 1).coerceAtLeast(0); update() } else setScreenOpen(false)
        }
    }

    // só o "Quem conduz" procura no palco, e sempre por pouco tempo (burst): achar quem voltou para o Wi-Fi
    @Volatile private var onStageSearch = 0

    /** Manda um setlist ou músicas para todos da banda na rede agora; devolve os ids de quem recebeu. */
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

    /** Uma música pedida à banda (título e artista, como no QR). */
    @kotlinx.serialization.Serializable
    data class Wanted(val title: String, val artist: String = "")

    // chave do par com um membro (null se ele não é mais da banda ou a chave guardada não abre)
    private fun pairKeyWith(id: String): ByteArray? {
        val member = trust.member(id) ?: return null
        val theirs = BandCrypto.publicKey(member.key) ?: return null
        return BandCrypto.pairKey(identity.privateKey, identity.id, theirs, member.id)
    }

    /**
     * Pergunta a todos da banda na rede agora quem tem as músicas [wanted]; junta as respostas (a primeira que chegar de
     * cada título). Ninguém do outro lado precisa tocar em nada.
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
                        .filter { it.allowed() }.take(com.juliano.stagebook.domain.BandContent.MAX_SONGS)
                }.getOrDefault(emptyList())
            }
        }.flatMap { it.await() }
        // mais de um aparelho tem a mesma música: fica a versão com cifra (no perfil só letra ela fica guardada, escondida)
        answers.groupBy { com.juliano.stagebook.domain.TextKey.of(it.title) + "|" + com.juliano.stagebook.domain.TextKey.of(it.author) }
            .values.map { versions -> versions.maxBy { if (com.juliano.stagebook.domain.Chords.hasChords(it.text)) 1 else 0 } }
    }

    /** Quem acompanha recebe a mensagem de quem conduz e devolve o que falta aqui (null = não está acompanhando). */
    @Volatile var conductHandler: ((BandProtocol.Hello, com.juliano.stagebook.domain.Conduct.Msg) -> com.juliano.stagebook.domain.Conduct.Lack?)? = null

    /**
     * "Quem conduz": manda [msg] aos da banda online, ao mesmo tempo, e junta o que falta para cada um (id → falta; quem
     * não respondeu fica de fora do mapa e a tela trata como "não respondeu").
     */
    suspend fun conduct(msg: com.juliano.stagebook.domain.Conduct.Msg): Map<String, com.juliano.stagebook.domain.Conduct.Lack> =
        kotlinx.coroutines.withContext(Dispatchers.IO) io@{
            val plain = com.juliano.stagebook.domain.Conduct.encode(msg).toByteArray()
            onlineMembers.value.map { peer ->
                this@io.async {
                    val key = pairKeyWith(peer.id) ?: return@async null
                    runCatching {
                        val s = Socket().apply { connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS) }
                        val body = BandSession(identity.hello()).ask(s, peer.id, ShareCode.encrypt(key, plain), BandProtocol.Kind.CONDUCT)
                            ?: return@async null
                        val bytes = ShareCode.decrypt(key, body) ?: return@async null
                        peer.id to json.decodeFromString(com.juliano.stagebook.domain.Conduct.Lack.serializer(), bytes.decodeToString())
                    }.getOrNull()
                }
            }.mapNotNull { it.await() }.toMap()
        }

    private fun onConduct(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null
        val msg = ShareCode.decrypt(key, body)?.let { com.juliano.stagebook.domain.Conduct.decode(it.decodeToString()) } ?: return null
        val lack = conductHandler?.invoke(from, msg) ?: return null
        return ShareCode.encrypt(key, json.encodeToString(com.juliano.stagebook.domain.Conduct.Lack.serializer(), lack).toByteArray())
    }

    // alguém da banda perguntou: responde com as que estão aqui (mesma regra do QR: título, e o artista desempata)
    private fun answer(from: BandProtocol.Hello, body: String): String? {
        val key = pairKeyWith(from.id) ?: return null
        val plain = ShareCode.decrypt(key, body) ?: return null
        val wanted = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Wanted.serializer()), plain.decodeToString()) }.getOrNull() ?: return null
        val library = repository.library.value.songs
        val found = wanted.take(MAX_ASK).mapNotNull { w ->
            (com.juliano.stagebook.domain.SetlistMatcher.match(QrPayload.Entry(w.title, w.artist), library) as? com.juliano.stagebook.domain.QrMatch.Found)?.song
        }.map { SetlistCloud.SharedSong(it.title, it.author, it.year, it.text) }
        return ShareCode.encrypt(key, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SetlistCloud.SharedSong.serializer()), found).toByteArray())
    }

    // pacote de alguém da banda: decifra com a chave do par e guarda na caixa de entrada
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
        val want = foreground && onWifi() && (screenOpen || trust.members.value.isNotEmpty())
        if (want && !_running.value) start() else if (!want && _running.value) stop()
        updateSearch()
    }

    /**
     * Tem rede local? No Wi-Fi (ou cabo), ou **criando o ponto de acesso**: o celular que divide a internet no bar fica com
     * a internet pelo 4G, mas a rede dos outros passa por uma interface dele (wlan/ap/swlan) com endereço local.
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

    // a procura liga e desliga sozinha, sem mexer no anúncio: tela que precisa aberta e fora do palco
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

    // o NSD do Android resolve um serviço por vez: uma fila simples
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
                // sem o id aqui: some quem tinha esse nome de serviço; o próximo anúncio traz de volta quem continua
                _peers.update { list -> list.filterNot { names[it.id] == info.serviceName } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(BandProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private val names = mutableMapOf<String, String>()   // id → nome do serviço anunciado

    @Suppress("DEPRECATION")   // resolveService: o substituto (registerServiceInfoCallback) é só do Android 14
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

    /** O usuário respondeu a um pedido de entrada. */
    fun answer(request: Request, accept: Boolean) { request.answer.complete(accept) }

    /** Pede para entrar na banda pelo aparelho [peer] (o usuário de lá toca Autorizar). */
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

    /** Tira o aparelho da banda (vale na hora). */
    fun remove(id: String) { trust.remove(id); _joins.update { it - id }; update() }

    private companion object {
        const val TAG = "stagebook"
        const val ATTR_ID = "id"
        const val ATTR_NAME = "name"
        const val ATTR_V = "v"
        const val ATTR_APP = "app"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val BURST_MS = 30_000L
        const val MAX_ASK = 200   // um setlist nunca passa disso; protege de pedido estranho
        val HOTSPOT = listOf("wlan", "ap", "swlan", "softap")   // nomes da interface do ponto de acesso nos Androids
    }

    // entrou no palco: a procura para na hora; saiu, volta se a tela ainda precisar. Fica depois de todos os campos:
    // a coleta roda em outra linha de execução e pode chamar update() antes de o resto da classe existir
    init { scope.launch { stage.onStage.collect { update() } } }
}
