package br.app.stagebook.ui.settings

import br.app.stagebook.data.model.ImportSite
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.data.model.ChordListPlace
import br.app.stagebook.data.model.StageFont
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SectionLang
import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.data.AppLanguage
import br.app.stagebook.data.Languages
import br.app.stagebook.data.MidiFound
import br.app.stagebook.data.MidiPedal
import br.app.stagebook.data.MidiState
import br.app.stagebook.domain.MidiCode
import br.app.stagebook.domain.PedalLearn
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.PedalPress
import br.app.stagebook.ui.isExternal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PedalKeyUi(val name: String, val action: PedalAction, val device: String = "", val keyCode: Int = 0)

@Immutable
data class SettingsUiState(
    val loading: Boolean = true,
    val look: Look = Look(),
    val songCount: Int = 0,
    val setCount: Int = 0,
    val lastKey: PedalKeyUi? = null,
    val version: String = "",
    val pedalConnected: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
    val midi: MidiState = MidiState(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val ownMix: Int = 0,   // songs with their own mix (changed over the stage): the general mixer doesn't reach them
)

/** Settings → Update the app. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionName: String) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Ready : UpdateState            // downloaded: Android's "Install" tap is still needed
    data object Offline : UpdateState          // couldn't find the published version (no internet?)
    data object DownloadFailed : UpdateState
}

class PendingImport(val json: String, val added: Int, val lyricsChanged: Int)

sealed interface SettingsMessage {
    data class Normalized(val count: Int, val unidentified: Int) : SettingsMessage
    data class Imported(val added: Int, val replaced: Int) : SettingsMessage
    data object ImportFailed : SettingsMessage
    data object ImportNewer : SettingsMessage   // file from a newer Stagebook: update
    data object BackupDone : SettingsMessage
    data object BackupFailed : SettingsMessage
    data object NotBackup : SettingsMessage
    data object RestoreFailed : SettingsMessage
    data class Restored(val restart: Boolean, val skipped: Int = 0) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    @ApplicationContext private val context: Context,
    private val updater: AppUpdater,
    private val midi: MidiPedal,
    private val backup: br.app.stagebook.data.Backup,
) : ViewModel() {
    /** Backup read and waiting for the choice of parts to restore. */
    data class PendingRestore(val uri: Uri, val contents: br.app.stagebook.data.Backup.Contents)

    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore.asStateFlow()

    fun backupTo(uri: Uri, parts: Set<br.app.stagebook.data.Backup.Part>) = viewModelScope.launch {
        val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { backup.write(it, parts) } }.isSuccess
        _messages.send(if (ok) SettingsMessage.BackupDone else SettingsMessage.BackupFailed)
    }

    /** Open file: a .stagebook shows the parts to choose; an old songs file (.json) adds the songs. */
    fun inspectBackup(uri: Uri) = viewModelScope.launch {
        val c = runCatching { context.contentResolver.openInputStream(uri)!!.use { backup.inspect(it) } }.getOrNull()
        when {
            c == null -> importFrom(uri)   // not a .stagebook: tries the old songs file (or says it couldn't)
            c.newer -> _messages.send(SettingsMessage.ImportNewer)
            else -> _pendingRestore.value = PendingRestore(uri, c)
        }
    }

    fun cancelRestore() { _pendingRestore.value = null }

    /** Erase everything: Android clears the app data (songs, tracks, settings, band) and closes it; it opens clean. */
    fun factoryReset() { context.getSystemService(android.app.ActivityManager::class.java)?.clearApplicationUserData() }

    fun restore(parts: Set<br.app.stagebook.data.Backup.Part>, replace: Boolean) = viewModelScope.launch {
        val p = _pendingRestore.value ?: return@launch
        _pendingRestore.value = null
        val ok = runCatching { context.contentResolver.openInputStream(p.uri)!!.use { backup.restore(it, parts, replace) } }.getOrDefault(false)
        // settings restored (language, look): the screen opens again so they apply
        _messages.send(if (!ok) SettingsMessage.RestoreFailed else SettingsMessage.Restored(br.app.stagebook.data.Backup.Part.SETTINGS in parts, backup.skipped))
    }

    private val lastKey = MutableStateFlow<PedalKeyUi?>(null)
    private val learn = PedalLearn()
    // the language shown in the picker: kept here, not read once, because changing the language recreates the
    // Activity and this ViewModel survives it (a value read at creation stayed stale: bug found on the TCL, 31/10)
    private val language = MutableStateFlow(Languages.get(context))
    // input devices connected now (the pedal shows here when connected)
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val devices = MutableStateFlow(connectedDevices())
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) { devices.value = connectedDevices() }
        override fun onInputDeviceRemoved(id: Int) { devices.value = connectedDevices() }
        override fun onInputDeviceChanged(id: Int) { devices.value = connectedDevices() }
    }

    init {
        inputManager?.registerInputDeviceListener(deviceListener, null)
        // leftover from an earlier update (already installed or abandoned)
        viewModelScope.launch(Dispatchers.IO) { updater.clean() }
    }

    override fun onCleared() {
        inputManager?.unregisterInputDeviceListener(deviceListener)
        midi.stopScan()
    }

    private fun connectedDevices(): Set<String> =
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it)?.takeIf(::isExternal)?.name }.toSet()
    private val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private val update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private var updateJob: Job? = null
    private var downloaded: File? = null
    // APK downloaded and checked: the screen opens the Android installer
    private val _install = Channel<Unit>(Channel.CONFLATED)
    val install = _install.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(repository.loaded, repository.library, lastKey, devices, update) { loaded, lib, key, connected, up ->
            // the command comes from the current choice: changing the picker updates what the card shows right away
            val shown = key?.copy(action = PedalAction.of(key.keyCode, lib.look.pedalKeys))
            SettingsUiState(!loaded, lib.look, lib.songs.size, lib.sets.size, shown, version, lib.look.pedalName in connected, up, ownMix = lib.songs.count { it.mix != null })
        },
        midi.state,
        language,
    ) { s, m, lang -> s.copy(midi = m, language = lang) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Saves the language and updates the picker at once; the screen then recreates to apply it. */
    fun setLanguage(lang: AppLanguage) {
        Languages.set(context, lang)
        language.value = lang
    }

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setFont(sp: Float) = repository.updateLook { it.copy(fontSp = sp.coerceIn(Look.MIN_FONT, Look.MAX_FONT)) }
    fun setTextColor(argb: Int) = repository.updateLook { it.copy(fg = argb) }
    fun setBackground(argb: Int) = repository.updateLook { it.copy(bg = argb) }
    fun setBold(on: Boolean) = repository.updateLook { it.copy(bold = on) }
    fun setSectionColor(argb: Int) = repository.updateLook { it.copy(sectionColor = argb) }
    fun setShowSections(on: Boolean) = repository.updateLook { it.copy(showSections = on) }
    fun setTranslateSections(on: Boolean) = repository.updateLook { it.copy(translateSections = on) }
    fun setFontFamily(f: StageFont) = repository.updateLook { it.copy(font = f) }
    fun setChordFont(f: StageFont) = repository.updateLook { it.copy(chordFont = f) }
    fun setInstrument(i: Instrument) = repository.updateLook { it.copy(instrument = i) }
    fun setSites(chords: Boolean, sites: List<ImportSite>) =
        repository.updateLook { if (chords) it.copy(chordSites = sites) else it.copy(lyricSites = sites) }
    fun setMixer(mixer: br.app.stagebook.domain.MixerConfig) = repository.updateLook { it.copy(mixer = mixer) }

    fun setPlayerMode(on: Boolean) {
        repository.updateLook { it.copy(playerMode = on).copy(essentialAlertOff = false) }
        if (!on) br.app.stagebook.player.StagePlayer.turnOff()   // when off, nothing from the player keeps running
    }
    fun setBandOn(on: Boolean) = repository.updateLook { it.copy(bandOn = on).copy(essentialAlertOff = false) }
    fun setInternetOn(on: Boolean) = repository.updateLook { it.copy(internetOn = on).copy(essentialAlertOff = false) }
    fun setExtrasOn(on: Boolean) = repository.updateLook { it.copy(extrasOn = on).copy(essentialAlertOff = false) }

    // touching any part clears "Don't show again": the alert on open comes back with the new list
    /**
     * Turning on turns off all four (player included); turning off brings back band, internet and extras (the player
     * stays as it was: it starts off).
     */
    fun setEssential(on: Boolean) {
        repository.updateLook {
            // turning it off brings every part back, the player too (user, 31/10: it stayed off)
            it.copy(bandOn = !on, internetOn = !on, extrasOn = !on, playerMode = !on)
                .copy(essentialAlertOff = false, webEditorOn = if (on) false else it.webEditorOn)
        }
        if (on) br.app.stagebook.player.StagePlayer.turnOff()
    }
    fun setChordTap(on: Boolean) = repository.updateLook { it.copy(chordTap = on) }
    fun setWebEditorOn(on: Boolean) = repository.updateLook { it.copy(webEditorOn = on) }
    fun setChordList(p: ChordListPlace) = repository.updateLook { it.copy(chordList = p) }
    fun setChords(on: Boolean) = repository.updateLook { it.copy(chords = on) }
    fun setSectionScale(s: Float) = repository.updateLook { it.copy(sectionScale = s.coerceIn(Look.MIN_SECTION_SCALE, Look.MAX_SECTION_SCALE)) }
    fun setChordScale(s: Float) = repository.updateLook { it.copy(chordScale = s.coerceIn(Look.MIN_CHORD_SCALE, Look.MAX_CHORD_SCALE)) }
    fun setChordColor(argb: Int) = repository.updateLook { it.copy(chordColor = argb) }
    fun setColumns(c: StageColumns) = repository.updateLook { it.copy(columns = c) }
    fun setMetronome(count: Boolean, sound: Boolean, glow: Boolean, glowColor: Int) =
        repository.updateLook { it.copy(metroCount = count, metroSound = sound, metroGlow = glow, metroGlowColor = glowColor) }
    fun setMetronomeShown(on: Boolean) = repository.updateLook { it.copy(metroShown = on) }
    fun autoTags(national: String, international: String, language: String, preview: Boolean) = repository.autoTags(national, international, language, preview)
    fun setAnnounce(on: Boolean) = repository.updateLook { it.copy(announce = on) }
    fun setChordPlay(m: br.app.stagebook.data.model.ChordPlayMode) = repository.updateLook { it.copy(chordPlay = m) }
    fun setAutoScroll(level: Int) = repository.updateLook { it.copy(autoScroll = level.coerceIn(1, Look.MAX_AUTO_SCROLL)) }
    fun setStageMode(m: br.app.stagebook.data.model.StageMode) = repository.updateLook { it.copy(stageMode = m) }
    fun setKeepScreenOn(on: Boolean) = repository.updateLook { it.copy(keepScreenOn = on) }
    fun setTrackStops(on: Boolean) = repository.updateLook { it.copy(trackStops = on) }

    fun checkUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            update.value = UpdateState.Checking
            val p = updater.published(maxAgeMs = 0)   // the user asked: read now
            update.value = when {
                p == null -> UpdateState.Offline
                p.versionCode > updater.installedCode -> UpdateState.Available(p.versionName)
                else -> UpdateState.UpToDate
            }
        }
    }

    fun downloadUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            update.value = UpdateState.Downloading(0)
            update.value = try {
                downloaded = updater.download { update.value = UpdateState.Downloading(it) }
                _install.trySend(Unit)
                UpdateState.Ready
            } catch (e: IOException) {
                Log.w("stagebook", "update download failed", e)
                UpdateState.DownloadFailed
            }
        }
    }

    /** The Android installer with the downloaded APK, or null if not downloaded yet. */
    fun installIntent(): Intent? = downloaded?.let(updater::installIntent)

    fun canInstall() = updater.canInstall()

    fun normalizeSections() {
        viewModelScope.launch {
            val r = repository.normalizeSections(SectionLang.PT)
            _messages.send(SettingsMessage.Normalized(r.changed, r.unidentified))
        }
    }

    /**
     * Pedal test: shows the button received, what it does on stage and which device it came from (and saves the name).
     */
    fun onKey(p: PedalPress) {
        val code = learn.code(p, System.currentTimeMillis()) ?: return
        lastKey.value = PedalKeyUi(p.name, PedalAction.NONE, p.device, code)
        // the saved name is the keyboard pedal's ("connected" comes from the keyboard list); MIDI has its own card
        if (!MidiCode.isMidi(p.code) && p.device.isNotEmpty() && p.device != repository.library.value.look.pedalName) {
            repository.updateLook { it.copy(pedalName = p.device) }
        }
    }

    /** Sets the last button pressed to a stage command. */
    fun setPedalAction(keyCode: Int, action: PedalAction) =
        repository.updateLook { it.copy(pedalKeys = PedalAction.assign(it.pedalKeys, keyCode, action)) }

    fun resetPedalKeys() = repository.updateLook { it.copy(pedalKeys = emptyMap()) }

    fun midiPermissions() = midi.permissions()
    fun hasMidiPermissions() = midi.hasPermissions()
    fun scanMidi() = midi.scan()
    fun chooseMidi(found: MidiFound) = midi.choose(found)
    fun forgetMidi() = midi.forget()
    fun setMidiUsb(on: Boolean) = repository.updateLook { it.copy(midiUsb = on) }

    /** Import waiting for ok: it would replace the lyrics of songs that already exist. */
    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            json to withContext(Dispatchers.Default) { repository.previewImport(json) }
        }
        val preview = result.getOrNull()
        when {
            result.exceptionOrNull() is br.app.stagebook.data.LibraryCodec.NewerFileException -> _messages.send(SettingsMessage.ImportNewer)
            preview == null -> _messages.send(SettingsMessage.ImportFailed)
            // an old file would replace lyrics edited after it: ask first
            preview.second.lyricsChanged > 0 -> _pendingImport.value = PendingImport(preview.first, preview.second.added, preview.second.lyricsChanged)
            else -> apply(preview.first)
        }
    }

    fun confirmImport() {
        val p = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch { apply(p.json) }
    }

    fun cancelImport() { _pendingImport.value = null }

    private suspend fun apply(json: String) {
        // reading and merging ~200 songs is heavy on the Fire: off the main thread
        val msg = runCatching { withContext(Dispatchers.Default) { repository.importSongs(json) }.let { SettingsMessage.Imported(it.added, it.replaced) } }
            .getOrElse { SettingsMessage.ImportFailed }
        _messages.send(msg)
    }
}

