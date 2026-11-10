package br.app.stagebook

import br.app.stagebook.domain.ClickPath
import br.app.stagebook.domain.ClickPath.Way
import br.app.stagebook.domain.MetronomeOut
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stage and the Settings test start a click the same way (user, 10/11: the test on USB stayed silent). */
class ClickPathTest {
    private val usb = MetronomeOut(8, true)

    @Test fun `a USB output with the player off turns the player on - the test used to fall to the device and stay silent`() {
        val tried = mutableListOf<String>()
        val way = ClickPath.choose(true, usb, { tried += "usb"; false }, { tried += "wake"; false }, { tried += "connect"; true })
        assertEquals(Way.CONNECT, way)
        assertEquals(listOf("usb", "wake", "connect"), tried)
    }

    @Test fun `the player on the interface takes it right away`() {
        assertEquals(Way.USB, ClickPath.choose(true, usb, { true }, { error("not tried") }, { error("not tried") }))
    }

    @Test fun `an idle stream wakes up`() {
        assertEquals(Way.WAKE, ClickPath.choose(true, usb, { false }, { true }, { error("not tried") }))
    }

    @Test fun `the device output or no interface plugged in clicks on the device`() {
        assertEquals(Way.DEVICE, ClickPath.choose(true, MetronomeOut.DEVICE, { false }, { false }, { error("never for the device") }))
        assertEquals(Way.DEVICE, ClickPath.choose(true, usb, { false }, { false }, { false }))
        assertEquals(Way.DEVICE, ClickPath.choose(false, usb, { error("player mode off") }, { error("") }, { error("") }))
    }
}
