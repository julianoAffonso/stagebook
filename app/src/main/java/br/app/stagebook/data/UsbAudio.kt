package br.app.stagebook.data

import android.hardware.usb.UsbDevice

/** (class, subclass, protocol) of each interface of a USB device. */
fun UsbDevice.interfaceKinds(): List<Triple<Int, Int, Int>> =
    (0 until interfaceCount).map { getInterface(it).run { Triple(interfaceClass, interfaceSubclass, interfaceProtocol) } }

/** A USB Audio 2.0 interface (like a USB mixer): class 1, subclass 2 (streaming), protocol 0x20, the kind the player speaks to. */
fun isUsbAudio(kinds: List<Triple<Int, Int, Int>>): Boolean = kinds.any { it == Triple(1, 2, 0x20) }

/**
 * Something that plays audio (any audio streaming interface): a mixer or sound card, never a pedal, even when it also
 * has MIDI, like many mixers (device test B-34: "MIDI pedal over USB" opened the mixer as a pedal).
 */
fun hasAudioStreaming(kinds: List<Triple<Int, Int, Int>>): Boolean = kinds.any { it.first == 1 && it.second == 2 }
