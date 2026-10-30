package br.app.stagebook.data

import kotlinx.coroutines.flow.first
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import br.app.stagebook.di.ApplicationScope
import br.app.stagebook.domain.MidiDecoder
import br.app.stagebook.domain.PedalPress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class MidiDeviceUi(val name: String, val bluetooth: Boolean)

@Immutable
data class MidiFound(val name: String, val address: String)

@Immutable
data class MidiState(
    val supported: Boolean = true,
    val connected: List<MidiDeviceUi> = emptyList(),
    val scanning: Boolean = false,
    val found: List<MidiFound> = emptyList(),
    val bluetoothOff: Boolean = false,
    val searched: Boolean = false,   // already searched in this visit to settings (to say "nothing found")
)

/**
 * MIDI pedal, like another app: a USB device comes in by itself when plugged in; Bluetooth (BLE MIDI) is searched once
 * in Settings, saved, and the app reconnects by itself when it comes back (or drops during the show). Each message
 * becomes a [PedalPress] with a MIDI code, and the command comes from the choice made in settings.
 */
@Singleton
class MidiPedal @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val manager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) context.getSystemService(MidiManager::class.java) else null
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _presses = MutableSharedFlow<PedalPress>(extraBufferCapacity = 64)
    /** Presses coming from MIDI (they arrive on the MIDI thread; the collector passes them to the screen). */
    val presses: SharedFlow<PedalPress> = _presses

    private val _state = MutableStateFlow(MidiState(supported = manager != null))
    val state: StateFlow<MidiState> = _state.asStateFlow()

    // open devices, by Android id; the ports stay open while the device exists
    private val open = HashMap<Int, MidiDevice>()
    private val lock = Any()
    private var started = false
    @Volatile private var usbOn = false
    private var reconnect: Job? = null
    // the reconnect loop only tries with the app in front: closed or with the screen off for hours, it waits (audit
    // 30/10: it called Bluetooth every 30 s forever)
    private val inFront = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun setForeground(on: Boolean) { inFront.value = on }
    // attempt in progress: Android can take up to ~30 s to give up on an out-of-range pedal
    @Volatile private var lastTry = 0L
    private var scanStop: Job? = null

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            // USB only, and only with the switch on; Bluetooth is opened by openBluetoothDevice (it shows up here too,
            // and would open twice) and virtual ones belong to other apps
            if (info.type == MidiDeviceInfo.TYPE_USB && usbOn) openDevice(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            synchronized(lock) { open.remove(info.id) }?.let { runCatching { it.close() } }
            publish()
        }
    }

    /** Once, on app open: listens for USB and reconnects the saved Bluetooth pedal. */
    fun start() {
        val m = manager ?: return
        if (started) return
        started = true
        m.registerDeviceCallback(callback, null)
        scope.launch {
            repository.library.map { it.look.midiUsb }.distinctUntilChanged().collect(::setUsb)
        }
        scope.launch {
            repository.library.map { it.look.midiBtAddress }.distinctUntilChanged().collect { keepConnected(it) }
        }
    }

    /** On: opens USB pedals already plugged in. Off: closes them all and doesn't open new arrivals. */
    private fun setUsb(on: Boolean) {
        val m = manager ?: return
        usbOn = on
        if (on) {
            @Suppress("DEPRECATION")
            m.devices.filter { it.type == MidiDeviceInfo.TYPE_USB }.forEach(::openDevice)
        } else {
            synchronized(lock) { open.filterValues { it.info.type == MidiDeviceInfo.TYPE_USB }.keys.toList().map { open.remove(it) } }
                .forEach { runCatching { it?.close() } }
            publish()
        }
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val m = manager ?: return
        if (synchronized(lock) { info.id in open }) return
        m.openDevice(info, { device -> attach(device) }, null)
    }

    private fun attach(device: MidiDevice?) {
        device ?: return
        val info = device.info
        // the switch was turned off while USB was opening
        if (info.type == MidiDeviceInfo.TYPE_USB && !usbOn) { runCatching { device.close() }; return }
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty()
        // the device's OUTPUT port = what it sends to the tablet
        info.ports.filter { it.type == android.media.midi.MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.forEach { port ->
            val decoder = MidiDecoder(name)
            device.openOutputPort(port.portNumber)?.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    // the decoder is per port, and the port delivers in order on a single thread
                    decoder.feed(msg, offset, count).forEach { _presses.tryEmit(it) }
                }
            })
        }
        val old = synchronized(lock) { open.put(info.id, device) }
        if (old != null && old !== device) runCatching { old.close() }
        publish()
    }

    private fun publish() {
        val list = synchronized(lock) { open.values.toList() }.map {
            MidiDeviceUi(it.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty(), it.info.type == MidiDeviceInfo.TYPE_BLUETOOTH)
        }
        _state.value = _state.value.copy(connected = list)
    }

    private fun btConnected() = synchronized(lock) { open.values.any { it.info.type == MidiDeviceInfo.TYPE_BLUETOOTH } }

    /** While there's a saved pedal: if it isn't open, try again every so often (turned off, out of range). */
    private fun keepConnected(address: String) {
        reconnect?.cancel()
        if (address.isEmpty()) {
            synchronized(lock) { open.filterValues { it.info.type == MidiDeviceInfo.TYPE_BLUETOOTH }.keys.toList().map { open.remove(it) } }
                .forEach { runCatching { it?.close() } }
            publish()
            return
        }
        reconnect = scope.launch {
            while (true) {
                inFront.first { it }
                val now = System.currentTimeMillis()
                if (!btConnected() && canConnect() && now - lastTry > CONNECT_TIMEOUT_MS) {
                    lastTry = now
                    connectBluetooth(address)
                }
                delay(RETRY_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")   // canConnect() checks first
    private fun connectBluetooth(address: String) {
        val m = manager ?: return
        val adapter = bluetooth ?: return
        if (!adapter.isEnabled) return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        try {
            m.openBluetoothDevice(device, { if (it == null) lastTry = 0 else attach(it) }, null)
        } catch (e: SecurityException) {
            Log.w("stagebook", "MIDI pedal: no Bluetooth permission", e)
        }
    }

    // ---- procurar (Ajustes) ----

    /** Permissions the Bluetooth search and connection ask for on this Android version. */
    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)   // Android 9 to 11 (the Fire): BLE search requires location

    fun hasPermissions() = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun canConnect() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else true

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = runCatching { d.name }.getOrNull() ?: result.scanRecord?.deviceName ?: d.address
            val s = _state.value
            if (s.found.none { it.address == d.address }) _state.value = s.copy(found = s.found + MidiFound(name, d.address))
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w("stagebook", "MIDI pedal: scan failed ($errorCode)")
            _state.value = _state.value.copy(scanning = false)
        }
    }

    /** Searches for BLE MIDI pedals for a few seconds (only the ones advertising the MIDI service). */
    @SuppressLint("MissingPermission")
    fun scan() {
        val adapter = bluetooth
        if (adapter == null || !adapter.isEnabled) { _state.value = _state.value.copy(bluetoothOff = true); return }
        val scanner = adapter.bluetoothLeScanner ?: return
        if (!hasPermissions() || _state.value.scanning) return
        _state.value = _state.value.copy(scanning = true, found = emptyList(), bluetoothOff = false, searched = true)
        try {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(MIDI_SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
        } catch (e: SecurityException) {
            Log.w("stagebook", "MIDI pedal: no permission to scan", e)
            _state.value = _state.value.copy(scanning = false)
            return
        }
        scanStop?.cancel()
        scanStop = scope.launch { delay(SCAN_MS); stopScan() }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanStop?.cancel()
        if (!_state.value.scanning) return
        runCatching { bluetooth?.bluetoothLeScanner?.stopScan(scanCallback) }
        _state.value = _state.value.copy(scanning = false)
    }

    /** Saves the chosen pedal; the connection (and reconnections) come from [keepConnected]. */
    fun choose(found: MidiFound) {
        stopScan()
        repository.updateLook { it.copy(midiBtAddress = found.address, midiBtName = found.name) }
    }

    fun forget() = repository.updateLook { it.copy(midiBtAddress = "", midiBtName = "") }

    private companion object {
        val MIDI_SERVICE: java.util.UUID = java.util.UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        const val SCAN_MS = 15_000L
        const val RETRY_MS = 8_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
