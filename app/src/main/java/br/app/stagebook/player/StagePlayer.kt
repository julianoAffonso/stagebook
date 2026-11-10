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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import br.app.stagebook.domain.MeterBallistics
import br.app.stagebook.domain.Meters
import br.app.stagebook.domain.MixerLevels
import br.app.stagebook.domain.Spectrum
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import br.app.stagebook.data.device.DeviceTweaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import br.app.stagebook.data.interfaceKinds
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
// [away]: the USB output dropped mid-song and hasn't come back; the track button shows it (user, 09/11), only while
// waiting (it carries on by itself): whatever ends the wait (a stop, a pause, the minute) ends it too
data class PlayerUi(val playing: Boolean = false, val waiting: Boolean = false, val message: String = "", val away: Boolean = false)

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
    // the player is on a USB interface: the metronome can sound there (user, 02/11)
    private val _usbReady = MutableStateFlow(false)
    val usbReady: StateFlow<Boolean> = _usbReady

    // backing track the player holds: live, the one playing goes on when the song on screen changes (TrackQueue)
    private val queue = br.app.stagebook.domain.TrackQueue<File>()
    private val wanted: File? get() = queue.wanted
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
        // USB through Android's audio (Settings): the default for interfaces with nothing remembered (UsbRoutes). The
        // service reads the way on connecting; a change connects again, and the service writes Android's USB routing
        // for the new way before it opens
        scope.launch {
            repository.library.map { it.look.usbViaAndroid }.distinctUntilChanged().collect { via ->
                val changed = PlayerService.androidDefault != via
                PlayerService.androidDefault = via
                // the player off: what the metronome reads until the service sets the interface's own way
                if (!PlayerService.running) PlayerService.viaAndroid = via
                else if (changed) PlayerService.reconnect()
            }
        }
        // the mixer changed (the general one in Settings, or the loaded song's own over the stage): measure what's
        // missing and apply right away (only with the player on)
        scope.launch {
            repository.library.map { lib -> lib.look.mixer to songMix(lib) }.distinctUntilChanged().collect { (general, _) ->
                mixer = general
                if (PlayerService.running) engine.withLock { measureAndApply() }
            }
        }
    }

    /** Song open on stage (null = no backing track): loads its track, or, with a track playing, after it stops. */
    fun open(track: File?) {
        if (queue.open(track) && PlayerService.running) scope.launch { load() }
        if (track != null) prepareUnread()
    }

    // ---- the interface on the cable (user, 10/11): known as soon as it's plugged in, not only on play ----
    private val usbManager get() = context.getSystemService(android.hardware.usb.UsbManager::class.java)
    private val deliveryPrefs get() = context.getSharedPreferences("usb_delivery", Context.MODE_PRIVATE)   // this device's only
    @Volatile private var deliveryWatch: Job? = null
    @Volatile private var deliveryRead = false   // this stream's reading is in; a new stream reads again

    private val usbWatch = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            syncPlugged()
            // plugged in: a new interface is read right away, wherever the app is (user, 10/11: waiting for a song was wrong)
            if (i.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) prepareUnread()
        }
    }

    init {
        // after usbWatch exists (initializers run in order). A receiver costs nothing until a cable comes or goes
        val f = android.content.IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, usbWatch, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { repository.loaded.first { it }; syncPlugged(); prepareUnread() }
    }

    /**
     * The mixer and the metronome show the interface on the cable, or the device when there's none. While the player is
     * connected, the player's own word wins (it knows the way and the real outputs). A cable pulled by accident and put
     * back comes back the same: the known interface keeps its outputs and routing.
     */
    fun syncPlugged() {
        if (PlayerService.connected) return
        val d = usbManager?.let { UsbLink.find(it) }
        repository.updateLook { look ->
            val m = look.mixer
            val out = m.plugged(d?.let(::keyOf), d?.productName?.trim().orEmpty())
            if (out == m.output) look else look.copy(mixer = m.copy(output = out))
        }
    }

    /**
     * The interface on the cable has never been read on this device: the player opens it now (plugged in, the app
     * opened with it plugged, a song with a track, the metronome settings), to read its outputs and how well this
     * device's USB carries it. Once per interface and device; the idle stream closes by itself after 10 s.
     */
    fun prepareUnread() {
        if (PlayerService.running || !repository.library.value.look.playerMode) return
        val d = usbManager?.let { UsbLink.find(it) } ?: return
        val key = keyOf(d)
        if (repository.library.value.look.mixer.known.containsKey(key) && deliveryPrefs.contains(key)) return
        ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
    }

    // the engine measures the delivery about 3.5 s after opening: read once it's there, warned about when it's low
    private fun checkDelivery(out: OutputInfo) {
        if (deliveryWatch?.isActive == true) return
        deliveryWatch = scope.launch {
            var hd = 0f
            for (i in 0 until DELIVERY_TRIES) {
                delay(DELIVERY_POLL_MS)
                if (!PlayerService.connected) return@launch
                hd = Engine.hostDelivery()
                if (hd > 0f) break
            }
            if (hd <= 0f) return@launch
            deliveryRead = true
            val warned = if (deliveryPrefs.contains(out.key + WARNED)) deliveryPrefs.getFloat(out.key + WARNED, 0f) else null
            deliveryPrefs.edit {
                putFloat(out.key, hd)
                if (br.app.stagebook.domain.HostDelivery.warns(hd, warned)) putFloat(out.key + WARNED, hd)
            }
            if (br.app.stagebook.domain.HostDelivery.warns(hd, warned)) {
                _hostWarning.value = br.app.stagebook.domain.HostWarning(out.name, 100f - hd, out.count)
            }
        }
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
        _ui.update { it.copy(away = false) }   // stopped on purpose: back to the start, nothing left to carry on
        if (PlayerService.running) scope.launch { engine.withLock { Engine.seek(0) } }
    }

    // the wait for an output that dropped mid-song (see serviceChanged)
    private var dropWait: kotlinx.coroutines.Job? = null

    fun play() {
        if (wanted == null) return
        dropWait?.cancel()
        if (!PlayerService.running) {
            ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
        } else {
            PlayerService.wake()   // the stream was closed for being idle: opens again, and this play waits for it
        }
        PlayerService.playWanted(true)
        _ui.update { it.copy(waiting = true) }
        queue.started()
        scope.launch {
            if (!load()) {
                PlayerService.playWanted(false)
                _ui.value = PlayerUi(message = context.getString(R.string.player_no_track))
                // nothing plays: a song changed while loading gets its own track (it stayed queued; audit 02/11)
                if (queue.stopped() && PlayerService.running) load()
                return@launch
            }
            startIfConnected()
        }
    }

    fun pause() {
        endWatch?.cancel()
        dropWait?.cancel()
        PlayerService.playWanted(false)
        // the sign goes with the wait: left on, it blinked on every song with nothing waiting for the interface (audit 10/11)
        _ui.update { it.copy(playing = false, waiting = false, away = false) }
        if (PlayerService.running) {
            Engine.pause()
            PlayerService.playing(false)
        }
        // the song on screen changed while this track played: its track comes in now
        if (queue.stopped() && PlayerService.running) scope.launch { load() }
    }

    /**
     * The metronome chose a USB output (user, 02/11): with a USB audio interface plugged in, turns the player on as play
     * does, with or without a track; [usbReady] says when it's there. False when no interface is plugged in.
     */
    /** A USB audio interface is plugged in. */
    fun interfacePresent(): Boolean {
        val usb = context.getSystemService(android.hardware.usb.UsbManager::class.java) ?: return false
        return usb.deviceList.values.any { br.app.stagebook.data.isUsbAudio(it.interfaceKinds()) }
    }

    fun connectForClick(): Boolean {
        if (!interfacePresent()) return false
        if (!PlayerService.running) ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
        else PlayerService.wake()
        return true
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
        Engine.setEq(p.eqMask != 0, cfg.eqGains())
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
                PlayerService.playWanted(false)
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

    // The notice a stage last showed. The notice is the service's state ("USB output disconnected" stays while the
    // interface is away) and the stage is made again for each song and setlist: each new one showed it again, the
    // whole time (user, 07/11). Main thread only. An empty notice (player off) clears it.
    private var noticeShown = ""

    /** A stage is about to show the player's [notice]: false when a stage already showed this same one. */
    fun firstShow(notice: String): Boolean {
        if (notice == noticeShown) return false
        noticeShown = notice
        return true
    }

    private fun serviceChanged() {
        _ui.update { it.copy(message = PlayerService.status) }
        val onUsb = PlayerService.running && PlayerService.connected && PlayerService.outputKey.startsWith("usb:")
        // only the engine's own USB path judges the interface's clock; through Android there's no input to judge
        val probing = onUsb && !PlayerService.viaAndroid && Engine.probing() != 0
        // ready for sound: the click and the blinks on screen start together, once the output isn't held any more
        _usbReady.value = onUsb && !probing
        // the click belongs to the USB output: the player turned off or now on the device's own output, it goes (audit
        // 02/11 #5: it stayed in the engine and, on outputs 1-2, would sound on the speaker). A quick reconnect of the
        // same interface keeps it; the stage puts it back if the interface returns after a longer drop
        if (!PlayerService.running || (PlayerService.connected && !PlayerService.outputKey.startsWith("usb:"))) usbClickOff()
        // another output connected: Settings start showing its routing, and the engine starts using it
        val out = OutputInfo(PlayerService.outputKey, PlayerService.outputName, PlayerService.outputCount)
        if (PlayerService.connected && out.key.isNotEmpty() && out != mixer.output) {
            repository.updateLook { it.copy(mixer = it.mixer.connected(out)) }
        }
        if (!PlayerService.connected) deliveryRead = false
        if (onUsb && !PlayerService.viaAndroid && !deliveryRead) checkDelivery(out)
        if (!PlayerService.connected) syncPlugged()   // dropped: what's on the cable now (none: the device)
        if (PlayerService.connected) {
            if (_ui.value.away) _ui.update { it.copy(away = false) }   // the interface is back
            // a click asked while the stream was closed starts now, with its count-in, as if it had just been asked
            if (!probing) pendingClick?.let { pendingClick = null; usbClick(it.bpm, it.out, it.skip, it.volume) }
            startIfConnected()
        } else if (_ui.value.playing) {
            // the output dropped while playing: the engine holds the position and the service reconnects by itself
            // within 5 s. A cable that doesn't come back within the minute stops the song (OutputWait): Play again
            _ui.update { it.copy(playing = false, waiting = true, away = true) }
            dropWait?.cancel()
            dropWait = scope.launch {
                val wait = br.app.stagebook.domain.OutputWait
                val um = context.getSystemService(android.hardware.usb.UsbManager::class.java)
                val t0 = android.os.SystemClock.elapsedRealtime()
                delay(wait.GIVE_UP_MS)
                // plugged back in time: the connection may still be on its way, so it waits for it (checked twice a second)
                while (_ui.value.waiting) {
                    val waited = android.os.SystemClock.elapsedRealtime() - t0
                    val plugged = um != null && UsbLink.find(um) != null
                    if (wait.givesUp(waited, PlayerService.connected, plugged)) { stop(); break }
                    if (!wait.keepWaiting(waited, PlayerService.connected, plugged)) break
                    delay(500)
                }
            }
        }
    }

    companion object {
        const val END_POLL_MS = 250L
        private const val DELIVERY_POLL_MS = 500L
        private const val DELIVERY_TRIES = 16   // 8 s: the reading comes about 3.5 s after opening
        private const val WARNED = "|warned"

        private fun keyOf(d: android.hardware.usb.UsbDevice) =
            String.format(java.util.Locale.ROOT, "usb:%04x:%04x", d.vendorId, d.productId)

        // the USB of this device can't carry the interface (shown over any screen, once; see HostDelivery)
        private val _hostWarning = MutableStateFlow<br.app.stagebook.domain.HostWarning?>(null)
        val hostWarning: StateFlow<br.app.stagebook.domain.HostWarning?> = _hostWarning

        fun dismissHostWarning() { _hostWarning.value = null }

        private const val KEY_BATTERY_ASKED = "batteryAsked"
        const val END_MARGIN_MS = 300L   // the real end comes when the last packet goes out; before that, it's already the end

        @Volatile private var instance: StagePlayer? = null

        // the only place that writes Android's USB routing: one thread, so a start and a stop can't cross
        private val routeScope = br.app.stagebook.data.backgroundScope(Dispatchers.IO.limitedParallelism(1))

        /** The service starts: on the engine route Android's own USB audio is switched off, then [then] runs on the main thread. */
        @JvmStatic
        fun routeUsbThen(context: Context, engineRoute: Boolean, then: Runnable) {
            val app = context.applicationContext
            routeScope.launch {
                DeviceTweaks.routeUsb(app, engineRoute)
                withContext(Dispatchers.Main) { then.run() }
            }
        }

        /** The service stopped: the device's own value comes back. */
        @JvmStatic
        fun releaseUsbRoute(context: Context) {
            val app = context.applicationContext
            routeScope.launch { DeviceTweaks.routeUsb(app, false) }
        }

        /** The way of an interface was chosen in Settings: with the player on, it connects again that way. */
        fun routeChanged() = PlayerService.reconnect()

        /** Player mode turned off in Settings: if the player had started, turn everything off. */
        fun turnOff() { instance?.shutdown() }

        /**
         * Metronome click into the USB interface (user, 02/11): mixed by the engine on the interface's clock. False when
         * the player isn't on a USB interface with that output: then the click stays on the device.
         */
        fun usbClick(bpm: Int, out: br.app.stagebook.domain.MetronomeOut, skip: Long = 0, volume: Int = br.app.stagebook.domain.MetronomeClick.FULL_VOLUME): Boolean {
            if (out.device || !PlayerService.running || !PlayerService.connected) return false
            if (!PlayerService.outputKey.startsWith("usb:") || out.out + (if (out.pair) 1 else 0) >= PlayerService.outputCount) return false
            // the engine holds its output while it judges the interface's clock: the click is set when that ends (the service
            // tells), so the sound and the blinks on screen start together
            if (!PlayerService.viaAndroid && Engine.probing() != 0) {
                pendingClick = PendingClick(bpm, out, skip, volume)
                PlayerService.clickChanged(true)
                return true
            }
            fun floats(s: ShortArray) = FloatArray(s.size) { s[it] / 32768f }
            val click = br.app.stagebook.domain.MetronomeClick
            Engine.setClick(
                floats(click.click(false, volume = volume)), floats(click.click(true, volume = volume)), click.samplesPerBeat(bpm),
                out.out, if (out.pair) out.out + 1 else -1, skip,
            )
            PlayerService.clickChanged(true)
            return true
        }

        private class PendingClick(val bpm: Int, val out: br.app.stagebook.domain.MetronomeOut, val skip: Long, val volume: Int)
        @Volatile private var pendingClick: PendingClick? = null

        /**
         * The stream was closed for being idle and a click on USB is asked: opens it again and starts the click, count-in
         * included, as soon as it's ready. False when the stream isn't closed (then [usbClick] or the usual path applies).
         */
        fun clickWhenReady(bpm: Int, out: br.app.stagebook.domain.MetronomeOut, skip: Long, volume: Int): Boolean {
            if (out.device || !PlayerService.running || !PlayerService.sleeping) return false
            pendingClick = PendingClick(bpm, out, skip, volume)
            PlayerService.clickChanged(true)   // keeps the stream from closing again while it opens
            PlayerService.wake()
            return true
        }

        /** Whether the player's output is connected right now. */
        fun outputConnected(): Boolean = PlayerService.connected

        /** The click asked while the stream opened is given up (the output never came, or the click was stopped). */
        fun dropPendingClick() { pendingClick = null }

        /** The USB interface the player is on (empty: none); the device metronome keeps away from it. */
        fun interfaceName(): String = if (PlayerService.outputKey.startsWith("usb:")) PlayerService.outputName.orEmpty() else ""

        /** No click on USB (also when the player is off: the engine keeps the setting for its next start). */
        /** The song plays through Android: as media too, the click would follow it to the interface. */
        fun viaAndroid() = PlayerService.viaAndroid

        fun usbClickOff() {
            pendingClick = null
            Engine.setClick(null, null, 0, -1, -1, 0)
            PlayerService.clickChanged(false)
        }

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
