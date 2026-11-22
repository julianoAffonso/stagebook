package br.app.stagebook.data

import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler

/**
 * Hears MIDI devices come and go on [handler]'s thread. Android 13 asks for the transport: byte-stream MIDI, the same
 * devices the older call reported.
 */
internal fun MidiManager.watchDevices(callback: MidiManager.DeviceCallback, handler: Handler) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, { handler.post(it) }, callback)
    } else {
        @Suppress("DEPRECATION")
        registerDeviceCallback(callback, handler)
    }
}
