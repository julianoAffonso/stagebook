package com.juliano.stagebook.ui.settings

import com.juliano.stagebook.data.model.ImportSite
import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.data.model.ChordListPlace
import com.juliano.stagebook.data.model.StageFont
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
import com.juliano.stagebook.data.AppUpdater
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.SectionLang
import com.juliano.stagebook.data.model.StageColumns
import com.juliano.stagebook.data.AppLanguage
import com.juliano.stagebook.data.Languages
import com.juliano.stagebook.data.MidiFound
import com.juliano.stagebook.data.MidiPedal
import com.juliano.stagebook.data.MidiState
import com.juliano.stagebook.domain.MidiCode
import com.juliano.stagebook.domain.PedalAction
import com.juliano.stagebook.domain.PedalPress
import com.juliano.stagebook.ui.isExternal
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
)

/** Ajustes → Atualizar o app. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionName: String) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Ready : UpdateState            // baixado: falta o toque em "Instalar" do Android
    data object Offline : UpdateState          // não achou a versão publicada (sem internet?)
    data object DownloadFailed : UpdateState
}

class PendingImport(val json: String, val added: Int, val lyricsChanged: Int)

sealed interface SettingsMessage {
    data class Normalized(val count: Int, val unidentified: Int) : SettingsMessage
    data class Imported(val added: Int, val replaced: Int) : SettingsMessage
    data object ImportFailed : SettingsMessage
    data object ImportNewer : SettingsMessage   // arquivo de uma versão mais nova do Stagebook: atualizar
    data object BackupDone : SettingsMessage
    data object BackupFailed : SettingsMessage
    data object NotBackup : SettingsMessage
    data object RestoreFailed : SettingsMessage
    data class Restored(val restart: Boolean) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    @ApplicationContext private val context: Context,
    private val updater: AppUpdater,
    private val midi: MidiPedal,
    private val backup: com.juliano.stagebook.data.Backup,
) : ViewModel() {
    /** Backup lido e esperando a escolha das partes para restaurar. */
    data class PendingRestore(val uri: Uri, val contents: com.juliano.stagebook.data.Backup.Contents)

    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore.asStateFlow()

    fun backupTo(uri: Uri, parts: Set<com.juliano.stagebook.data.Backup.Part>) = viewModelScope.launch {
        val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { backup.write(it, parts) } }.isSuccess
        _messages.send(if (ok) SettingsMessage.BackupDone else SettingsMessage.BackupFailed)
    }

    /** Abrir arquivo: um .stagebook mostra as partes para escolher; um arquivo de músicas antigo (.json) soma as músicas. */
    fun inspectBackup(uri: Uri) = viewModelScope.launch {
        val c = runCatching { context.contentResolver.openInputStream(uri)!!.use { backup.inspect(it) } }.getOrNull()
        when {
            c == null -> importFrom(uri)   // não é .stagebook: tenta o arquivo de músicas antigo (ou avisa que não deu)
            c.newer -> _messages.send(SettingsMessage.ImportNewer)
            else -> _pendingRestore.value = PendingRestore(uri, c)
        }
    }

    fun cancelRestore() { _pendingRestore.value = null }

    fun restore(parts: Set<com.juliano.stagebook.data.Backup.Part>, replace: Boolean) = viewModelScope.launch {
        val p = _pendingRestore.value ?: return@launch
        _pendingRestore.value = null
        val ok = runCatching { context.contentResolver.openInputStream(p.uri)!!.use { backup.restore(it, parts, replace) } }.getOrDefault(false)
        // ajustes restaurados (língua, aparência): a tela abre de novo para valerem
        _messages.send(if (!ok) SettingsMessage.RestoreFailed else SettingsMessage.Restored(com.juliano.stagebook.data.Backup.Part.SETTINGS in parts))
    }

    private val lastKey = MutableStateFlow<PedalKeyUi?>(null)
    // trocar a língua recria a tela (e este ViewModel junto não precisa ouvir mudanças)
    private val language = Languages.get(context)
    // aparelhos de entrada ligados agora (o pedal aparece aqui quando está conectado)
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val devices = MutableStateFlow(connectedDevices())
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) { devices.value = connectedDevices() }
        override fun onInputDeviceRemoved(id: Int) { devices.value = connectedDevices() }
        override fun onInputDeviceChanged(id: Int) { devices.value = connectedDevices() }
    }

    init {
        inputManager?.registerInputDeviceListener(deviceListener, null)
        // sobra de uma atualização anterior (já instalada ou abandonada)
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
    // APK baixado e conferido: a tela abre o instalador do Android
    private val _install = Channel<Unit>(Channel.CONFLATED)
    val install = _install.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(repository.loaded, repository.library, lastKey, devices, update) { loaded, lib, key, connected, up ->
            // o comando sai da escolha atual: trocar no seletor muda na hora o que o cartão mostra
            val shown = key?.copy(action = PedalAction.of(key.keyCode, lib.look.pedalKeys))
            SettingsUiState(!loaded, lib.look, lib.songs.size, lib.sets.size, shown, version, lib.look.pedalName in connected, up)
        },
        midi.state,
    ) { s, m -> s.copy(midi = m, language = language) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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
    fun setMixer(mixer: com.juliano.stagebook.domain.MixerConfig) = repository.updateLook { it.copy(mixer = mixer) }

    fun setPlayerMode(on: Boolean) {
        repository.updateLook { it.copy(playerMode = on) }
        if (!on) com.juliano.stagebook.player.StagePlayer.turnOff()   // desligado, nada do player fica rodando
    }
    fun setChordTap(on: Boolean) = repository.updateLook { it.copy(chordTap = on) }
    fun setChordList(p: ChordListPlace) = repository.updateLook { it.copy(chordList = p) }
    fun setChords(on: Boolean) = repository.updateLook { it.copy(chords = on) }
    fun setSectionScale(s: Float) = repository.updateLook { it.copy(sectionScale = s.coerceIn(Look.MIN_SECTION_SCALE, Look.MAX_SECTION_SCALE)) }
    fun setChordScale(s: Float) = repository.updateLook { it.copy(chordScale = s.coerceIn(Look.MIN_CHORD_SCALE, Look.MAX_CHORD_SCALE)) }
    fun setChordColor(argb: Int) = repository.updateLook { it.copy(chordColor = argb) }
    fun setColumns(c: StageColumns) = repository.updateLook { it.copy(columns = c) }
    fun setAutoScroll(level: Int) = repository.updateLook { it.copy(autoScroll = level.coerceIn(1, Look.MAX_AUTO_SCROLL)) }
    fun setStageMode(m: com.juliano.stagebook.data.model.StageMode) = repository.updateLook { it.copy(stageMode = m) }
    fun setKeepScreenOn(on: Boolean) = repository.updateLook { it.copy(keepScreenOn = on) }

    fun checkUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            update.value = UpdateState.Checking
            val p = updater.published(maxAgeMs = 0)   // o usuário pediu: lê agora
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
                Log.w("stagebook", "atualização não baixou", e)
                UpdateState.DownloadFailed
            }
        }
    }

    /** O instalador do Android com o APK baixado, ou null se ainda não baixou. */
    fun installIntent(): Intent? = downloaded?.let(updater::installIntent)

    fun canInstall() = updater.canInstall()

    fun normalizeSections() {
        viewModelScope.launch {
            val r = repository.normalizeSections(SectionLang.PT)
            _messages.send(SettingsMessage.Normalized(r.changed, r.unidentified))
        }
    }

    /** Teste do pedal: mostra o botão recebido, o que ele faz no palco e de qual aparelho veio (e guarda o nome). */
    fun onKey(p: PedalPress) {
        lastKey.value = PedalKeyUi(p.name, PedalAction.NONE, p.device, p.code)
        // o nome guardado é o do pedal-teclado (o "conectado" vem da lista de teclados); o MIDI tem o cartão dele
        if (!MidiCode.isMidi(p.code) && p.device.isNotEmpty() && p.device != repository.library.value.look.pedalName) {
            repository.updateLook { it.copy(pedalName = p.device) }
        }
    }

    /** Liga o último botão apertado a um comando do palco. */
    fun setPedalAction(keyCode: Int, action: PedalAction) =
        repository.updateLook { it.copy(pedalKeys = PedalAction.assign(it.pedalKeys, keyCode, action)) }

    fun resetPedalKeys() = repository.updateLook { it.copy(pedalKeys = emptyMap()) }

    fun midiPermissions() = midi.permissions()
    fun hasMidiPermissions() = midi.hasPermissions()
    fun scanMidi() = midi.scan()
    fun chooseMidi(found: MidiFound) = midi.choose(found)
    fun forgetMidi() = midi.forget()
    fun setMidiUsb(on: Boolean) = repository.updateLook { it.copy(midiUsb = on) }

    /** Import esperando o ok: ia trocar a letra de músicas que já existem. */
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
            result.exceptionOrNull() is com.juliano.stagebook.data.LibraryCodec.NewerFileException -> _messages.send(SettingsMessage.ImportNewer)
            preview == null -> _messages.send(SettingsMessage.ImportFailed)
            // um arquivo antigo trocaria letras editadas depois dele: pergunta antes
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
        // ler e juntar ~200 músicas pesa no Fire: fora da thread principal
        val msg = runCatching { withContext(Dispatchers.Default) { repository.importSongs(json) }.let { SettingsMessage.Imported(it.added, it.replaced) } }
            .getOrElse { SettingsMessage.ImportFailed }
        _messages.send(msg)
    }
}

