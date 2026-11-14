package br.app.stagebook.ui.stage

import br.app.stagebook.player.PlayerUi
import br.app.stagebook.player.StagePlayer
import br.app.stagebook.domain.ignoringShortDrops
import br.app.stagebook.data.TrackStore
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.Voicings
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.toSongMix
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Look
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.songs.subtitle
import br.app.stagebook.ui.songs.artistLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import br.app.stagebook.data.model.claimOldZoom
import br.app.stagebook.data.model.withZoom
import javax.inject.Inject

@Immutable
data class StageSong(val title: String, val meta: String, val text: String, val missing: Boolean, val id: String = "", val zoom: Float = 1f, val track: String = "", val mix: br.app.stagebook.domain.SongMix? = null,
    val lostTrack: String = "",   // the name of an audio that didn't come with the song ("" = none lost)
    val zooms: Map<String, br.app.stagebook.data.model.ScreenZoom> = emptyMap(),
    val fit: br.app.stagebook.data.model.ScreenZoom? = null,   // the calculated zoom saved for this screen
    val artist: String = "",   // for the setlist's panel, whose rows leave the year out ([meta] is the header's: artist and year)
) {
    /** The song as [screen] shows it: its zoom there, or none, which is the automatic one. */
    fun onScreen(screen: String): StageSong {
        val saved = zooms[screen] ?: return this
        return if (saved.fit.isEmpty()) copy(zoom = saved.zoom) else copy(fit = saved)
    }
}

/**
 * The setlist the stage was opened from, for the panel over the stage: its [name] and, for each song, where the show
 * is ([states], the same marks the setlist's screen shows) and its key ([keys], "" = none written).
 */
@Immutable
data class StageSetlist(val name: String, val states: List<br.app.stagebook.domain.SetlistShow.State> = emptyList(), val keys: List<String> = emptyList())

/**
 * The leader's break on a follower's stage: [since] when it started (this device's clock), [next] the song that comes
 * after it and that song's [key] here ("" = none written, or the song isn't on this device).
 */
@Immutable
data class FollowBreak(val since: Long, val next: String, val key: String = "")

/**
 * The break a follower's stage shows: the next song is the one the leader announced, with the title and key of this
 * device's own copy when it has one.
 */
internal fun followBreak(role: br.app.stagebook.data.BandConduct.Role.Following, songs: List<br.app.stagebook.data.model.Song>): FollowBreak? {
    val since = role.breakSince ?: return null
    val item = role.msg.songs.getOrNull(role.msg.index + 1) ?: return null
    val own = br.app.stagebook.domain.Conduct.find(item, songs)
    return FollowBreak(since, own?.title ?: item.title, own?.let { Chords.key(it.text) }.orEmpty())
}

/**
 * A stage opened for the leader's break alone ([Stage.leaderBreak]) has nothing left to show: not following any more,
 * or the leader left the break for a song that isn't here either. With the song here, that song's stage takes this
 * one's place, so it waits for it instead of showing the screen behind for a moment.
 */
internal fun leaderBreakOver(role: br.app.stagebook.data.BandConduct.Role): Boolean {
    val following = role as? br.app.stagebook.data.BandConduct.Role.Following ?: return true
    return following.songId == null && following.breakSince == null
}

@Immutable
data class StageUiState(
    val loading: Boolean = true,
    val songs: List<StageSong> = emptyList(),
    val look: Look = Look(),
    /** Following the band: the leader's next song (the follower's stage holds only the current one); [followEnd]: it's the last. */
    val followNext: String? = null,
    val followEnd: Boolean = false,
    /** Following the band: the leader is on a break, and this stage shows it too (null = no break). */
    val followBreak: FollowBreak? = null,
    /** A stage opened for the leader's break alone: the break is over and no song of this device takes its place. */
    val breakOver: Boolean = false,
    /** A show (a setlist, or following the band): leaving asks first. A single song from the library leaves at once. */
    val live: Boolean = false,
    /** The setlist's breaks: each one comes before the song at that position (see SetlistBreaks). */
    val breaks: List<Int> = emptyList(),
    /** Opened from a setlist: it can be opened over the stage. Null for a single song (the library, or following the band). */
    val setlist: StageSetlist? = null,
)

