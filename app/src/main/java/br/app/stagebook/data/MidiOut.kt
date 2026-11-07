package br.app.stagebook.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Immutable
import br.app.stagebook.di.ApplicationScope
import br.app.stagebook.domain.MidiBytes
import br.app.stagebook.domain.MidiMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A MIDI device the app can send to, as Settings lists it; [key] is what the library saves. */
@Immutable
data class MidiOutput(val key: String, val name: String, val bluetooth: Boolean)

/**
 * The MIDI output chosen in Settings (user, 07/11): the songs' messages on entering and leaving the stage go there, by
 * USB or Bluetooth (a Bluetooth device shows up once it's connected, as the pedal does). Opened on the first message
 * and kept while the device is there: nothing runs while no song sends anything.
 */
@Singleton
class MidiOut @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val manager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) context.getSystemService(MidiManager::class.java) else null
    private val main = Handler(Looper.getMainLooper())
    private val lock = Mutex()
    private var device: MidiDevice? = null
    private var port: MidiInputPort? = null
    private var openKey = ""
    private var watching = false

    val supported get() = manager != null

    private val removed = object : MidiManager.DeviceCallback() {
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            scope.launch { lock.withLock { if (device?.info?.id == info.id) closeOpen() } }
        }
    }

    /** The devices that take MIDI in, right now. A mixer or sound card on USB isn't one: its MIDI took the player's
     *  interface (B-34). The app's own virtual devices aren't either. */
    fun outputs(): List<MidiOutput> {
        val m = manager ?: return emptyList()
        @Suppress("DEPRECATION")
        return m.devices.filter { usable(it) }.map { MidiOutput(keyOf(it), nameOf(it), it.type == MidiDeviceInfo.TYPE_BLUETOOTH) }
            .distinctBy { it.key }
    }

    /** Sends [messages] in order to the chosen output; a note is released [MidiBytes.NOTE_MS] later. Errors only log. */
    fun send(messages: List<MidiMessage>) {
        if (messages.isEmpty() || manager == null) return
        val key = repository.library.value.look.midiOut
        if (key.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                val p = portFor(key) ?: return@withLock
                runCatching {
                    messages.forEach { p.send(MidiBytes.bytes(it), 0, MidiBytes.bytes(it).size) }
                    val releases = messages.mapNotNull(MidiBytes::release)
                    if (releases.isNotEmpty()) {
                        delay(MidiBytes.NOTE_MS)
                        releases.forEach { p.send(it, 0, it.size) }
                    }
                }.onFailure { Log.w(TAG, "MIDI send failed: $it"); closeOpen() }
            }
        }
    }

    private suspend fun portFor(key: String): MidiInputPort? {
        if (openKey == key) port?.let { return it }
        closeOpen()
        val m = manager ?: return null
        @Suppress("DEPRECATION")
        val info = m.devices.firstOrNull { usable(it) && keyOf(it) == key } ?: return null
        if (!watching) { m.registerDeviceCallback(removed, main); watching = true }
        val opened = withTimeoutOrNull(OPEN_TIMEOUT_MS) {
            suspendCancellableCoroutine<MidiDevice?> { c -> m.openDevice(info, { c.resume(it) }, main) }
        } ?: return null
        val number = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }?.portNumber
        val p = number?.let { opened.openInputPort(it) }
        if (p == null) { runCatching { opened.close() }; return null }
        device = opened; port = p; openKey = key
        return p
    }

    private val stage = br.app.stagebook.domain.StageMidi(::send)

    /** A song showed on stage ([id] null: none): the one before sends its exit, then this one its entry. */
    fun songOnStage(id: String?, midi: br.app.stagebook.domain.SongMidi?) = stage.show(id, midi)

    /** The stage closed: the last song sends its exit. */
    fun leftStage() = stage.leave()

    private fun closeOpen() {
        runCatching { port?.close() }
        runCatching { device?.close() }
        port = null; device = null; openKey = ""
    }

    private fun usable(info: MidiDeviceInfo): Boolean {
        if (info.inputPortCount == 0) return false
        if (info.type == MidiDeviceInfo.TYPE_VIRTUAL && info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) == context.packageName) return false
        if (info.type == MidiDeviceInfo.TYPE_USB) {
            @Suppress("DEPRECATION")
            val usb = info.properties.getParcelable<android.hardware.usb.UsbDevice>(MidiDeviceInfo.PROPERTY_USB_DEVICE)
            if (usb != null && hasAudioStreaming(usb.interfaceKinds())) return false
        }
        return true
    }

    // the same device keeps its key across plugs and launches: the type and its name (Android ids change)
    private fun keyOf(info: MidiDeviceInfo) = "${info.type}:${nameOf(info)}"

    // Android gives Bluetooth MIDI no name: the one the pedal search saved
    private fun nameOf(info: MidiDeviceInfo): String =
        info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)?.takeIf { it.isNotBlank() }
            ?: if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH) repository.library.value.look.midiBtName else "MIDI"

    private companion object {
        const val TAG = "stagebook"
        const val OPEN_TIMEOUT_MS = 3000L
    }
}
