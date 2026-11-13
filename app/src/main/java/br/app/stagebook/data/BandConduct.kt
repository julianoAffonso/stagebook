package br.app.stagebook.data

import kotlinx.coroutines.flow.update
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
 * song opens by itself ([openSong]); [leave] stops following, and while that leader goes on leading the strip offers
 * to come back ([Role.Left], [follow]); the leader starting again, or calling the band ([search]), brings them back too.
 * The leader's break opens the stage too, for a follower who doesn't have the song before it ([BREAK_ONLY]).
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

            /**
             * What this device's stage shows for it: the leader's song here, or the leader's break alone when that song
             * isn't here ([BREAK_ONLY]); null = nothing to show (the song is missing and there's no break).
             */
            val stage: String? get() = songId ?: BREAK_ONLY.takeIf { breakSince != null }
        }

        /**
         * Left the leader [fromId], who goes on leading: nothing opens by itself here, and the strip offers to follow
         * again. [msg], [at] and [breakSince] are kept up to date as in [Following], so coming back lands where the
         * leader is now.
         */
        @Immutable
        data class Left(val fromId: String, val msg: Conduct.Msg, val at: Long = System.currentTimeMillis(), val breakSince: Long? = null) : Role {
            /** No news for more than [GONE_MS]: nobody to follow again. */
            fun gone(now: Long = System.currentTimeMillis()) = now - at > GONE_MS
        }
    }

    private val _role = MutableStateFlow<Role>(Role.None)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _openSong = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Follower: the song to open on stage now, or [BREAK_ONLY]. */
    val openSong: SharedFlow<String> = _openSong

    /**
     * Follower: what the stage shows for the leader now, the song here or [BREAK_ONLY] (null = not following, or the
     * song is missing here and the leader isn't on a break).
     */
    val followingSong: StateFlow<String?> by lazy {   // lazy: [scope] is declared further down
        _role.map { (it as? Role.Following)?.stage }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    }

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
                if (!on) when (_role.value) { is Role.Conducting -> end(); is Role.Following -> leave(); is Role.Left, Role.None -> Unit }
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
     *
     * Each time the setlist goes first, as when starting ([Conduct.CHECK]): that is the call that brings back a
     * follower who left, which the current song alone never does (two devices, 13/11: three searches and the phone that
     * had left stayed out). The current song follows at once, with the break if there is one.
     */
    fun search() {
        link.burst(evenOnStage = true)
        scope.launch {
            repeat(SEARCH_SENDS) { i ->
                if (i > 0) kotlinx.coroutines.delay(SEARCH_EVERY_MS)
                val now = _role.value as? Role.Conducting ?: return@launch
                deliver(now.msg.copy(type = Conduct.CHECK))
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

    /**
     * Follower: stops following. Nothing the leader does on its stage opens here any more; [follow], the leader
     * starting again or calling the band brings it back.
     */
    fun leave() {
        _role.update { if (it is Role.Following) Role.Left(it.fromId, it.msg, it.at, it.breakSince) else it }
    }

    /** Who left follows again: the leader's current song opens, or its break, as on a device that has just arrived. */
    fun follow() {
        if (!repository.library.value.look.bandOn) return
        while (true) {
            val left = _role.value as? Role.Left ?: return
            val now = Role.Following(left.fromId, left.msg, songHere(left.msg)?.id, left.at, left.breakSince)
            if (!_role.compareAndSet(left, now)) continue   // news from the leader meanwhile: follow that
            now.stage?.let(_openSong::tryEmit)
            return
        }
    }

    private fun songHere(msg: Conduct.Msg) = msg.songs.getOrNull(msg.index)?.let { Conduct.find(it, repository.library.value.songs) }

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
        val before = _role.value
        if (msg.type == Conduct.END) {
            if ((before as? Role.Following)?.fromId == from.id || (before as? Role.Left)?.fromId == from.id) _role.value = Role.None
            return Conduct.Lack(identity.name.value, emptyList())
        }
        if (before is Role.Conducting) return null
        // left this leader: what it says only keeps "follow again" up to date. The setlist sent again is the call back
        if (before is Role.Left && before.fromId == from.id && msg.type != Conduct.CHECK) {
            _role.compareAndSet(before, Role.Left(from.id, msg, breakSince = breakSince(msg, before.breakSince.takeIf { before.msg.index == msg.index })))
            return null
        }
        val song = songHere(msg)
        val known = when (before) {
            is Role.Following -> before.takeIf { it.fromId == from.id && it.msg.index == msg.index }?.let { it.msg to it.breakSince }
            is Role.Left -> before.takeIf { it.fromId == from.id && it.msg.index == msg.index }?.let { it.msg to it.breakSince }
            else -> null
        }
        // the call says nothing about the break: the one already known stays until the song that follows it says
        val since = if (msg.type == Conduct.CHECK && known?.first?.songs == msg.songs) known.second else breakSince(msg, known?.second)
        val now = Role.Following(from.id, msg, song?.id, breakSince = since)
        _role.value = now
        // a song, or a break after a song that isn't here: the stage opens once for it, not again on each sign of life
        val was = before as? Role.Following
        val stage = now.stage
        if (stage != null && (was?.stage != stage || was.msg.index != msg.index)) _openSong.tryEmit(stage)
        return Conduct.lackFor(identity.name.value, msg, repository.library.value.songs)
    }

    // the leader says how long its break has lasted; here that becomes when it started on this device's clock. The same
    // break told again (keep-alive, a resend) keeps the earliest start ([known]): the message's trip only ever makes it
    // look later
    private fun breakSince(msg: Conduct.Msg, known: Long?): Long? {
        val since = clock() - (msg.rest ?: return null)
        if (known == null) return since
        return if (kotlin.math.abs(since - known) <= SAME_BREAK_MS) minOf(since, known) else since
    }

    companion object {
        /**
         * In place of a song's id: the leader is on a break and the song before it isn't on this device, so the stage
         * opens on the break alone (a follower without that song saw no break, user 13/11). Never a song's id.
         */
        const val BREAK_ONLY = ""
        const val LEFT_BREAK_MS = 300L
        const val SAME_BREAK_MS = 2_000L
        const val ALIVE_MS = 3 * 60_000L
        const val SEARCH_SENDS = 3
        const val SEARCH_EVERY_MS = 5_000L
        const val GONE_MS = 10 * 60_000L
    }
}
