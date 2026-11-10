package br.app.stagebook.domain

/**
 * Where a metronome click starts. The stage and the Settings test take this same path (user, 10/11: the test on a USB
 * output stayed silent until a song played on stage, because only the stage turned the player on). The steps run in
 * order and stop at the first that takes the click: [usbNow] (the player is on the interface), [wake] (its stream was
 * closed for being idle), [connect] (the interface is plugged in: the player turns on for the click).
 */
object ClickPath {
    enum class Way { USB, WAKE, CONNECT, DEVICE }

    fun choose(playerMode: Boolean, out: MetronomeOut, usbNow: () -> Boolean, wake: () -> Boolean, connect: () -> Boolean): Way = when {
        !playerMode -> Way.DEVICE
        usbNow() -> Way.USB
        wake() -> Way.WAKE
        !out.device && connect() -> Way.CONNECT
        else -> Way.DEVICE
    }
}
