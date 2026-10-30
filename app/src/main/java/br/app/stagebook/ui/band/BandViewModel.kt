package br.app.stagebook.ui.band

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.BandLink
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
)

/** What the band screen does. */
class BandActions(
    val onName: (String) -> Unit = {},
    val onJoin: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
)

@HiltViewModel
class BandViewModel @Inject constructor(
    private val link: BandLink,
    private val trust: BandTrust,
    private val identity: DeviceIdentity,
) : ViewModel() {
    val ui: StateFlow<BandUi> = combine(
        combine(link.peers, link.joins, trust.members, ::Triple),
        identity.name, link.wifi, link.running,
    ) { (peers, joins, members), name, wifi, running ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BandUi())

    fun actions() = BandActions(
        onName = identity::setName,
        onJoin = { id -> link.peers.value.firstOrNull { it.id == id }?.let(link::requestJoin) },
        onRemove = link::remove,
    )

    /** Settings → Band open: this device shows up for whoever wants to join. */
    fun screenOpen(open: Boolean) = link.setScreenOpen(open)
}
