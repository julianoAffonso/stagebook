package br.app.stagebook.domain

import androidx.compose.runtime.Immutable

/**
 * How much of the output the device's USB really delivers to an interface (Engine.hostDelivery, in %). Measured on the
 * user's interfaces (10/11): every clean one gave 99.9% or more; the Chuwi with an 18-channel desk gave 98.4% (96 to 99.5%
 * over a song) and the sound broke. Below [MIN] the app warns: using it is at the musician's own risk.
 */
object HostDelivery {
    const val MIN = 99.7f
    const val MANY_CHANNELS = 10   // from here the warning says the channels are what weighs on the USB
    private const val WORSE_BY = 0.3f   // a later reading this much lower warns again

    /** Warn about [measured] (0 = not measured), given the reading already warned about on this device ([warned]; null = none)? */
    fun warns(measured: Float, warned: Float?): Boolean =
        measured > 0f && measured < MIN && (warned == null || measured < warned - WORSE_BY)
}

/** The warning on screen: the interface [name] lost [lossPct]% of its packets; [channels] tells whether to blame them. */
@Immutable
data class HostWarning(val name: String, val lossPct: Float, val channels: Int)
