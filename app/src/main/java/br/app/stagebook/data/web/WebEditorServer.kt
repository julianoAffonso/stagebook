package br.app.stagebook.data.web

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import br.app.stagebook.R
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.Languages
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.essential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The editor on the computer (T37): a small HTTP server on the local network, on port [PORT] (or the next free one).
 * On only with the switch on, outside essential mode and with the app in front (closing the app turns it off).
 */
@Singleton
class WebEditorServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val identity: DeviceIdentity,
    val auth: WebEditorAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var foreground = false

    /** "http://192.168.0.10:8090" while on; empty when off or with no local network. */
    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address

    private val idle = IdleTimer(IDLE_MS)
    private var idleJob: Job? = null

    val routes = WebEditorRoutes(auth, ::page, ::strings, { identity.name.value }, onAccess = idle::touch)

    init {
        // every app start begins with the editor off (user's decision of 30/10): it's turned on on purpose, each time
        scope.launch {
            repository.loaded.first { it }
            turnOff()
        }
        scope.launch {
            repository.library.map { it.look.webEditorOn && !it.look.essential }.distinctUntilChanged().collect { update() }
        }

    }

    fun setForeground(on: Boolean) {
        foreground = on
        update()
    }

    @Synchronized
    fun update() {
        val look = repository.library.value.look
        val want = look.webEditorOn && !look.essential && foreground
        if (want && server == null) start() else if (!want && server != null) stop()
        else if (want) _address.value = addressOf(server?.localPort ?: 0)
    }

    private fun start() {
        val socket = (PORT until PORT + 10).firstNotNullOfOrNull { port ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
        } ?: run { Log.w(TAG, "no free port"); return }
        server = socket
        _address.value = addressOf(socket.localPort)
        idle.touch()
        idleJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(IDLE_CHECK_MS)
                if (idle.expired()) turnOff()
            }
        }
        serverJob = scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { serve(client) }
            }
        }
    }

    private fun stop() {
        runCatching { server?.close() }
        server = null
        serverJob?.cancel()
        idleJob?.cancel()
        _address.value = ""
    }

    // switches it off in Settings too: to use it again, turn it on there
    private fun turnOff() {
        if (repository.library.value.look.webEditorOn) repository.updateLook { it.copy(webEditorOn = false) }
    }

    private fun serve(client: Socket) = client.use { s ->
        s.soTimeout = TIMEOUT_MS
        val response = runCatching {
            val req = WebHttp.read(s.getInputStream(), s.inetAddress?.hostAddress.orEmpty()) { MAX_BODY }
            routes.handle(req)
        }.getOrElse { e -> HttpResponse.status((e as? HttpError)?.status ?: 400) }
        runCatching { WebHttp.write(s.getOutputStream(), response) }
    }

    // the address the computer types: the Wi-Fi's (or the hotspot's) IPv4
    private fun addressOf(port: Int): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromWifi = cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses.orEmpty()
            .map { it.address }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        val ip = fromWifi ?: runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()
        return ip?.let { "http://${it.hostAddress}:$port" }.orEmpty()
    }

    private fun page(): String = context.assets.open("web/index.html").bufferedReader().use { it.readText() }

    // the page's texts, in the app's language (Settings → Language): translating the app translates the editor
    private fun strings(): Map<String, String> {
        val localized = Languages.wrap(context)
        return WEB_STRINGS.mapValues { localized.getString(it.value) }
    }

    companion object {
        private const val TAG = "WebEditor"
        const val PORT = 8090
        private const val TIMEOUT_MS = 15_000
        const val MAX_BODY = 2 * 1024 * 1024
        const val IDLE_MS = 5 * 60_000L
        private const val IDLE_CHECK_MS = 30_000L

        /** Texts the page uses, by the name it knows them. */
        val WEB_STRINGS = mapOf(
            "title" to R.string.web_title,
            "ask" to R.string.web_ask,
            "asking" to R.string.web_asking,
            "denied" to R.string.web_denied,
            "busy" to R.string.web_busy,
            "connected" to R.string.web_connected,
            "offline" to R.string.web_offline,
        )
    }
}
