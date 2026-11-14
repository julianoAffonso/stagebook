package br.app.stagebook.data

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
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
 *
 * Missing songs during the show: the leader sends each follower the ones it lacks ([sendLacking]) and a follower asks
 * the band for the one on the strip ([ask]), both over this network only. Those, and only those, go straight into the
 * follower's library ([enter]); everything else the band sends keeps waiting in the inbox.
 */
@Singleton
class BandConduct @Inject constructor(
    private val link: BandLink,
    private val repository: LibraryRepository,
    private val identity: DeviceIdentity,
) {
    sealed interface Role {
        data object None : Role

        /**
         * [noAnswer]: band members online who didn't answer (another version, outside the app). [lackers]: the devices
         * following that lack songs, by id (the message only carries their names).
         */
        @Immutable
        data class Conducting(val setId: String, val msg: Conduct.Msg, val noAnswer: Int = 0, val lackers: Map<String, Conduct.Lack> = emptyMap()) : Role

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

            /** The song that matters now, the one the strip is about: the leader's, or on its break the one after it. */
            val item: Conduct.Item? get() = breakSince?.let { msg.songs.getOrNull(msg.index + 1) } ?: msg.songs.getOrNull(msg.index)
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
        link.showIntake = ::intake
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

    // a message leaving: who lacks what stays as known until the answers say otherwise
    private fun keepLeading(setId: String, msg: Conduct.Msg): Boolean =
        _role.updateAndGet { if (it is Role.Conducting) Role.Conducting(setId, msg, lackers = if (it.setId == setId) it.lackers else emptyMap()) else it } is Role.Conducting

    private suspend fun send(setId: String, msg: Conduct.Msg) {
        val members = link.onlineMembers.value.size
        if (_role.value !is Role.Conducting) return   // ended before sending: doesn't turn back on
        val ticket = newest.ticket()
        if (!keepLeading(setId, msg)) return
        val replies = deliver(stamped(msg))
        if (_role.value !is Role.Conducting) return   // ended while sending
        if (!newest.isNewest(ticket)) return   // a newer song went out meanwhile: this late answer would bring back the old one
        val withLacks = msg.copy(missing = Conduct.lacks(replies.values))
        val noAnswer = (members - replies.size).coerceAtLeast(0)
        if (!keepLeading(Role.Conducting(setId, withLacks, noAnswer, replies.filterValues { it.titles.isNotEmpty() }))) return
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

    // ---- missing songs during the show ----

    /** What the strip tells in a short message ([notices]). */
    sealed interface Notice {
        data class Sent(val to: String) : Notice
        data class SentMany(val devices: Int) : Notice
        data object NotSent : Notice
        data class Arrived(val title: String, val from: String) : Notice
        data class NoChords(val title: String) : Notice
        /** Nobody on this network has the song asked for; [chords]: a copy with chords was what was asked. */
        data class Nobody(val chords: Boolean) : Notice
        /** The copy with chords asked for came, and waits in the inbox: a song the musician touched is never replaced by itself. */
        data class ChordsKept(val title: String, val from: String) : Notice
        /** The copy with chords asked for took the place of the one that had just come without them. */
        data class ChordsCame(val title: String, val from: String) : Notice
    }

    // a notice waits for the screen when nobody is collecting at that instant (the screen being redrawn: the device
    // turned, the text size changed): a shared flow dropped it, and "arrived without chords" never showed (two devices,
    // 13/11). One that waits too long isn't news any more and is dropped
    private val pendingNotices = kotlinx.coroutines.channels.Channel<Pair<Long, Notice>>(8, BufferOverflow.DROP_OLDEST)
    val notices: kotlinx.coroutines.flow.Flow<Notice> = pendingNotices.receiveAsFlow()
        .mapNotNull { (at, notice) -> notice.takeIf { clock() - at <= NOTICE_MS } }

    private fun notify(notice: Notice) { pendingNotices.trySend(clock() to notice) }

    private val _sending = MutableStateFlow(false)
    /** Leading: the missing songs are going out. */
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _asking = MutableStateFlow(false)
    /** Following: the band is being asked for the strip's song. */
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    // songs that went straight in without chords on a profile that shows them, by id: the strip says so and offers to
    // ask for a copy with chords. Until the app closes
    private val _chordless = MutableStateFlow<Set<String>>(emptySet())
    val chordless: StateFlow<Set<String>> = _chordless.asStateFlow()
    // the text each of them came in with: only a copy nobody touched since may give way to one with chords
    private val chordlessText = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** How songs reach one member on this network, and how the band is asked (the tests look at what goes out). */
    internal var sendSongs: suspend (String, List<br.app.stagebook.data.model.Song>) -> Boolean = { id, songs -> link.sendSongsTo(id, songs) }
    internal var request: suspend (List<BandLink.Wanted>) -> List<BandLink.Answer> = { link.askBand(it) }

    private fun bandOn() = repository.library.value.look.bandOn

    /**
     * Leading: sends each follower in [memberIds] (null = every one that lacks something) the setlist songs it lacks,
     * to its device on this network and nowhere else. Then everyone says again what's missing, so who got them leaves
     * the list.
     */
    fun sendLacking(memberIds: Set<String>? = null) {
        if (!bandOn()) return
        val now = _role.value as? Role.Conducting ?: return
        val targets = now.lackers.filterKeys { memberIds == null || it in memberIds }
        if (targets.isEmpty() || !_sending.compareAndSet(false, true)) return
        scope.launch {
            try {
                val library = repository.library.value.songs
                val reached = targets.filter { (id, lack) ->
                    // a song that isn't here either is left out
                    val songs = now.msg.songs.filter { it.title in lack.titles }.mapNotNull { Conduct.find(it, library) }.distinctBy { it.id }
                    songs.isNotEmpty() && sendSongs(id, songs)
                }
                notify(
                    when {
                        reached.isEmpty() -> Notice.NotSent
                        memberIds?.size == 1 -> Notice.Sent(reached.values.first().who)
                        else -> Notice.SentMany(reached.size)
                    },
                )
                (_role.value as? Role.Conducting)?.takeIf { reached.isNotEmpty() }?.let { send(it.setId, it.msg.copy(type = Conduct.OPEN)) }
            } finally {
                _sending.value = false
            }
        }
    }

    /**
     * Following: asks the band on this network for the strip's song, when it isn't here. When it came without chords
     * ([chordless]) only a copy with chords is taken, so the same one never comes again.
     */
    fun ask() {
        if (!bandOn()) return
        val item = (_role.value as? Role.Following)?.item ?: return
        val library = repository.library.value
        val own = Conduct.find(item, library.songs)
        val forChords = own != null
        if (own != null && !(own.id in _chordless.value && library.look.chords && !br.app.stagebook.domain.Chords.hasChords(own.text))) return
        if (!_asking.compareAndSet(false, true)) return
        scope.launch {
            try {
                // several copies can answer (one per member, and per spelling of the artist): one with chords first
                val got = request(listOf(BandLink.Wanted(item.title, item.artist)))
                    .filter { it.song.allowed() && Conduct.isFor(item, it.song.title, it.song.author) }
                    .filter { !forChords || br.app.stagebook.domain.Chords.hasChords(it.song.text) }
                    .maxByOrNull { if (br.app.stagebook.domain.Chords.hasChords(it.song.text)) 1 else 0 }
                when {
                    got == null -> notify(Notice.Nobody(forChords))
                    own == null && enter(got.song, got.fromName) -> Unit
                    // the copy that had just come without chords, untouched since: the one with chords takes its place
                    // (user, 13/11: a guitarist shouldn't have to leave the stage to accept it)
                    own != null && replaceChordless(own, got.song) -> notify(Notice.ChordsCame(got.song.title, got.fromName))
                    else -> {
                        // a song with this title and artist is here (or following ended meanwhile): reviewed like the rest
                        link.keep(got.fromId, got.fromName, listOf(got.song))
                        if (own != null) {
                            _chordless.update { it - own.id }
                            chordlessText.remove(own.id)
                            notify(Notice.ChordsKept(got.song.title, got.fromName))
                        }
                    }
                }
            } finally {
                _asking.value = false
            }
        }
    }

    // a package from the band arrived (another thread): from the leader followed now, loose songs of its setlist go
    // straight in; returns what is left for the inbox
    private fun intake(fromId: String, fromName: String, text: String, songs: List<SetlistCloud.SharedSong>): List<SetlistCloud.SharedSong> {
        val following = _role.value as? Role.Following ?: return songs
        if (following.fromId != fromId || !br.app.stagebook.domain.BandContent.listOk(text)) return songs
        // a setlist is always reviewed: the leader sends the missing songs loose
        if (br.app.stagebook.domain.QrPayload.parse(text)?.name?.isEmpty() != true) return songs
        return songs.filterNot { s -> following.msg.songs.any { Conduct.isFor(it, s.title, s.author) } && enter(s, fromName) }
    }

    private val entering = Any()

    /**
     * The copy with chords takes the place of the one that came in without them through this device's own request:
     * only while following, only if that copy is still exactly as it came (anything the musician changed is theirs).
     */
    private fun replaceChordless(own: br.app.stagebook.data.model.Song, song: SetlistCloud.SharedSong): Boolean {
        if (!bandOn() || !song.allowed() || !br.app.stagebook.domain.Chords.hasChords(song.text)) return false
        synchronized(entering) {
            if (_role.value !is Role.Following) return false
            val now = repository.song(own.id) ?: return false
            if (chordlessText[own.id] != now.text) return false
            repository.saveSong(now.copy(text = br.app.stagebook.domain.Chords.withDetectedKey(song.text)))
            chordlessText.remove(own.id)
            _chordless.update { it - own.id }
        }
        return true
    }

    /**
     * A song the band sent goes straight into the library, without the review: only while following, only one that
     * may travel, and never when a song with that title and artist is here (the musician's own is not replaced). The
     * callers let in only what this device asked for, or what the leader sent of its setlist. False = not let in.
     */
    private fun enter(song: SetlistCloud.SharedSong, fromName: String): Boolean {
        if (!bandOn() || !song.allowed()) return false
        val noChords: Boolean
        synchronized(entering) {   // two arrivals of the same song: the second one finds the first
            if (_role.value !is Role.Following || repository.findSong(song.title, song.author) != null) return false
            val id = repository.saveSong(
                br.app.stagebook.data.model.Song(title = song.title, author = song.author, year = song.year, text = br.app.stagebook.domain.Chords.withDetectedKey(song.text)),
            )
            noChords = repository.library.value.look.chords && !br.app.stagebook.domain.Chords.hasChords(song.text)
            if (noChords) {
                chordlessText[id] = repository.song(id)?.text.orEmpty()
                _chordless.update { it + id }
            }
        }
        val current = (_role.value as? Role.Following)?.item?.let { Conduct.isFor(it, song.title, song.author) } == true
        songArrived()
        if (noChords) notify(Notice.NoChords(song.title)) else if (!current) notify(Notice.Arrived(song.title, fromName))
        return true
    }

    // the library changed under the follower: the leader's song may be here now, and the stage opens on it
    private fun songArrived() {
        while (true) {
            val before = _role.value as? Role.Following ?: return
            val id = songHere(before.msg)?.id
            if (id == before.songId) return
            val now = before.copy(songId = id)
            if (!_role.compareAndSet(before, now)) continue
            now.stage?.takeIf { it != before.stage }?.let(_openSong::tryEmit)
            return
        }
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
        const val NOTICE_MS = 30_000L   // a notice not shown by then is dropped
    }
}
