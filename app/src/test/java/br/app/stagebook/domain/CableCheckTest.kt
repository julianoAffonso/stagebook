package br.app.stagebook.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Type-C port says a host partner is attached, yet no USB device is listed: the cable carries no data. */
class CableCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun port(name: String, role: String, partner: Boolean): File {
        val root = tmp.root
        File(root, name).mkdirs()
        File(root, "$name/data_role").writeText(role)
        if (partner) File(root, "$name-partner").mkdirs()
        return root
    }

    @Test fun `host with a partner is a host partner`() {
        assertEquals(CableCheck.Port.HOST_PARTNER, CableCheck.port(port("port0", "[host] device\n", true)))
    }

    @Test fun `host without a partner is nothing attached`() {
        assertEquals(CableCheck.Port.NO_HOST_PARTNER, CableCheck.port(port("port0", "[host] device\n", false)))
    }

    @Test fun `a partner with the phone as the device is not a host partner`() {
        assertEquals(CableCheck.Port.NO_HOST_PARTNER, CableCheck.port(port("port0", "host [device]\n", true)))
    }

    @Test fun `no typec directory is unknown`() {
        assertEquals(CableCheck.Port.UNKNOWN, CableCheck.port(File(tmp.root, "missing")))
    }

    @Test fun `ports without a readable role are unknown`() {
        File(tmp.root, "port0").mkdirs()
        assertEquals(CableCheck.Port.UNKNOWN, CableCheck.port(tmp.root))
    }

    @Test fun `the second port can be the one with the cable`() {
        port("port0", "host [device]\n", false)
        assertEquals(CableCheck.Port.HOST_PARTNER, CableCheck.port(port("port1", "[host] device\n", true)))
    }

    @Test fun `a listed device resets the count`() {
        assertEquals(2, CableCheck.hits(1, CableCheck.Port.HOST_PARTNER, 0))
        assertEquals(0, CableCheck.hits(1, CableCheck.Port.HOST_PARTNER, 1))
        assertEquals(0, CableCheck.hits(1, CableCheck.Port.NO_HOST_PARTNER, 0))
    }

    @Test fun `it warns only after two agreeing checks`() {
        assertFalse(CableCheck.warn(1))
        assertTrue(CableCheck.warn(2))
    }
}
