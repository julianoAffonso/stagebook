package br.app.stagebook.ui.band

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.SetlistCloud
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.DeviceIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * A device on the network now: whether it's already in the band and, if this device asked to join through it, the
 * request's state.
 */
@Immutable
data class NearbyUi(val id: String, val name: String, val member: Boolean, val join: BandLink.Join? = null, val otherVersion: String? = null)

@Immutable
data class MemberUi(val id: String, val name: String, val since: Long)

/** Settings → Band: this device, who is on the network now and who is in the band. */
@Immutable
data class BandUi(
    val name: String = "",
    val nameConfirmed: Boolean = true,
    val wifi: Boolean = false,
    val searching: Boolean = false,
    val nearby: List<NearbyUi> = emptyList(),
    val members: List<MemberUi> = emptyList(),
    val inviting: Boolean = false,   // creating an invite over the internet
)

/** What the band screen does. */
class BandActions(
    val onName: (String) -> Unit = {},
    val onJoin: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onInvite: () -> Unit = {},
)

@HiltViewModel
class BandViewModel @Inject constructor(
    private val link: BandLink,
    private val trust: BandTrust,
    private val identity: DeviceIdentity,
    private val cloud: SetlistCloud,
    private val bandInternet: BandInternet,
) : ViewModel() {
    private val inviting = MutableStateFlow(false)
    private val invites = Channel<String?>(Channel.BUFFERED)

    /** Each invite created: its code, to share, or null if it couldn't go (no internet, service down). */
    val inviteResults: Flow<String?> = invites.receiveAsFlow()

    val ui: StateFlow<BandUi> = combine(
        combine(link.peers, link.joins, trust.members, ::Triple),
        identity.name, link.wifi, link.running, inviting,
    ) { (peers, joins, members), name, wifi, running, sending ->
        val ids = members.map { it.id }.toSet()
        BandUi(
            name = name,
            nameConfirmed = identity.nameConfirmed,
            wifi = wifi,
            searching = running,
            nearby = peers.map {
                val other = if (it.v != br.app.stagebook.domain.BandProtocol.VERSION) it.app.ifEmpty { "?" } else null
                NearbyUi(it.id, it.name, it.id in ids, joins[it.id], other)
            },
            members = members.map { MemberUi(it.id, it.name, it.since) },
            inviting = sending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BandUi())

    fun actions() = BandActions(
        onName = identity::setName,
        onJoin = { id -> link.peers.value.firstOrNull { it.id == id }?.let(link::requestJoin) },
        onRemove = link::remove,
        onInvite = ::invite,
    )

    /**
     * An invite over the internet (user, 07/11): any band member can send it. Its code is remembered like any send
     * through the 3 words, so the first one who accepts joins the band on the next sync (single use).
     */
    fun invite() {
        if (!inviting.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val code = runCatching { cloud.sendInvite() }.getOrNull()
            code?.let { bandInternet.remember(it, invite = true) }
            inviting.value = false
            invites.send(code)
        }
    }

    /** Settings → Band open: this device shows up for whoever wants to join. */
    fun screenOpen(open: Boolean) = link.setScreenOpen(open)
}
