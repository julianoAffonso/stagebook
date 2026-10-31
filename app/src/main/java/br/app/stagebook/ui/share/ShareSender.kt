package br.app.stagebook.ui.share

import androidx.compose.ui.graphics.asImageBitmap
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.domain.QrEncoder
import br.app.stagebook.domain.ShareCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the two Share sheets (songs and setlist) do the same way: the 3 words over the internet (code, its QR, who
 * accepted) and the send to the band (nearby on Wi-Fi, the others over the internet). It was copied in both view
 * models (audit 30/10); each one now only says what goes.
 */
class ShareSender(
    private val scope: CoroutineScope,
    repository: LibraryRepository,
    private val connectivity: Connectivity,
    private val bandLink: BandLink,
    bandTrust: BandTrust,
    private val bandInternet: BandInternet,
) {
    val internetOn: StateFlow<Boolean> = repository.library.map { it.look.internetOn }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.internetOn)
    private val internet = combine(connectivity.online, internetOn) { a, b -> a && b }   // essential mode (T19)

    private val _cloud = MutableStateFlow<CloudUi?>(null)
    val cloud: StateFlow<CloudUi?> = _cloud

    /**
     * How many band members we can send to right now ("Send to the band (N)"): all of them if someone is on Wi-Fi or
     * there's internet; 0 = the option doesn't show.
     */
    val bandOnline: StateFlow<Int> = combine(bandTrust.members, bandLink.onlineMembers, internet) { members, lan, net ->
        if (members.isNotEmpty() && (lan.isNotEmpty() || net)) members.size else 0
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    private val bandSent = Channel<Int>(Channel.BUFFERED)
    /** How many band devices got the last send (0 = nobody got it). */
    val bandResult: Flow<Int> = bandSent.receiveAsFlow()

    private var joinsJob: Job? = null

    /** Sends over the internet; the code (and its QR, to photograph or scan) shows in a dialog titled [name]. */
    fun sendCode(name: String, send: suspend () -> SetlistCloud.Sent?) {
        if (_cloud.value == CloudUi.Sending) return
        _cloud.value = CloudUi.Sending
        scope.launch {
            val sent = send()
            val qr = sent?.let { s ->
                withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(ShareCode.qrText(s.code).toByteArray(), 600).asImageBitmap() }.getOrNull() }
            }
            sent?.let { bandInternet.remember(it.code); watchJoins(it.code) }
            _cloud.value = sent?.let { CloudUi.Sent(it.code, name, it.songs, it.skipped, qr) } ?: CloudUi.Failed
        }
    }

    fun dismissCloud() { _cloud.value = null; joinsJob?.cancel() }

    // with the words dialog open, checks now and then who accepted (they join the band); once closed, it stops
    private fun watchJoins(code: String) {
        joinsJob?.cancel()
        joinsJob = scope.launch {
            repeat(JOIN_CHECKS) {
                delay(JOIN_EVERY_MS)
                val names = bandInternet.checkAcks(code)
                if (names.isNotEmpty()) _cloud.update { c -> (c as? CloudUi.Sent)?.let { it.copy(joined = it.joined + names) } ?: c }
            }
        }
    }

    /** The Share sheet opened or closed: turns the band search on or off. */
    fun bandSearch(on: Boolean) = bandLink.setScreenOpen(on)

    /** Sends [pkg] to everyone in the band now: nearby ones (Wi-Fi) right away, the others in each pair's inbox. */
    fun sendBand(pkg: () -> Pair<ByteArray, List<SetlistCloud.SharedSong>>?) {
        scope.launch {
            val (list, songs) = pkg() ?: return@launch
            val lan = bandLink.send(list, songs)
            val net = if (connectivity.isOnline()) bandInternet.sendToBand(list, songs, lan) else 0
            bandSent.send(lan.size + net)
        }
    }

    companion object {
        const val JOIN_EVERY_MS = 15_000L
        const val JOIN_CHECKS = 40   // 10 min at most
    }
}
