package com.juliano.stagebook.ui.band

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.data.BandLink
import com.juliano.stagebook.data.BandTrust
import com.juliano.stagebook.data.DeviceIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Um aparelho na rede agora: se já é da banda e, se este aparelho pediu para entrar por ele, como está o pedido. */
@Immutable
data class NearbyUi(val id: String, val name: String, val member: Boolean, val join: BandLink.Join? = null, val otherVersion: String? = null)

@Immutable
data class MemberUi(val id: String, val name: String, val since: Long)

/** Ajustes → Banda: este aparelho, quem está na rede agora e quem é da banda. */
@Immutable
data class BandUi(
    val name: String = "",
    val nameConfirmed: Boolean = true,
    val wifi: Boolean = false,
    val searching: Boolean = false,
    val nearby: List<NearbyUi> = emptyList(),
    val members: List<MemberUi> = emptyList(),
)

/** O que a tela da banda faz. */
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
                val other = if (it.v != com.juliano.stagebook.domain.BandProtocol.VERSION) it.app.ifEmpty { "?" } else null
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

    /** Ajustes → Banda aberto: este aparelho aparece para quem quer entrar. */
    fun screenOpen(open: Boolean) = link.setScreenOpen(open)
}
