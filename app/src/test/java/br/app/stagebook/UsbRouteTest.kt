package br.app.stagebook

import br.app.stagebook.domain.UsbRoute
import br.app.stagebook.domain.UsbRoute.Choice
import br.app.stagebook.domain.UsbRoute.Way
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbRouteTest {
    @Test fun `nothing remembered opens the engine`() = assertEquals(Way.ENGINE, UsbRoute.way(null, androidDefault = false))

    @Test fun `the old global switch still counts for interfaces with nothing remembered`() =
        assertEquals(Way.ANDROID, UsbRoute.way(null, androidDefault = true))

    @Test fun `a remembered way wins over the default`() {
        assertEquals(Way.ENGINE, UsbRoute.way(Choice(Way.ENGINE, manual = true), androidDefault = true))
        assertEquals(Way.ANDROID, UsbRoute.way(Choice(Way.ANDROID, manual = false), androidDefault = false))
    }

    @Test fun `the engine failing with nothing remembered switches to Android, as the app's choice`() =
        assertEquals(Choice(Way.ANDROID, manual = false), UsbRoute.fallback(null, Way.ENGINE))

    @Test fun `Android failing with nothing remembered switches to the engine`() =
        assertEquals(Choice(Way.ENGINE, manual = false), UsbRoute.fallback(null, Way.ANDROID))

    @Test fun `a way chosen in Settings never switches by itself`() {
        assertNull(UsbRoute.fallback(Choice(Way.ENGINE, manual = true), Way.ENGINE))
        assertNull(UsbRoute.fallback(Choice(Way.ANDROID, manual = true), Way.ANDROID))
    }

    @Test fun `both ways failing stays put instead of going back and forth`() {
        val afterEngineFailed = UsbRoute.fallback(null, Way.ENGINE)
        assertNull(UsbRoute.fallback(afterEngineFailed, Way.ANDROID))
    }

    @Test fun `saved choices read back, and anything unknown is nothing saved`() {
        for (c in listOf(Choice(Way.ENGINE, true), Choice(Way.ENGINE, false), Choice(Way.ANDROID, true), Choice(Way.ANDROID, false))) {
            assertEquals(c, UsbRoute.decode(UsbRoute.encode(c)))
        }
        assertNull(UsbRoute.decode(null))
        assertNull(UsbRoute.decode(""))
        assertNull(UsbRoute.decode("bluetooth:auto"))
        assertNull(UsbRoute.decode("android:maybe"))
        assertNull(UsbRoute.decode("android:auto:extra"))
    }

    @Test fun `saved values keep their spelling`() {
        assertEquals("android:auto", UsbRoute.encode(Choice(Way.ANDROID, manual = false)))
        assertEquals("engine:manual", UsbRoute.encode(Choice(Way.ENGINE, manual = true)))
    }

    @Test fun `the key is the device and the interface ids`() {
        assertEquals("tablet|usb:1397:0508", UsbRoute.key("tablet", 0x1397, 0x0508))
        assertFalse(UsbRoute.key("tablet", 1, 2) == UsbRoute.key("phone", 1, 2))
    }

    @Test fun `three failed opens in a row count, a good open starts the count again`() {
        val t = UsbRoute.Trouble()
        assertFalse(t.openFailed())
        assertFalse(t.openFailed())
        t.opened()
        assertFalse(t.openFailed())
        assertFalse(t.openFailed())
        assertTrue(t.openFailed())
    }

    @Test fun `the engine halving its queue is not trouble, more drops in two minutes are`() {
        val t = UsbRoute.Trouble()
        // 64, 32, 16 and 8 ms: the engine adapting, with the watchdog's 5 s between drops
        for (i in 0 until UsbRoute.DROP_LIMIT - 1) assertFalse(t.dropped(i * 5_000L))
        assertTrue(t.dropped((UsbRoute.DROP_LIMIT - 1) * 5_000L))
    }

    @Test fun `drops spread over a long show don't add up`() {
        val t = UsbRoute.Trouble()
        for (i in 0 until 20) assertFalse(t.dropped(i * (UsbRoute.DROP_WINDOW_MS / 2)))
    }

    @Test fun `a new cable or a new way starts clean`() {
        val t = UsbRoute.Trouble()
        repeat(UsbRoute.DROP_LIMIT - 1) { t.dropped(it.toLong()) }
        repeat(UsbRoute.OPEN_FAILS - 1) { t.openFailed() }
        t.reset()
        assertFalse(t.dropped(10))
        assertFalse(t.openFailed())
    }
}