@HiltViewModel
class StageViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle,
    private val stageGate: StageGate,
    private val tracks: TrackStore,
    private val player: StagePlayer,
    private val conduct: br.app.stagebook.data.BandConduct,
    private val metronome: br.app.stagebook.data.MetronomeSound,
    private val chordPlayer: br.app.stagebook.data.ChordPlayer,
    private val announcer: br.app.stagebook.data.StageAnnouncer,
    private val midiOut: br.app.stagebook.data.MidiOut,
    private val show: br.app.stagebook.data.SetlistShowStore,
) : ViewModel() {
    /** The song's MIDI on entering, and the one before's on leaving (user, 07/11); a stage extra (T19). */
    private fun sendMidi(index: Int) {
        val lib = repository.library.value
        if (!lib.look.extrasOn) return
        val ids = route.setId?.let(lib::setlist)?.items ?: listOfNotNull(route.songId)
        val song = ids.getOrNull(index)?.let(lib::song)
        midiOut.songOnStage(song?.id, song?.midi)
    }

    /** Now Playing: the current song for OBS and others (if on in Settings → Screen). */
    fun announce(index: Int) {
        val lib = repository.library.value
        if (!lib.look.announce || !lib.look.extrasOn) { announcer.stop(); return }   // Now Playing is a stage extra (T19)
        val set = route.setId?.let(lib::setlist)
        val ids = set?.items ?: listOfNotNull(route.songId)
        val song = ids.getOrNull(index)?.let(lib::song) ?: return
        announcer.announce(
            br.app.stagebook.domain.StageAnnouncement(
                title = song.title, artist = song.author, set = set?.name.orEmpty(),
                index = if (set != null) index + 1 else 0, count = if (set != null) ids.size else 0,
                key = Chords.key(song.text), bpm = Chords.tempo(song.text),
            ),
        )
    }

    /** Plays the stage chord the way chosen in Settings (picked, strummed or both). */
    fun playChord(notes: List<Int>, keyboard: Boolean) = chordPlayer.play(notes, repository.library.value.look.chordPlay, keyboard)

    /**
     * Metronome click (null = stopped or visual only): on the USB output chosen in Settings when the player is there
     * (user, 02/11), otherwise on the device's regular output.
     */
    fun metronomeSound(bpm: Int?) {
        clickFallback?.cancel()
        val look = repository.library.value.look
        val usb = br.app.stagebook.domain.MetronomeOut(look.metroUsbOut, look.metroUsbPair)
        val skip = if (look.metroCount) br.app.stagebook.domain.MetronomeClick.COUNT_IN_SKIP else 0L
        val volume = look.metroVolume
        if (bpm == null) { metronome.stop(); br.app.stagebook.player.StagePlayer.usbClickOff(); return }
        val way = br.app.stagebook.domain.ClickPath.choose(
            look.playerMode, usb,
            usbNow = { br.app.stagebook.player.StagePlayer.usbClick(bpm, usb, skip, volume) },
            wake = { br.app.stagebook.player.StagePlayer.clickWhenReady(bpm, usb, skip, volume) },
            connect = { player.connectForClick() },
        )
        when (way) {
            br.app.stagebook.domain.ClickPath.Way.USB -> metronome.stop()
            // the USB stream was closed for being idle: opens again, and the click starts by itself (count-in included) as
            // soon as it's ready, with the player's "preparing the interface" notice; the device after a while if it never is
            br.app.stagebook.domain.ClickPath.Way.WAKE -> {
                metronome.stop()
                clickFallback = viewModelScope.launch {
                    kotlinx.coroutines.delay(USB_WAIT_MS)
                    if (!br.app.stagebook.player.StagePlayer.outputConnected()) {
                        br.app.stagebook.player.StagePlayer.usbClickOff()
                        metronome.start(bpm, skip, volume)
                    }
                }
            }
            // a USB output chosen and the interface plugged in: the metronome turns the player on, track or not, and
            // sounds there once it's connected ([usbReady] restarts it on the stage); meanwhile nothing on the device
            br.app.stagebook.domain.ClickPath.Way.CONNECT -> {
                metronome.stop(); br.app.stagebook.player.StagePlayer.usbClickOff()
                // the interface never got ready (USB permission refused, an error): the device after a while instead of
                // silence (audit 02/11 #4); if it gets ready later the stage restarts the metronome there
                clickFallback = viewModelScope.launch { kotlinx.coroutines.delay(USB_WAIT_MS); if (!player.usbReady.value) metronome.start(bpm, skip, volume) }
            }
            br.app.stagebook.domain.ClickPath.Way.DEVICE -> { br.app.stagebook.player.StagePlayer.usbClickOff(); metronome.start(bpm, skip, volume) }
        }
    }

    /** Song on screen (position in the setlist): when leading this setlist, the band opens the same one. */
    fun songShown(index: Int) {
        shownIndex = index
        resumeBreak(index)
        route.setId?.let { conduct.open(it, index) }
        announce(index)
        sendMidi(index)
        markOnStage(index)
    }

    /**
     * The setlist screen's marks: this song is the one on stage now, and after a minute in a row on it, played. Only
     * for a stage opened from a setlist. A break doesn't change the song on stage, so its time never counts for the
     * song that comes after it.
     */
    private fun markOnStage(index: Int) {
        val setId = route.setId ?: return
        val songId = repository.library.value.setlist(setId)?.items?.getOrNull(index) ?: return
        if (songId == markedSong) return   // shown again (the screen turned): its minute goes on counting
        markedSong = songId
        show.onStage(setId, songId)
        playedJob?.cancel()
        // dies with the stage: a song left before its minute isn't played
        playedJob = viewModelScope.launch {
            kotlinx.coroutines.delay(br.app.stagebook.domain.SetlistShow.PLAYED_AFTER_MS)
            show.played(setId, songId)
        }
    }

    private var markedSong: String? = null
    private var playedJob: kotlinx.coroutines.Job? = null

    /** Playing (or waiting for the USB output to play) and the player notice. */
    val playerUi: StateFlow<PlayerUi> = player.ui
    fun playerNoticeFresh(notice: String) = player.firstShow(notice)
    private var clickFallback: kotlinx.coroutines.Job? = null

    // short drops ignored: the interface reconnecting for its clock restarted the metronome twice (audit 02/11 #3)
    /** A USB audio interface is plugged in (the metronome on USB waits for it to be ready; the device's click otherwise). */
    fun usbPlugged(): Boolean = player.interfacePresent()
    val usbReady: StateFlow<Boolean> = player.usbReady.ignoringShortDrops(USB_DROP_MS)
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, player.usbReady.value)

    fun togglePlay() = player.toggle(repository.library.value.look.trackStops)
    fun needsBatteryAsk() = player.needsBatteryAsk()
    fun batteryAsked() = player.batteryAsked()

    /** Song on screen: the player notes its backing track (and stops the previous one's). */
    fun showSong(track: String) = player.open(track.takeIf { it.isNotEmpty() }?.let(tracks::file))

    /**
     * Links the chosen track to the song (copied into the app); the previous one is deleted. False if the file won't
     * work.
     */
    suspend fun attachTrack(songId: String, uri: android.net.Uri): Boolean {
        // cancelling the picker never gets here, and a file that isn't audio returns before anything changes: the
        // current track stays (user's question, 01/11)
        val name = tracks.copy(songId, uri) ?: return false
        val shown = tracks.displayName(uri)
        val old = repository.song(songId)?.track.orEmpty()
        repository.updateSong(songId) { it.copy(track = name, trackName = shown) }
        if (old != name) tracks.remove(old)
        return true
    }


    fun removeTrack(songId: String) {
        val old = repository.song(songId)?.track.orEmpty()
        repository.updateSong(songId) { it.copy(track = "", trackName = "") }
        player.open(null)
        tracks.remove(old)
    }

    init {
        stageGate.enter()   // holds the Drive sync while the stage is open
        // yesterday's counter (saved with the settings, or from a restored backup) isn't shown as if it were running
        val look = repository.library.value.look
        val mode = br.app.stagebook.domain.ShowTimer.onOpening(look.clockMode, look.showStart, System.currentTimeMillis())
        if (mode != look.clockMode) repository.updateLook { it.copy(clockMode = mode) }
    }
    override fun onCleared() {
        stageGate.leave()
        // the break goes away with this stage: the band doesn't stay on a break nobody can end
        route.setId?.let { conduct.rest(it, null) }
        // leaving the stage stops the backing track; a follower going to the leader's next song swaps one stage for
        // another (the new one enters first), and the track playing goes on, as when changing songs (audit 02/11)
        // back to the start: the same song on another stage (a setlist) went on from where it was left
        if (!stageGate.onStage.value) { player.stop(); midiOut.leftStage() }
        metronome.stop()
        chordPlayer.stop()
        announcer.stop()
        // leaving the stage does NOT end "Lead the band" (user's request): during a show someone steps out to check
        // something and the band stays together; only "End" releases everyone
    }
    private val route = savedStateHandle.toRoute<Stage>()

    // a show on another day starts with no song marked (after `route`, which the init block above can't see yet)
    init { route.setId?.let(show::stageOpened) }

    /**
     * The setlist's break on screen: where its counter would have started (null = no break). The song on stage stays the
     * one before the break, so the next one only "enters" (MIDI, Now Playing, the band) when the break is left. The
     * counter itself is in [show] (by wall clock, per setlist and break), so turning the device, leaving the stage or
     * going on to the next song and back doesn't restart it; it ends when the song after it has been played.
     */
    val breakSince: StateFlow<Long?> = savedStateHandle.getStateFlow<Long?>(BREAK_SINCE, null)

    // the song after the break on screen: the counter's key in [show]
    private val breakSong = savedStateHandle.getStateFlow<String?>(BREAK_SONG, null)

    /** The counter of the break on screen (null = none): running or paused, for the stage to draw. */
    val breakTimer: StateFlow<br.app.stagebook.domain.BreakTimer.Timer?> =
        combine(show.breaks, breakSong) { all, song -> song?.let { all[br.app.stagebook.domain.BreakTimer.key(route.setId.orEmpty(), it)] } }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    fun setBreak(on: Boolean) {
        if (on == (breakSince.value != null)) return
        if (!on) {
            val setId = route.setId
            val song = breakSong.value
            if (setId != null && song != null) show.breakLeft(setId, song)
            savedStateHandle[BREAK_SINCE] = null; savedStateHandle[BREAK_SONG] = null; return }
        val setId = route.setId ?: return
        val song = repository.library.value.setlist(setId)?.items?.getOrNull(shownIndex + 1) ?: "#${shownIndex + 1}"
        val timer = show.breakEntered(setId, song)
        savedStateHandle[BREAK_SONG] = song
        savedStateHandle[BREAK_SINCE] = br.app.stagebook.domain.BreakTimer.since(timer, System.currentTimeMillis())
    }

    private var reopened = false

    /**
     * The stage opened from the setlist's open break row ([Stage.atBreak]), on the song before it: the break comes back,
     * with its counter. Only that: a tap on the song before an open break opens the song (a stage that opened the break
     * by itself made it impossible to play that song, user 14/11). Only the first song shown counts, and not for a
     * follower (the leader's stage says what is shown). Coming back from an editor or Settings needs none of this: the
     * stage is still in the back stack, with its break.
     */
    private fun resumeBreak(index: Int) {
        if (reopened) return
        reopened = true
        val setId = route.setId ?: return
        if (!route.atBreak || breakOnly || conduct.role.value is br.app.stagebook.data.BandConduct.Role.Following) return
        val next = repository.library.value.setlist(setId)?.items?.getOrNull(index + 1) ?: return
        if ((index + 1) in repository.library.value.setlist(setId)!!.breaks && show.breakOpen(setId, next)) setBreak(true)
    }

    fun pauseBreak() = breakSong.value?.let { song -> route.setId?.let { show.breakPause(it, song) } }

    fun resumeBreak() = breakSong.value?.let { song -> route.setId?.let { show.breakResume(it, song) } }

    /** Zero: keeps running if it was running, stays paused if it was paused. */
    fun resetBreak() = breakSong.value?.let { song -> route.setId?.let { show.breakReset(it, song) } }

    // the position on screen, as [songShown] last heard it
    private var shownIndex = -1

    // leading the band: the followers' stages show this stage's break, and leave it, with it. How long it has lasted
    // goes along, so a stage that comes back in the middle of a break (the device turned) says the right time
    init {
        route.setId?.let { setId ->
            // the strip counts from the time it is told: a reset or a resume sends the new one
            viewModelScope.launch {
                combine(breakSince, breakTimer) { since, timer -> if (since == null) null else timer }.collect { timer ->
                    conduct.rest(setId, timer?.elapsed(System.currentTimeMillis()))
                }
            }
        }
    }

    /** Song the stage opens on (position in the setlist). */
    val start: Int = route.start

    /** Opened by following, without the leader's song: this stage is the leader's break and nothing else. */
    val breakOnly: Boolean = route.leaderBreak

    val uiState: StateFlow<StageUiState> = combine(repository.loaded, repository.library, conduct.role, show.marks) { loaded, lib, role, marks ->
        val set = route.setId?.let(lib::setlist)
        val ids = set?.items ?: listOfNotNull(route.songId)
        // following, the "Next:" at the end of the song comes from the leader's setlist, which the message carries
        // (user, 02/11: the follower saw no next song even with the same setlist)
        val following = (role as? br.app.stagebook.data.BandConduct.Role.Following)?.takeIf {
            // the song this stage holds is the leader's; or it holds none, and the leader's isn't on this device
            set == null && if (route.leaderBreak) it.songId == null else it.songId != null && it.songId == route.songId
        }
        val follow = following?.msg
        val followNext = follow?.songs?.getOrNull(follow.index + 1)?.title
        StageUiState(
            followNext = followNext,
            followEnd = follow != null && followNext == null && follow.index >= 0,
            followBreak = following?.let { followBreak(it, lib.songs) },
            breakOver = loaded && route.leaderBreak && leaderBreakOver(role),
            live = set != null || role is br.app.stagebook.data.BandConduct.Role.Following,
            loading = !loaded,
            songs = ids.map { id ->
                val s = lib.song(id)
                StageSong(s?.title.orEmpty(), s?.subtitle().orEmpty(), s?.text.orEmpty(), s == null, id, s?.zoom ?: 1f, s?.track.orEmpty(), s?.mix, if (s?.audioLost == true) s.trackName else "", s?.zooms.orEmpty(), artist = s?.artistLine().orEmpty())
            },
            look = lib.look,
            breaks = set?.breaks.orEmpty(),
            setlist = set?.let {
                StageSetlist(it.name, br.app.stagebook.domain.SetlistShow.states(it.items, marks[it.id]), it.items.map { id -> Chords.key(lib.song(id)?.text.orEmpty()) })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StageUiState())

    /** Chord shape picked on stage: goes into the song as a ChordPro {define} (replaces the previous one). */
    fun saveShape(songId: String, chord: String, define: String) {
        if (songId.isEmpty()) return
        val instrument = repository.library.value.look.instrument
        repository.updateSong(songId) { it.copy(text = Voicings.saveDefine(it.text, instrument, chord, define)) }
    }

    /** Chords taken off this song's chord list (the × on stage); empty brings them all back. */
    fun setHiddenChords(songId: String, hidden: Set<String>) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = Chords.setHiddenChords(it.text, hidden)) }
    }

    /** Pinch zoom, saved in the song (applies in any setlist). */
    /** Auto-scroll speed (−/+ on stage), saved in settings. */
    fun keyboardCapoHintSeen() = repository.updateLook { it.copy(keyboardCapoHintSeen = true) }

    /** Tapping the clock: the next face; the show's counter starts the first time it shows up. */
    fun nextClock() = repository.updateLook {
        val mode = br.app.stagebook.domain.ShowTimer.next(it.clockMode)
        val start = if (mode == br.app.stagebook.data.model.StageClockMode.CLOCK) it.showStart
            else br.app.stagebook.domain.ShowTimer.startFor(it.showStart, System.currentTimeMillis())
        it.copy(clockMode = mode, showStart = start)
    }

    fun stepShow(up: Boolean) = repository.updateLook { it.copy(showMinutes = br.app.stagebook.domain.ShowTimer.step(it.showMinutes, up)) }

    fun restartShow() = repository.updateLook { it.copy(showStart = System.currentTimeMillis()) }

    fun setAutoScroll(level: Int) = repository.updateLook { it.copy(autoScroll = level.coerceIn(1, br.app.stagebook.data.model.Look.MAX_AUTO_SCROLL)) }

    /** Key changed on stage ("Key G" menu): saved in the song right away, as in the editor. */
    fun transpose(songId: String, semitones: Int) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = Chords.transposeSong(it.text, semitones)) }
    }

    /** Capo changed on stage: saved in the song right away. */
    /**
     * The mixer over the stage: what's changed stays with the song (user, 31/10), starting from the general one; the
     * channel count stays general. Null = back to the general mixer.
     */
    fun setSongMix(songId: String, mixer: br.app.stagebook.domain.MixerConfig?) {
        if (mixer != null && mixer.channels != repository.library.value.look.mixer.channels) {
            repository.updateLook { it.copy(mixer = it.mixer.copy(channels = mixer.channels)) }
        }
        repository.updateSong(songId) { it.copy(mix = mixer?.toSongMix()) }
    }

    /** The global mixer changed from the stage (the song has none of its own): every song's, like Settings → Player. */
    fun setGlobalMix(mixer: br.app.stagebook.domain.MixerConfig) = repository.updateLook { it.copy(mixer = mixer) }

    /** The metronome's BPM changed on stage: saved in the song ({tempo}), like key and capo (user, 02/11). */
    fun setTempo(songId: String, bpm: Int) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = Chords.setDirective(it.text, "tempo", bpm.toString())) }
    }

    fun setCapo(songId: String, capo: Int) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = Chords.withCapo(it.text, capo)) }
    }

    /** "Highlight this line" on stage (B-28): saved in the song right away, like key and capo. */
    /** Highlighter on stage: the words from [from] to [to] of line [line] as [shown] get (or lose) their highlight. */
    fun highlightWords(songId: String, line: Int, shown: String, from: Int, to: Int, after: Int = 0) {
        if (songId.isEmpty()) return
        repository.updateSong(songId) { song ->
            val lines = song.text.split('\n').toMutableList()
            val saved = lines.getOrNull(line) ?: return@updateSong song
            lines[line] = br.app.stagebook.domain.HighlightRange.toggle(saved, shown, from, to, after)
            song.copy(text = lines.joinToString("\n"))
        }
    }

    /** Saves the zoom of [screen]: a pinch, a calculated one, or null to go back to automatic. */
    fun setZoom(songId: String, screen: String, zoom: br.app.stagebook.data.model.ScreenZoom?) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.withZoom(screen, zoom) }
    }

    fun claimOldZoom(songId: String, screen: String) {
        if (repository.song(songId)?.let { it.zoom != 1f && screen !in it.zooms } == true) repository.updateSong(songId) { it.claimOldZoom(screen) }
    }
}

private const val BREAK_SINCE = "breakSince"
private const val BREAK_SONG = "breakSong"     // the song after the break on screen: its counter's key
private const val USB_WAIT_MS = 6_000L   // a USB metronome output not ready by then: the device's own
private const val USB_DROP_MS = 3_000L   // a USB "not ready" shorter than this doesn't restart the metronome
