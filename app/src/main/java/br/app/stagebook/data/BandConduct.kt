package br.app.stagebook.data

import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.Immutable
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.stateIn
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

        /**
         * [songId]: the current song here (null = missing here); [at]: last news from the leader (device clock);
         * [breakSince]: the leader is on the break after that song since then, on this device's clock (null = no break).
         */
        @Immutable
        data class Following(
            val fromId: String, val msg: Conduct.Msg, val songId: String?, val at: Long = System.currentTimeMillis(),
            val breakSince: Long? = null,
        ) : Role {
            /** No news for more than [GONE_MS]: the leader is gone (battery, app closed, off the Wi-Fi). */
            fun gone(now: Long = System.currentTimeMillis()) = now - at > GONE_MS
        }
    }

    private val _role = MutableStateFlow<Role>(Role.None)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _openSong = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Follower: the song to open on stage now. */
    val openSong: SharedFlow<String> = _openSong

    /** Follower: the leader's current song here (null = not following, or missing here). */
    val followingSong: StateFlow<String?> by lazy {   // lazy: [scope] is declared further down
        _role.map { (it as? Role.Following)?.songId }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    }

    @Volatile private var leftFrom: String? = null
    private val scope = backgroundScope(Dispatchers.IO)   // errors logged, never a crash on stage
    private var searchedAt = -1   // the song for which a delivery failure already started the search (once per song)

    /** This device's clock; the tests' own, so a break's time is checked without waiting. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** How a message reaches the band (the tests look at what goes out). */
    internal var deliver: suspend (Conduct.Msg) -> Map<String, Conduct.Lack> = { link.conduct(it) }

    // leading: the break on this stage, when it started (null = none). Kept apart from the message: each one that
    // leaves, the keep-alive and a resend to who just arrived too, says how long the break has lasted by then
    @Volatile private var breakStart: Long? = null
    private val moves = java.util.concurrent.atomic.AtomicInteger()   // song changes and breaks, to tell a newer one came

    private fun stamped(msg: Conduct.Msg): Conduct.Msg {
        val start = breakStart?.takeIf { msg.type == Conduct.OPEN } ?: return msg
        return msg.copy(breakMs = (clock() - start).coerceIn(0L, Conduct.MAX_BREAK_MS))
    }

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
        breakStart = null
        val msg = Conduct.Msg(Conduct.CHECK, from = identity.name.value, set = set, songs = songs, index = index)
        _role.value = Role.Conducting(setId, msg)
        link.burst(evenOnStage = true)
        scope.launch { send(setId, msg) }
    }

    /** The leader changed songs on the stage of setlist [setId]. */
    fun open(setId: String, index: Int) {
        val now = _role.value as? Role.Conducting ?: return
        if (now.setId != setId || (now.msg.index == index && now.msg.type == Conduct.OPEN)) return
        breakStart = null   // another song on stage: the break, if there was one, is over
        moves.incrementAndGet()
        scope.launch { send(setId, now.msg.copy(type = Conduct.OPEN, index = index)) }
    }

    /**
     * The leader's stage of setlist [setId] is on the break after the song on stage, for [elapsedMs] now, or left it
     * (null): the followers' stages show the break, or go back to the song. Nothing happens when not leading.
     */
    fun rest(setId: String, elapsedMs: Long?) {
        val now = _role.value as? Role.Conducting ?: return
        if (now.setId != setId) return
        val start = elapsedMs?.let { clock() - it.coerceAtLeast(0L) }
        if ((start == null) == (breakStart == null)) return
        breakStart = start
        val move = moves.incrementAndGet()
        scope.launch {
            // leaving forward, the next song's message follows at once and already says there's no break; sent now,
            // this one could arrive after it and take the band back a song. It only goes if nothing else did
            if (start == null) {
                kotlinx.coroutines.delay(LEFT_BREAK_MS)
                if (moves.get() != move) return@launch
            }
            val still = _role.value as? Role.Conducting ?: return@launch
            send(still.setId, still.msg.copy(type = Conduct.OPEN))
        }
    }

    /**
     * "Look for the band": 30 s of search on stage; whoever shows up gets the current song. Also sends the current song
     * to everyone now and twice more: a member who reopened the app still looked online to the leader, so the "came
     * back" resend never fired and a late follower never joined (device test, 02/11).
     */
    fun search() {
        link.burst(evenOnStage = true)
        scope.launch {
            repeat(SEARCH_SENDS) { i ->
                if (i > 0) kotlinx.coroutines.delay(SEARCH_EVERY_MS)
                val now = _role.value as? Role.Conducting ?: return@launch
                send(now.setId, now.msg.copy(type = Conduct.OPEN))
            }
        }
    }

    fun end() {
        val now = (_role.value as? Role.Conducting)?.msg ?: return
        _role.value = Role.None
        breakStart = null
        scope.launch { deliver(now.copy(type = Conduct.END, missing = emptyList())) }
    }

    /** Follower: stops following (comes back if the leader starts again with [start]). */
    fun leave() {
        leftFrom = (_role.value as? Role.Following)?.fromId
        _role.value = Role.None
    }

    // sends overlap (song change, keep-alive, someone back on the Wi-Fi) and one can take 20 s on a weak Wi-Fi: only
    // the newest writes its answers back and resends
    private val newest = br.app.stagebook.domain.Newest()

    /** Leading goes on with [now]; ended meanwhile, it stays ended: checking and then writing let a send bring it back. */
    private fun keepLeading(now: Role.Conducting): Boolean = _role.updateAndGet { if (it is Role.Conducting) now else it } is Role.Conducting

    private suspend fun send(setId: String, msg: Conduct.Msg) {
        val members = link.onlineMembers.value.size
        if (_role.value !is Role.Conducting) return   // ended before sending: doesn't turn back on
        val ticket = newest.ticket()
        if (!keepLeading(Role.Conducting(setId, msg))) return
        val replies = deliver(stamped(msg))
        if (_role.value !is Role.Conducting) return   // ended while sending
        if (!newest.isNewest(ticket)) return   // a newer song went out meanwhile: this late answer would bring back the old one
        val withLacks = msg.copy(missing = Conduct.lacks(replies.values))
        val noAnswer = (members - replies.size).coerceAtLeast(0)
        if (!keepLeading(Role.Conducting(setId, withLacks, noAnswer))) return
        // the missing songs changed: send again so everyone sees the same alert
        if (withLacks.missing != msg.missing && newest.isNewest(ticket)) deliver(stamped(withLacks))
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
        _role.value = Role.Following(from.id, msg, song?.id, breakSince = breakSince(from.id, msg, before))
        if (song != null && (before?.songId != song.id || before.msg.index != msg.index)) _openSong.tryEmit(song.id)
        return Conduct.lackFor(identity.name.value, msg, library)
    }

    // the leader says how long its break has lasted; here that becomes when it started on this device's clock. The same
    // break told again (keep-alive, a resend) keeps the earliest start: the message's trip only ever makes it look later
    private fun breakSince(fromId: String, msg: Conduct.Msg, before: Role.Following?): Long? {
        val since = clock() - (msg.rest ?: return null)
        val known = before?.takeIf { it.fromId == fromId && it.msg.index == msg.index }?.breakSince ?: return since
        return if (kotlin.math.abs(since - known) <= SAME_BREAK_MS) minOf(since, known) else since
    }

    companion object {
        const val LEFT_BREAK_MS = 300L
        const val SAME_BREAK_MS = 2_000L
        const val ALIVE_MS = 3 * 60_000L
        const val SEARCH_SENDS = 3
        const val SEARCH_EVERY_MS = 5_000L
        const val GONE_MS = 10 * 60_000L
    }
}
