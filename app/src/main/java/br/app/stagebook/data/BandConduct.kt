package br.app.stagebook.data

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.Immutable
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Lead the band" / "follow" (over the local network, [BandLink.conduct]). The leader starts with the setlist ([start]:
 * each one says what's missing), changes songs ([open]) and ends ([end]); everyone's missing songs go in every message,
 * so the whole band sees the alert. A follower turns nothing on: the first message already makes them follow and the
 * song opens by itself ([openSong]); [leave] stops following until the leader starts again.
 */
@Singleton
class BandConduct @Inject constructor(
    private val link: BandLink,
    private val repository: LibraryRepository,
    private val identity: DeviceIdentity,
) {
    sealed interface Role {
        data object None : Role

        /** [noAnswer]: band members online who didn't answer (another version, outside the app). */
        @Immutable
        data class Conducting(val setId: String, val msg: Conduct.Msg, val noAnswer: Int = 0) : Role

        /** [songId]: the current song here (null = missing here); [at]: last news from the leader (device clock). */
        @Immutable
        data class Following(val fromId: String, val msg: Conduct.Msg, val songId: String?, val at: Long = System.currentTimeMillis()) : Role {
            /** No news for more than [GONE_MS]: the leader is gone (battery, app closed, off the Wi-Fi). */
            fun gone(now: Long = System.currentTimeMillis()) = now - at > GONE_MS
        }
    }

    private val _role = MutableStateFlow<Role>(Role.None)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _openSong = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Follower: the song to open on stage now. */
    val openSong: SharedFlow<String> = _openSong

    @Volatile private var leftFrom: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var searchedAt = -1   // the song for which a delivery failure already started the search (once per song)

    init {
        link.conductHandler = ::onMessage
        // essential mode (T19): "Band on the network" off ends Lead the band (leading or following) and the strip goes
        // away
        scope.launch {
            repository.library.map { it.look.bandOn }.distinctUntilChanged().collect { on ->
                if (!on) when (_role.value) { is Role.Conducting -> end(); is Role.Following -> leave(); Role.None -> Unit }
            }
        }
        // leading: a sign of life every 3 min (the current song again; followers don't change songs because of it)
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(ALIVE_MS)
                val now = _role.value as? Role.Conducting ?: continue
                send(now.setId, now.msg.copy(type = Conduct.OPEN))
            }
        }
        // leading: a band member came back to the Wi-Fi (found in a short search) → gets the current song, no tap
        // needed
        scope.launch {
            var before = emptySet<String>()
            link.onlineMembers.collect { peers ->
                val ids = peers.map { it.id }.toSet()
                val conducting = _role.value as? Role.Conducting
                if (conducting != null && (ids - before).isNotEmpty()) send(conducting.setId, conducting.msg.copy(type = Conduct.OPEN))
                before = ids
            }
        }
    }

    /**
     * Starts with the setlist: asks everyone what's missing (the alert shows before the first song) and searches for
     * the band.
     */
    fun start(setId: String, set: String, songs: List<Conduct.Item>, index: Int = 0) {
        searchedAt = -1
        _role.value = Role.Conducting(setId, Conduct.Msg(Conduct.CHECK, from = identity.name.value, set = set, songs = songs, index = index))
        link.burst(evenOnStage = true)
        scope.launch { send(setId, (_role.value as Role.Conducting).msg) }
    }

    /** The leader changed songs on the stage of setlist [setId]. */
    fun open(setId: String, index: Int) {
        val now = _role.value as? Role.Conducting ?: return
        if (now.setId != setId || (now.msg.index == index && now.msg.type == Conduct.OPEN)) return
        scope.launch { send(setId, now.msg.copy(type = Conduct.OPEN, index = index)) }
    }

    /** "Look for the band": 30 s of search on stage; whoever shows up gets the current song. */
    fun search() = link.burst(evenOnStage = true)

    fun end() {
        val now = (_role.value as? Role.Conducting)?.msg ?: return
        _role.value = Role.None
        scope.launch { link.conduct(now.copy(type = Conduct.END, missing = emptyList())) }
    }

    /** Follower: stops following (comes back if the leader starts again with [start]). */
    fun leave() {
        leftFrom = (_role.value as? Role.Following)?.fromId
        _role.value = Role.None
    }

    private suspend fun send(setId: String, msg: Conduct.Msg) {
        val members = link.onlineMembers.value.size
        if (_role.value !is Role.Conducting) return   // ended before sending: doesn't turn back on
        _role.value = Role.Conducting(setId, msg)
        val replies = link.conduct(msg)
        if (_role.value !is Role.Conducting) return   // encerrado enquanto mandava
        val withLacks = msg.copy(missing = Conduct.lacks(replies.values))
        val noAnswer = (members - replies.size).coerceAtLeast(0)
        _role.value = Role.Conducting(setId, withLacks, noAnswer)
        // the missing songs changed: send again so everyone sees the same alert
        if (withLacks.missing != msg.missing) link.conduct(withLacks)
        // someone didn't get it: one short search per song (they may have changed address on the Wi-Fi)
        if (noAnswer > 0 && searchedAt != msg.index) { searchedAt = msg.index; link.burst(evenOnStage = true) }
    }

    // follower: arrives over the network (another thread); answers what's missing here
    private fun onMessage(from: BandProtocol.Hello, msg: Conduct.Msg): Conduct.Lack? {
        if (msg.type == Conduct.END) {
            if ((_role.value as? Role.Following)?.fromId == from.id) _role.value = Role.None
            leftFrom = null
            return Conduct.Lack(identity.name.value, emptyList())
        }
        if (msg.type == Conduct.CHECK) leftFrom = null
        if (leftFrom == from.id || _role.value is Role.Conducting) return null
        val library = repository.library.value.songs
        val song = msg.songs.getOrNull(msg.index)?.let { Conduct.find(it, library) }
        val before = _role.value as? Role.Following
        _role.value = Role.Following(from.id, msg, song?.id)
        if (song != null && (before?.songId != song.id || before.msg.index != msg.index)) _openSong.tryEmit(song.id)
        return Conduct.lackFor(identity.name.value, msg, library)
    }

    companion object {
        const val ALIVE_MS = 3 * 60_000L
        const val GONE_MS = 10 * 60_000L
    }
}
