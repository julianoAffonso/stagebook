package br.app.stagebook.ui.setlists

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandInternet
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.QrMatch
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SetlistMatcher
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.SongMatch
import br.app.stagebook.domain.SongMerge
import br.app.stagebook.domain.TextKey
import br.app.stagebook.navigation.ScanSetlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A library song that can go into the scanned setlist. */
@Immutable
data class SongRef(val id: String, val title: String, val author: String)

/**
 * A review row: the setlist's song, what was found in the library and the chosen one (null = left out). [found] = found
 * without doubt; otherwise [candidates] are the similar ones for the user to pick. [carried] = not in the library, but
 * came with the setlist (online): goes into the library on create.
 */
@Immutable
data class ScanRow(
    val title: String,
    val artist: String,
    val found: Boolean,
    val candidates: List<SongRef>,
    val chosen: String?,
    val carried: Boolean = false,
    val fromBand: Boolean = false,   // the band had it (asked during the review)
    val differs: Boolean = false,    // I have it, but the one that arrived is another version: the user chooses
    val lyricsOnly: Boolean = false, // lyrics and chords profile, but the one that arrived has no chords
    val wantChords: Boolean = false, // refused the lyrics-only one: goes to find it with chords on the sites (import queue)
    val useIncoming: Boolean = false,
)

enum class ScanProblem { NOT_SETLIST, INCOMPLETE, NEWER }

enum class CodeProblem { NOT_FOUND, OFFLINE, NEWER }

sealed interface ScanUi {
    /** Camera open; [problem] = the last QR scanned didn't work. */
    data class Scanning(val problem: ScanProblem? = null) : ScanUi

    /** The code was an invite to the band (user, 07/11): who invites, to accept or not. */
    data class Invite(val name: String, val joined: Boolean = false) : ScanUi

    /** Fetching by the internet code, or what went wrong. */
    data class Fetching(val problem: CodeProblem? = null) : ScanUi

    /** [songsOnly]: loose songs came (no setlist name): they only go into the library, no setlist is created. */
    @Immutable
    data class Review(val name: String, val rows: List<ScanRow>, val songsOnly: Boolean = false) : ScanUi {
        /** The ones that will be left out: no choice and not carried along. */
        val missing: List<ScanRow> get() = rows.filter { it.chosen == null && !it.carried }
    }
}

/**
 * Reads the setlist (QR from another device, or the internet code: same format as the QR) and checks it against this
 * device's library before creating. Every song not found is flagged before creating: a 15-song setlist never silently
 * becomes 12. Songs carried along only fill in for missing ones (the ones here never change).
 */
