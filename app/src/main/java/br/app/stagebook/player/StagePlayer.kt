package br.app.stagebook.player

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.edit
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import br.app.stagebook.R
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.Measure
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.forSong
import br.app.stagebook.domain.Mixer
import br.app.stagebook.domain.NormMode
import br.app.stagebook.domain.OutputInfo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import br.app.stagebook.domain.MeterBallistics
import br.app.stagebook.domain.Meters
import br.app.stagebook.domain.MixerLevels
import br.app.stagebook.domain.Spectrum
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the stage shows from the player: playing, waiting for the USB output to play, and the current notice (empty =
 * none).
 */
@Immutable
data class PlayerUi(val playing: Boolean = false, val waiting: Boolean = false, val message: String = "")

/**
 * The stage's bridge to the engine. Nothing from the player starts before the first play: not the service, not the
 * native library. The open song only notes the backing track; it's loaded on play (or when changing songs with the
 * player already on). At the end of the backing track, it stops and goes back to the start, waiting for the next play.
 */
@Singleton
class StagePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
) {
    private val scope = br.app.stagebook.data.backgroundScope(Dispatchers.Default)   // errors logged, never a crash on stage
    private val engine = Mutex()   // load and seek read the disk: off the UI thread and one at a time
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui

    @Volatile private var wanted: File? = null    // backing track of the open song
    @Volatile private var loaded: File? = null    // what's in the engine
    // what's in the engine with its mix and normalization applied: only this one plays (the output could connect while
    // load() was still measuring, and the song started with the old gains; audit 01/11)
    @Volatile private var ready: File? = null
    private var durationMs = -1L
    @Volatile private var endWatch: Job? = null   // swapped only under [engine]

    @Volatile private var mixer = MixerConfig()
    private val measures = mutableMapOf<Int, Measure>()   // of the loaded backing track, per mixer channel

    init {
        instance = this
        // the mixer changed (the general one in Settings, or the loaded song's own over the stage): measure what's
        // missing and apply right away (only with the player on)
        scope.launch {
            repository.library.map { lib -> lib.look.mixer to songMix(lib) }.distinctUntilChanged().collect { (general, _) ->
                mixer = general
                if (PlayerService.running) engine.withLock { measureAndApply() }
            }
        }
    }

    /** Song open on stage (null = no backing track): stops whatever is playing and notes the new one. */
    fun open(track: File?) {
        if (track == wanted) return
        wanted = track
        if (_ui.value.playing || _ui.value.waiting) pause()
        if (PlayerService.running) scope.launch { load() }
    }

    /** Ask to skip battery saving (play with the screen off)? Only once, and only if it hasn't yet. */
    fun needsBatteryAsk(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return !prefs.getBoolean(KEY_BATTERY_ASKED, false) && !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryAsked() = prefs.edit { putBoolean(KEY_BATTERY_ASKED, true) }

    private val prefs get() = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    /** Play, or stop the song: back to the start ([stops], the default live) or just pause where it is. */
    fun toggle(stops: Boolean = true) = if (_ui.value.playing || _ui.value.waiting) { if (stops) stop() else pause() } else play()

    /** Stops and goes back to the start, with the same steps as the end of the song (live, a pause makes no sense). */
    fun stop() {
        pause()
        if (PlayerService.running) scope.launch { engine.withLock { Engine.seek(0) } }
    }

    fun play() {
        if (wanted == null) return
        if (!PlayerService.running) {
            ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
        }
        _ui.update { it.copy(waiting = true) }
        scope.launch {
            if (!load()) {
                _ui.value = PlayerUi(message = context.getString(R.string.player_no_track))
                return@launch
            }
            startIfConnected()
        }
    }

    fun pause() {
        endWatch?.cancel()
        _ui.update { it.copy(playing = false, waiting = false) }
        if (PlayerService.running) {
            Engine.pause()
            PlayerService.playing(false)
        }
    }

    /** Turns off the player (closing player mode): releases USB, engine and service. */
    fun shutdown() {
        pause()
        loaded = null
        ready = null
        if (PlayerService.running) {
            context.startService(Intent(context, PlayerService::class.java).setAction(PlayerService.ACTION_STOP))
        }
    }

    private suspend fun load(): Boolean = engine.withLock {
        val f = wanted ?: return@withLock false
        if (f == loaded) return@withLock durationMs >= 0
        ready = null
        durationMs = Engine.load(f.absolutePath)
        loaded = f
        measures.clear()
        if (durationMs < 0) return@withLock false
        // file with more channels than the mixer shows: the mixer starts showing all of them
        val lanes = Engine.lanes()
        if (lanes > mixer.channels) repository.updateLook { it.copy(mixer = it.mixer.copy(channels = lanes)) }
        measureAndApply()
        ready = f
        true
    }

    // Normalization: measures (or reads from cache) each mixer channel that normalizes, and sends the mix to the
    // engine. With the engine locked: it runs before play, so a song never starts at one volume and jumps to another.
    private fun measureAndApply() {
        val f = loaded ?: return
        val lanes = Engine.lanes()
        val cfg = mixer.forSong(songMix(repository.library.value))   // the song's own mix, if it has one (31/10)
        for (g in cfg.groups(lanes)) {
            if (cfg.strip(g.first).norm == NormMode.NONE || g.first in measures) continue
            measure(f, Mixer.mask(g))?.let { measures[g.first] = it }
        }
        val p = Mixer.plan(cfg, lanes, measures)
        Engine.setMix(p.gains, p.norms, p.eqMask, p.route)
        // always the engine's 5 bands: a shorter list (an old or hand-edited file) made the native call throw (audit 01/11)
        Engine.setEq(cfg.eqOn, cfg.eqGains())
    }

    // the loaded backing track's song: each track file belongs to one song
    private fun songMix(lib: br.app.stagebook.data.model.Library): br.app.stagebook.domain.SongMix? =
        loaded?.name?.let { name -> lib.songs.firstOrNull { it.track == name }?.mix }

    private fun measure(f: File, mask: Int): Measure? {
        val prefs = context.getSharedPreferences("norm", Context.MODE_PRIVATE)
        val key = "${f.name}|${f.length()}|$mask"
        prefs.getString(key, null)?.split(';')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 2 }
            ?.let { return Measure(it[0], it[1]) }
        val m = Engine.analyzeMask(f.absolutePath, mask) ?: return null
        // measures of tracks no longer in the folder go away (the list only grew and went into every backup; audit 30/10)
        val present = f.parentFile?.list()?.toSet().orEmpty()
        prefs.edit {
            prefs.all.keys.filter { it.substringBefore('|') !in present }.forEach { remove(it) }
            putString(key, "${m[0]};${m[1]}")
        }
        return Measure(m[0], m[1])
    }

    // Only plays with the output ready (USB or device); without it, play waits (the service notifies when it connects).
    // Also resumes after a reconnection: stopping the engine resets its play, but not the position.
    // Called from the main thread (service) and from play(): checked and started under [engine], after load() finished,
    // so only one end watch ever runs.
    private fun startIfConnected() {
        scope.launch {
            engine.withLock {
                val ui = _ui.value
                if (!(ui.waiting || ui.playing) || !PlayerService.connected || ready == null || ready != wanted) return@withLock
                Engine.play()
                PlayerService.playing(true)
                _ui.update { it.copy(playing = true, waiting = false) }
                endWatch?.cancel()
                endWatch = scope.launch {
                    while (isActive) {
                        delay(END_POLL_MS)
                        if (durationMs > 0 && Engine.position() >= durationMs - END_MARGIN_MS) {
                            // pause() cancels this watch: without NonCancellable, a busy engine (a mixer change being
                            // measured) skipped the seek and the next play stayed at the end (audit 01/11)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                pause()
                                engine.withLock { Engine.seek(0) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun serviceChanged() {
        _ui.update { it.copy(message = PlayerService.status) }
        // another output connected: Settings start showing its routing, and the engine starts using it
        val out = OutputInfo(PlayerService.outputKey, PlayerService.outputName, PlayerService.outputCount)
        if (PlayerService.connected && out.key.isNotEmpty() && out != mixer.output) {
            repository.updateLook { it.copy(mixer = it.mixer.copy(output = out)) }
        }
        if (PlayerService.connected) {
            startIfConnected()
        } else if (_ui.value.playing) {
            // the output dropped while playing: the engine holds the position and the service reconnects by itself
            // within 5 s
            _ui.update { it.copy(playing = false, waiting = true) }
        }
    }

    companion object {
        const val END_POLL_MS = 250L
        private const val KEY_BATTERY_ASKED = "batteryAsked"
        const val END_MARGIN_MS = 300L   // the real end comes when the last packet goes out; before that, it's already the end

        @Volatile private var instance: StagePlayer? = null

        /** Player mode turned off in Settings: if the player had started, turn everything off. */
        fun turnOff() { instance?.shutdown() }

        private const val METER_MS = 66L          // ~15 reads per second
        private const val METER_IDLE_MS = 500L    // player off: only check whether it turned on

        /**
         * Live meters for Settings (channels and spectrum). The engine only measures while someone collects and the
         * player is on; when collecting stops, measuring turns off. None of this loads the engine with the player off.
         */
        fun liveLevels(): Flow<MixerLevels> = flow {
            val meters = MeterBallistics(MixerConfig.MAX)
            val spectrum = MeterBallistics(Spectrum.BINS, Spectrum.FLOOR_DB)
            var wasLive = true
            try {
                while (true) {
                    if (PlayerService.running) {
                        Engine.setMeter(true)
                        val lanes = Engine.lanePeaks()
                        val p = Engine.peaks()
                        if (lanes != null && p != null && p.size >= PEAK_OUTS + Spectrum.BINS) {
                            val db = FloatArray(MixerConfig.MAX) { Meters.toDb(lanes[it]) }
                            val sp = FloatArray(Spectrum.BINS) { Meters.toDb(p[PEAK_OUTS + it], Spectrum.FLOOR_DB) }
                            emit(MixerLevels(meters.update(db, METER_MS), spectrum.update(sp, METER_MS).levelDb, live = true))
                            wasLive = true
                        }
                        delay(METER_MS)
                    } else {
                        if (wasLive) emit(MixerLevels.SILENT)
                        wasLive = false
                        delay(METER_IDLE_MS)
                    }
                }
            } finally {
                if (PlayerService.running) Engine.setMeter(false)
            }
        }.flowOn(Dispatchers.Default)

        private const val PEAK_OUTS = 4   // the engine's peaks(): 4 outputs and then the spectrum

        /** The service changed state (connected, dropped, turned off): called on the main thread. */
        @JvmStatic
        fun onServiceChanged() { instance?.serviceChanged() }
    }
}
