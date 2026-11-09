package br.app.stagebook.domain

import java.io.File

/**
 * The USB audio interface is powering this device: the device is the USB host but receives power. Some interfaces
 * that charge the device behave worse on the direct engine (one tablet read the charge as a charger and never offered
 * the interface as audio), so Settings warns. Public APIs first: the battery's plug type while an interface is
 * attached. The kernel's Type-C directory is a read-only extra: where readable it is more precise and wins. Where
 * nothing is readable, it never warns.
 */
object UsbPower {
    const val TYPEC_DIR = "/sys/class/typec"

    // BatteryManager.EXTRA_PLUGGED values (the same numbers, kept here so the rules stay pure)
    const val PLUGGED_AC = 1
    const val PLUGGED_USB = 2

    enum class Port {
        /** This device doesn't let the app read its ports. */
        UNKNOWN,

        /** Readable, and no port is a host receiving power with a partner attached. */
        NOT_POWERED,

        /** A port is the host, receives power ("[sink]") and has a partner attached. */
        POWERED_HOST,
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
            val data = read(File(p, "data_role")) ?: continue
            val power = read(File(p, "power_role")) ?: continue
            readable = true
            // the active role is the one in brackets: "[host] device", "source [sink]"
            if (data.contains("[host]") && power.contains("[sink]") && File(dir, p.name + "-partner").exists()) return Port.POWERED_HOST
        }
        return if (readable) Port.NOT_POWERED else Port.UNKNOWN
    }

    /**
     * Warn? Only with a USB audio interface attached. A readable port decides; otherwise a battery charging from a wired
     * plug (AC or USB, as phones report USB-C power both ways) while the interface is attached means the power comes
     * through the USB chain. Wireless, dock or unknown (-1) never warn.
     */
    @JvmStatic
    fun warn(audioAttached: Boolean, plugged: Int, port: Port): Boolean = audioAttached && when (port) {
        Port.POWERED_HOST -> true
        Port.NOT_POWERED -> false
        Port.UNKNOWN -> plugged == PLUGGED_AC || plugged == PLUGGED_USB
    }

    private fun read(f: File): String? = try {
        f.readText()
    } catch (e: java.io.IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private val PORT = Regex("port\\d+")
}
