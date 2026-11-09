package br.app.stagebook

import br.app.stagebook.domain.UsbPower
import br.app.stagebook.domain.UsbPower.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UsbPowerTest {
    @get:Rule val tmp = TemporaryFolder()

    // a fake /sys/class/typec with one port
    private fun typec(data: String?, power: String?, partner: Boolean): File {
        val dir = tmp.newFolder("typec")
        val port = File(dir, "port0").apply { mkdir() }
        data?.let { File(port, "data_role").writeText(it) }
        power?.let { File(port, "power_role").writeText(it) }
        if (partner) File(dir, "port0-partner").mkdir()
        return dir
    }

    @Test fun `host receiving power with a partner is the interface charging the device`() =
        assertEquals(Port.POWERED_HOST, UsbPower.port(typec("[host] device\n", "source [sink]\n", partner = true)))

    @Test fun `host giving power is the usual case`() =
        assertEquals(Port.NOT_POWERED, UsbPower.port(typec("[host] device\n", "[source] sink\n", partner = true)))

    @Test fun `charging from a computer, as the device, is not an interface`() =
        assertEquals(Port.NOT_POWERED, UsbPower.port(typec("host [device]\n", "source [sink]\n", partner = true)))

    @Test fun `no partner attached is not powered by anything`() =
        assertEquals(Port.NOT_POWERED, UsbPower.port(typec("[host] device\n", "source [sink]\n", partner = false)))

    @Test fun `no directory or unreadable roles are unknown`() {
        assertEquals(Port.UNKNOWN, UsbPower.port(File(tmp.root, "missing")))
        assertEquals(Port.UNKNOWN, UsbPower.port(typec(null, null, partner = true)))
    }

    @Test fun `never warns without a USB audio interface`() {
        assertFalse(UsbPower.warn(false, UsbPower.PLUGGED_USB, Port.POWERED_HOST))
        assertFalse(UsbPower.warn(false, UsbPower.PLUGGED_AC, Port.UNKNOWN))
    }

    @Test fun `a readable port decides`() {
        assertTrue(UsbPower.warn(true, 0, Port.POWERED_HOST))
        assertFalse(UsbPower.warn(true, UsbPower.PLUGGED_USB, Port.NOT_POWERED))
    }

    @Test fun `without the port, a wired charge with the interface attached warns`() {
        assertTrue(UsbPower.warn(true, UsbPower.PLUGGED_USB, Port.UNKNOWN))
        assertTrue(UsbPower.warn(true, UsbPower.PLUGGED_AC, Port.UNKNOWN))
    }

    @Test fun `nothing readable, wireless or on battery never warns`() {
        assertFalse(UsbPower.warn(true, -1, Port.UNKNOWN))
        assertFalse(UsbPower.warn(true, 0, Port.UNKNOWN))
        assertFalse(UsbPower.warn(true, 4, Port.UNKNOWN))   // wireless
        assertFalse(UsbPower.warn(true, 8, Port.UNKNOWN))   // dock
    }

    @Test fun `the plug numbers are Android's`() {
        assertEquals(android.os.BatteryManager.BATTERY_PLUGGED_AC, UsbPower.PLUGGED_AC)
        assertEquals(android.os.BatteryManager.BATTERY_PLUGGED_USB, UsbPower.PLUGGED_USB)
    }
}
