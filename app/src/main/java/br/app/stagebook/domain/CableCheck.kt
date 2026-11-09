package br.app.stagebook.domain

import java.io.File

/**
 * A cable that is plugged in but never enumerates (seen on the TCL and the Chuwi with a USB-C cable that carried power and
 * no data): the Type-C port says a partner is attached with this phone as the host, yet Android lists no USB device.
 * That is not the same as no interface at all, and the fix is different (unplug and plug the cable, turning the
 * connector over, or power the interface off and on). Android has no public API for the port state, so this reads the
 * kernel's Type-C directory; a phone that doesn't let the app read it just never warns.
 */
object CableCheck {
    const val TYPEC_DIR = "/sys/class/typec"

    /** Checks in a row that must agree before the warning shows: an interface takes a second or two to enumerate. */
    const val HITS_TO_WARN = 2
    const val CHECK_MS = 4_000L

    enum class Port {
        /** This phone doesn't let us read the port: no warning, no more checks. */
        UNKNOWN,

        /** Nothing attached, or attached with this phone as the device. */
        NO_HOST_PARTNER,

        /** The phone is the host and a partner is attached. */
        HOST_PARTNER,
    }

    @JvmStatic
    @JvmOverloads
    fun port(dir: File = File(TYPEC_DIR)): Port {
        val ports = try {
            dir.listFiles { f -> PORT.matches(f.name) }
        } catch (e: SecurityException) {
            null
        } ?: return Port.UNKNOWN
        var readable = false
        for (p in ports) {
            val role = try {
                File(p, "data_role").readText()
            } catch (e: java.io.IOException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
            readable = true
            // "[host] device" when the phone is the host, "host [device]" otherwise
            if (role.contains("[host]") && File(dir, p.name + "-partner").exists()) return Port.HOST_PARTNER
        }
        return if (readable) Port.NO_HOST_PARTNER else Port.UNKNOWN
    }

    /** One more agreeing check, or back to zero: the port has a partner, no USB device is listed. */
    @JvmStatic
    fun hits(before: Int, port: Port, devicesListed: Int): Int =
        if (port == Port.HOST_PARTNER && devicesListed == 0) before + 1 else 0

    @JvmStatic
    fun warn(hits: Int): Boolean = hits >= HITS_TO_WARN

    private val PORT = Regex("port\\d+")
}