@HiltViewModel
class ScanSetlistViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importQueue: ImportQueue,
    private val cloud: SetlistCloud,
    savedStateHandle: SavedStateHandle,
    private val inbox: BandInbox,
    private val bandLink: BandLink,
    private val bandInternet: BandInternet,
    private val trust: br.app.stagebook.data.BandTrust,
    private val connectivity: br.app.stagebook.data.Connectivity,
) : ViewModel() {
    // who sent through the 3 words: on accepting, they join the band (and get my "ok")
    private val senderNow = MutableStateFlow<br.app.stagebook.domain.BandProtocol.Hello?>(null)
    private var sender by senderNow::value
    // an invite read by the code: who invites (user, 07/11)
    private var invite: br.app.stagebook.domain.BandProtocol.Hello? = null

    /** Accepting the invite: who invited joins the band here, and my "ok" brings me into theirs. */
    fun acceptInvite() {
        val from = invite ?: return
        bandInternet.accepted(code, from)
        _ui.value = ScanUi.Invite(from.name, joined = true)
    }

    /** The sender, if they're in the band (the online request goes to their inbox). */
    private fun senderId(): String? = (inbox.get(route.inbox)?.fromId ?: sender?.id)?.takeIf(trust::isMember)

    /**
     * Sender's name, when the missing ones can be asked for online (in the band and there's internet); otherwise null.
     */
    // the sender arrives with the 3 words, after the screen is open: without them here the button never showed (test QR-11)
    val internetFrom: StateFlow<String?> = combine(connectivity.online, repository.library.map { it.look.internetOn }, senderNow) { a, b, _ -> a && b }.map { on ->
        if (!on) null else senderId()?.let { id -> trust.member(id)?.name ?: fromName.ifBlank { null } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val internetAsked = Channel<Boolean>(Channel.BUFFERED)
    /** Result of the online request (true = sent). */
    val internetResult = internetAsked.receiveAsFlow()

    /** Asks the sender online for the missing songs (T12); they arrive later in "Received from the band". */
    fun askInternet() {
        val ui = _ui.value as? ScanUi.Review ?: return
        val id = senderId() ?: return
        val missing = ui.rows.filter { r -> r.chosen == null && !r.carried }.map { BandLink.Wanted(it.title, it.artist) }
        if (missing.isEmpty()) return
        viewModelScope.launch { internetAsked.send(bandInternet.requestSongs(id, missing)) }
    }

    /** How many band members are on the network now ("Ask the band" only shows with someone and with missing songs). */
    val bandOnline: StateFlow<Int> = bandLink.onlineMembers.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _asking = MutableStateFlow(false)
    val asking: StateFlow<Boolean> = _asking

    private val bandAnswer = Channel<Pair<Int, Int>>(Channel.BUFFERED)
    /** (how many the band had, how many were asked for), for the notice. */
    val bandResult = bandAnswer.receiveAsFlow()

    /** The review with missing songs is open: search for the band (closed, it stops). */
    fun bandSearch(on: Boolean) = bandLink.setScreenOpen(on)

    /** Asks the band for the missing ones; the ones that arrive come in as "from the band", in their place. */
    fun askBand() {
        val ui = _ui.value as? ScanUi.Review ?: return
        if (_asking.value) return
        val missing = ui.rows.withIndex().filter { (_, r) -> r.chosen == null && !r.carried && !r.wantChords }
        if (missing.isEmpty()) return
        _asking.value = true
        viewModelScope.launch {
            val got = bandLink.askMissing(missing.map { (_, r) -> BandLink.Wanted(r.title, r.artist) })
            val now = _ui.value as? ScanUi.Review
            if (now != null) {
                val came = carried.toMutableMap()
                val rows = now.rows.mapIndexed { i, r ->
                    if (r.chosen != null || r.carried) return@mapIndexed r
                    val s = got.firstOrNull { it.isFor(r.title, r.artist) }
                    if (s == null) r else { came[i] = s; r.copy(carried = true, fromBand = true, lyricsOnly = noChords(s)) }
                }
                carried = came
                _ui.value = now.copy(rows = rows)
                bandAnswer.send(rows.count { it.fromBand } to missing.size)
            }
            _asking.value = false
        }
    }
    private val route = savedStateHandle.toRoute<ScanSetlist>()
    /** The code came in a link: the screen talks about the link, never about the 3 words. */
    val fromLink: Boolean = route.fromLink
    var code: String = route.code
        private set

    /** Came from the band (inbox): the sender, for the title; once reviewed, it leaves the inbox. */
    val fromName: String = inbox.get(route.inbox)?.fromName.orEmpty()
    private fun done() {
        if (route.inbox.isNotEmpty()) inbox.remove(route.inbox)
        if (code.isNotEmpty()) bandInternet.accepted(code, sender)
    }

    /** The camera reads a QR here: 3 words and what the band sent need none. */
    val readsQr: Boolean = code.isEmpty() && route.inbox.isEmpty()
    private val _ui = MutableStateFlow<ScanUi>(if (readsQr) ScanUi.Scanning() else ScanUi.Fetching())
    val ui: StateFlow<ScanUi> = _ui

    // songs carried along, per review row
    private var carried: Map<Int, SetlistCloud.SharedSong> = emptyMap()
    // another version of a song I already have, per row (only used if the user chooses it)
    private var incoming: Map<Int, SetlistCloud.SharedSong> = emptyMap()

    // the refused lyrics-only ones, per row (to undo "Accept lyrics only")
    private var declined: Map<Int, SetlistCloud.SharedSong> = emptyMap()

    /**
     * The one that came with lyrics only (lyrics and chords profile): [on] = don't accept and search with chords on the
     * sites (it stays "missing" and joins the "Import the missing ones" queue); false = accept the lyrics only again.
     */
    fun searchWithChords(index: Int, on: Boolean) {
        val ui = _ui.value as? ScanUi.Review ?: return
        val row = ui.rows.getOrNull(index) ?: return
        if (on && row.carried) {
            carried[index]?.let { declined = declined + (index to it) }
            carried = carried - index
        } else if (!on && row.wantChords) {
            declined[index]?.let { carried = carried + (index to it) }
        }
        _ui.value = ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(carried = !on, wantChords = on) else r })
    }

    /** What changes from mine to theirs, on row [index] (for "See what changes"). */
    fun changes(index: Int): List<SongMerge.Change> {
        val ui = _ui.value as? ScanUi.Review ?: return emptyList()
        val mine = ui.rows.getOrNull(index)?.chosen?.let(repository::song) ?: return emptyList()
        val theirs = incoming[index] ?: return emptyList()
        return SongMerge.changes(mine.text, theirs.text)
    }

    /** Keep mine (false) or use theirs (true) on row [index]. */
    fun useIncoming(index: Int, use: Boolean) = _ui.update { ui ->
        if (ui !is ScanUi.Review) ui else ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(useIncoming = use) else r })
    }

    // the chosen received ones go over mine, without deleting my chords
    private fun applyIncoming(ui: ScanUi.Review) {
        ui.rows.forEachIndexed { i, r ->
            val theirs = incoming[i] ?: return@forEachIndexed
            val id = r.chosen ?: return@forEachIndexed
            if (r.useIncoming) repository.updateSong(id) { it.copy(text = SongMerge.accept(it.text, theirs.text)) }
        }
    }

    init {
        when {
            route.inbox.isNotEmpty() -> inbox.get(route.inbox)?.let {
                if (it.newer) _ui.value = ScanUi.Fetching(CodeProblem.NEWER) else review(it.text, it.songs)
            } ?: run { _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND) }
            code.isNotEmpty() -> fetch()
        }
    }

    /** Fetches the setlist by the code (again, after an error). */
    fun fetch() {
        _ui.value = ScanUi.Fetching()
        viewModelScope.launch {
            when (val r = cloud.receive(code)) {
                is SetlistCloud.Received.Ok -> { sender = r.from; park(r.others, r.songs); review(r.text, r.songs) }
                SetlistCloud.Received.NotFound -> _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND)
                SetlistCloud.Received.Offline -> _ui.value = ScanUi.Fetching(CodeProblem.OFFLINE)
                is SetlistCloud.Received.Newer -> _ui.value = ScanUi.Fetching(CodeProblem.NEWER)
                is SetlistCloud.Received.Invite -> { invite = r.from; _ui.value = ScanUi.Invite(r.from.name) }
            }
        }
    }

    /** Text of a QR scanned by the camera (already decompressed). */
    fun onText(text: String) {
        if (_ui.value !is ScanUi.Scanning) return
        // QR of an internet code (photo of the sender's dialog): fetch by the code
        ShareCode.fromQr(text)?.let { code = it; fetch(); return }
        if (QrPayload.parse(text) == null) {
            // starts like a setlist QR but the count doesn't match: cut scan, scan again; newer format: update
            _ui.value = ScanUi.Scanning(
                when {
                    QrPayload.isNewer(text) -> ScanProblem.NEWER
                    text.startsWith(QrPayload.HEADER) -> ScanProblem.INCOMPLETE
                    else -> ScanProblem.NOT_SETLIST
                },
            )
            return
        }
        review(text, emptyList())
    }

    private val _parked = MutableStateFlow(0)
    /** How many more setlists came in the same send and wait in Received (the screen says so once). */
    val parked: StateFlow<Int> = _parked

    // several setlists in one code (user, 01/11): the first is reviewed here, the others go to Received with their
    // own songs, each reviewed when the person wants
    internal fun park(others: List<String>, songs: List<SetlistCloud.SharedSong>) {
        if (others.isEmpty()) return
        val from = sender
        others.forEach { list ->
            val titles = QrPayload.parse(list)?.songs?.map { TextKey.of(it.title) }?.toSet().orEmpty()
            inbox.add(from?.id.orEmpty(), from?.name.orEmpty(), list, songs.filter { TextKey.of(it.title) in titles })
        }
        _parked.value = others.size
    }

    fun parkedShown() { _parked.value = 0 }

    internal fun review(text: String, songs: List<SetlistCloud.SharedSong>) {
        val parsed = QrPayload.parse(text) ?: run { _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND); return }
        val library = repository.library.value.songs
        val came = mutableMapOf<Int, SetlistCloud.SharedSong>()
        val other = mutableMapOf<Int, SetlistCloud.SharedSong>()
        val songsOnly = parsed.name.isEmpty()
        fun came(e: QrPayload.Entry) = songs.firstOrNull { it.isFor(e.title, e.artist) }
        val rows = parsed.songs.mapIndexed { i, e ->
            val m = SetlistMatcher.match(e, library)
            // loose songs: a similar one isn't the same, so the one that came goes in as new (in a setlist, the user
            // chooses)
            val c = if (m is QrMatch.Missing || (songsOnly && m is QrMatch.Similar)) came(e) else null
            if (c != null) came[i] = c
            when {
                m is QrMatch.Found -> {
                    // I have it: if another version came along, the row asks (mine stays unless I choose otherwise)
                    val theirs = came(e)?.takeIf { !SongMerge.same(it.text, m.song.text) }
                    if (theirs != null) other[i] = theirs
                    ScanRow(e.title, e.artist, true, listOf(m.song.ref()), m.song.id, differs = theirs != null)
                }
                c != null -> ScanRow(e.title, e.artist, false, emptyList(), null, carried = true, lyricsOnly = noChords(c))
                m is QrMatch.Similar -> ScanRow(e.title, e.artist, false, m.candidates.map { it.ref() }, null)
                else -> ScanRow(e.title, e.artist, false, emptyList(), null)
            }
        }
        carried = came
        incoming = other
        finished = false   // a new review can be created once
        _ui.value = ScanUi.Review(parsed.name, rows, songsOnly)
    }

    private fun Song.ref() = SongRef(id, title, author)

    // in the lyrics and chords profile, flag a song that arrived with lyrics only (chords missing)
    private fun noChords(s: SetlistCloud.SharedSong) =
        repository.library.value.look.chords && !br.app.stagebook.domain.Chords.hasChords(s.text)

    fun setName(name: String) = _ui.update { if (it is ScanUi.Review) it.copy(name = name) else it }

    /** Picks the song for an uncertain row (null = leave out). */
    fun choose(index: Int, songId: String?) = _ui.update { ui ->
        if (ui !is ScanUi.Review) ui else ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(chosen = songId) else r })
    }

    fun scanAgain() { if (code.isEmpty()) _ui.value = ScanUi.Scanning() else fetch() }

    // a double tap on Create / Add would make two setlists and save the songs twice: only the first one counts
    private var finished = false

    /** Loose songs: the ones that came and aren't here go into the library; returns how many. */
    fun addSongs(): Int {
        if (finished) return 0
        finished = true
        carried.values.forEach { s -> save(s) }
        (_ui.value as? ScanUi.Review)?.let(::applyIncoming)
        done()
        return carried.size
    }

    // a song that came goes into the library with its key computed (the editor shows it)
    private fun save(s: SetlistCloud.SharedSong): String =
        repository.saveSong(Song(title = s.title, author = s.author, year = s.year, text = br.app.stagebook.domain.Chords.withDetectedKey(s.text)))

    // a song that came answers a setlist line: same title, and the artist when both say one
    private fun SetlistCloud.SharedSong.isFor(title: String, artist: String) =
        TextKey.of(this.title) == TextKey.of(title) && SongMatch.sameArtistKey(TextKey.of(author), TextKey.of(artist))

    /** Is there already a setlist with this name (ignoring accents and case)? */
    fun existing(name: String): String? = repository.library.value.sets.firstOrNull { TextKey.of(it.name) == TextKey.of(name) }?.id

    /** Creates the setlist (or replaces the songs of the one with the same name, if [replaceId]) and returns the id. */
    fun create(replaceId: String? = null): String? = createWithSlots(replaceId)?.first

    // the carried ones go into the library now; returns the setlist and, in its order, each row's id (null = missing)
    private fun createWithSlots(replaceId: String?): Pair<String, List<String?>>? {
        if (finished) return null
        val ui = _ui.value as? ScanUi.Review ?: return null
        finished = true
        val slots = ui.rows.mapIndexed { i, r ->
            r.chosen ?: carried[i]?.let { s -> save(s) }
        }
        val id = replaceId ?: repository.createSetlist(uniqueName(ui.name.trim()))
        repository.setSetlistItems(id, slots.filterNotNull())
        applyIncoming(ui)
        done()
        return id to slots
    }

    /**
     * Creates the setlist with the found ones (and the carried ones) and starts importing the missing ones, one by one,
     * in their place (guided queue in the importer). Uncertain ones with no choice stay out.
     */
    fun createAndImport(replaceId: String? = null): Boolean {
        val ui = _ui.value as? ScanUi.Review ?: return false
        val (id, slots) = createWithSlots(replaceId) ?: return false
        val name = repository.library.value.setlist(id)?.name ?: ui.name
        val missing = ui.rows.withIndex().filter { (i, r) -> slots[i] == null && r.candidates.isEmpty() }
            .map { (i, r) -> ImportQueue.Item(r.title, r.artist, i) }
        importQueue.start(id, name, slots, missing)
        return missing.isNotEmpty()
    }

    // "Create another" with a name already in use: "Show (2)", "Show (3)"…
    private fun uniqueName(name: String): String {
        if (existing(name) == null) return name
        var n = 2
        while (existing("$name ($n)") != null) n++
        return "$name ($n)"
    }
}
