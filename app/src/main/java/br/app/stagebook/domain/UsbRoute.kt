package br.app.stagebook.domain

import java.util.Locale

/**
 * Which way the player reaches a USB audio interface: Stagebook's own engine talking to USB directly, or Android's audio.
 * Each way works on some (interface, device) pairs and not on others, so the choice is remembered per pair (user, 09/11).
 * A choice the person made in Settings always wins. Without one, the engine is the default (or the old global switch,
 * for libraries that had it on); when the way in use keeps failing, the other way is tried once and remembered.
 * Pure rules: the service counts the failures ([Trouble]) and the app keeps the choices.
 */
object UsbRoute {
    enum class Way { ENGINE, ANDROID }

    /** A remembered way; [manual] = the person chose it in Settings, otherwise the app switched after a failure. */
    data class Choice(val way: Way, val manual: Boolean)

    /** Open attempts in a row that fail before the way counts as not working. */
    const val OPEN_FAILS = 3

    /**
     * Stream drops within [DROP_WINDOW_MS] before the way counts as not working. Above the three halvings a stalled
     * engine queue goes through on its own (64 to 8 ms), each of which is one drop and is the engine adapting.
     */
    const val DROP_LIMIT = 5
    const val DROP_WINDOW_MS = 120_000L

    /** The key of an interface on this device: the device's own name and the interface's vendor and product ids. */
    @JvmStatic
    fun key(device: String, vendorId: Int, productId: Int): String =
        String.format(Locale.ROOT, "%s|usb:%04x:%04x", device, vendorId, productId)

    @JvmStatic
    fun encode(c: Choice): String = (if (c.way == Way.ANDROID) ANDROID else ENGINE) + ":" + (if (c.manual) MANUAL else AUTO)

    /** Null for nothing saved or anything unreadable (a value from a future version): the default applies. */
    @JvmStatic
    fun decode(s: String?): Choice? {
        val parts = s?.split(':') ?: return null
        if (parts.size != 2) return null
        val way = when (parts[0]) { ANDROID -> Way.ANDROID; ENGINE -> Way.ENGINE; else -> return null }
        val manual = when (parts[1]) { MANUAL -> true; AUTO -> false; else -> return null }
        return Choice(way, manual)
    }

    /** The way to open: the remembered one, or the default. */
    @JvmStatic
    fun way(remembered: Choice?, androidDefault: Boolean): Way =
        remembered?.way ?: if (androidDefault) Way.ANDROID else Way.ENGINE

    /**
     * The way in use failed: the choice to remember, or null to stay. Only an interface with nothing remembered moves:
     * a manual choice is the person's, and an automatic one means the other way already failed here (no ping-pong).
     */
    @JvmStatic
    fun fallback(remembered: Choice?, failed: Way): Choice? =
        if (remembered != null) null else Choice(if (failed == Way.ENGINE) Way.ANDROID else Way.ENGINE, manual = false)

    /**
     * Failures of the way in use, for one interface while it stays plugged in: a successful open clears the failed
     * opens, a new cable or a new way clears everything.
     */
    class Trouble {
        private var fails = 0
        private val drops = ArrayDeque<Long>()

        fun opened() { fails = 0 }

        /** One more failed open: true when the way counts as not working. */
        fun openFailed(): Boolean = ++fails >= OPEN_FAILS

        /** The open stream dropped at [nowMs]: true when the way counts as not working. */
        fun dropped(nowMs: Long): Boolean {
            drops.addLast(nowMs)
            while (nowMs - drops.first() > DROP_WINDOW_MS) drops.removeFirst()
            return drops.size >= DROP_LIMIT
        }

        fun reset() {
            fails = 0
            drops.clear()
        }
    }

    // saved values: never renamed
    private const val ANDROID = "android"
    private const val ENGINE = "engine"
    private const val MANUAL = "manual"
    private const val AUTO = "auto"
}
