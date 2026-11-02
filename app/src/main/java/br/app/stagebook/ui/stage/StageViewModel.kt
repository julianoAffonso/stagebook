package br.app.stagebook.ui.stage

import br.app.stagebook.player.PlayerUi
import br.app.stagebook.player.StagePlayer
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class StageSong(val title: String, val meta: String, val text: String, val missing: Boolean, val id: String = "", val zoom: Float = 1f, val track: String = "", val mix: br.app.stagebook.domain.SongMix? = null)

@Immutable
data class StageUiState(
    val loading: Boolean = true,
    val songs: List<StageSong> = emptyList(),
    val look: Look = Look(),
)

@HiltViewModel
class StageViewModel @Inject constructor(
    private val repository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
    private val stageGate: StageGate,
    private val tracks: TrackStore,
    private val player: StagePlayer,
    private val conduct: br.app.stagebook.data.BandConduct,
    private val metronome: br.app.stagebook.data.MetronomeSound,
    private val chordPlayer: br.app.stagebook.data.ChordPlayer,
    private val announcer: br.app.stagebook.data.StageAnnouncer,
) : ViewModel() {
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
        val look = repository.library.value.look
        val usb = br.app.stagebook.domain.MetronomeOut(look.metroUsbOut, look.metroUsbPair)
        when {
            bpm == null -> { metronome.stop(); br.app.stagebook.player.StagePlayer.usbClickOff() }
            look.playerMode && br.app.stagebook.player.StagePlayer.usbClick(bpm, usb) -> metronome.stop()
            // a USB output chosen and the interface plugged in: the metronome turns the player on, track or not, and
            // sounds there once it's connected ([usbReady] restarts it on the stage); meanwhile nothing on the device
            look.playerMode && !usb.device && player.connectForClick() -> { metronome.stop(); br.app.stagebook.player.StagePlayer.usbClickOff() }
            else -> { br.app.stagebook.player.StagePlayer.usbClickOff(); metronome.start(bpm) }
        }
    }

    /** Song on screen (position in the setlist): when leading this setlist, the band opens the same one. */
    fun songShown(index: Int) {
        route.setId?.let { conduct.open(it, index) }
        announce(index)
    }

    /** Playing (or waiting for the USB output to play) and the player notice. */
    val playerUi: StateFlow<PlayerUi> = player.ui
    val usbReady: StateFlow<Boolean> = player.usbReady

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

    init { stageGate.enter() }   // holds the Drive sync while the stage is open
    override fun onCleared() {
        player.pause()   // leaving the stage stops the backing track
        metronome.stop()
        chordPlayer.stop()
        announcer.stop()
        stageGate.leave()
        // leaving the stage does NOT end "Lead the band" (user's request): during a show someone steps out to check
        // something and the band stays together; only "End" releases everyone
    }
    private val route = savedStateHandle.toRoute<Stage>()
    /** Song the stage opens on (position in the setlist). */
    val start: Int = route.start

    val uiState: StateFlow<StageUiState> = combine(repository.loaded, repository.library) { loaded, lib ->
        val set = route.setId?.let(lib::setlist)
        val ids = set?.items ?: listOfNotNull(route.songId)
        StageUiState(
            loading = !loaded,
            songs = ids.map { id ->
                val s = lib.song(id)
                StageSong(s?.title.orEmpty(), s?.subtitle().orEmpty(), s?.text.orEmpty(), s == null, id, s?.zoom ?: 1f, s?.track.orEmpty(), s?.mix)
            },
            look = lib.look,
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

    /** Pinch zoom, saved in the song (applies in any setlist, like another app). */
    /** Auto-scroll speed (−/+ on stage), saved in settings. */
    fun keyboardCapoHintSeen() = repository.updateLook { it.copy(keyboardCapoHintSeen = true) }

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

    fun setCapo(songId: String, capo: Int) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = Chords.withCapo(it.text, capo)) }
    }

    /** "Highlight this line" on stage (B-28): saved in the song right away, like key and capo. */
    fun toggleLineHighlight(songId: String, line: Int) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(text = br.app.stagebook.ui.editor.toggleLineHighlight(it.text, line)) }
    }

    fun setZoom(songId: String, zoom: Float) {
        if (songId.isNotEmpty()) repository.updateSong(songId) { it.copy(zoom = zoom) }
    }
}
