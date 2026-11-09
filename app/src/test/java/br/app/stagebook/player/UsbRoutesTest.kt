package br.app.stagebook.player

import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.domain.UsbRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

/** The way each USB audio interface opens, remembered on this device (user, 09/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UsbRoutesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun device(vendor: Int, product: Int): UsbDevice = Shadow.newInstanceOf(UsbDevice::class.java).also {
        ReflectionHelpers.setField(it, "mVendorId", vendor)
        ReflectionHelpers.setField(it, "mProductId", product)
    }

    private val mixer = device(0x1397, 0x0507)
    private val other = device(0x0582, 0x01d6)

    @Test fun `nothing remembered opens the default way`() {
        assertNull(UsbRoutes.remembered(context, mixer))
        assertFalse(UsbRoutes.android(context, mixer, androidDefault = false))
        assertTrue(UsbRoutes.android(context, mixer, androidDefault = true))
    }

    @Test fun `the person's choice wins over the default and is kept per interface`() {
        UsbRoutes.choose(context, mixer, android = true)
        assertTrue(UsbRoutes.android(context, mixer, androidDefault = false))
        assertEquals(UsbRoute.Choice(UsbRoute.Way.ANDROID, manual = true), UsbRoutes.remembered(context, mixer))
        // another interface keeps the default
        assertFalse(UsbRoutes.android(context, other, androidDefault = false))
    }

    @Test fun `a failing engine switches once to Android and remembers it as automatic`() {
        assertTrue(UsbRoutes.fallback(context, mixer, failedAndroid = false))
        assertEquals(UsbRoute.Choice(UsbRoute.Way.ANDROID, manual = false), UsbRoutes.remembered(context, mixer))
        assertTrue(UsbRoutes.android(context, mixer, androidDefault = false))
        // Android fails too: no ping-pong, it stays
        assertFalse(UsbRoutes.fallback(context, mixer, failedAndroid = true))
        assertTrue(UsbRoutes.android(context, mixer, androidDefault = false))
    }

    @Test fun `a failing Android way switches to the engine`() {
        assertTrue(UsbRoutes.fallback(context, mixer, failedAndroid = true))
        assertFalse(UsbRoutes.android(context, mixer, androidDefault = true))
    }

    @Test fun `a manual choice is never undone by a failure`() {
        UsbRoutes.choose(context, mixer, android = false)
        assertFalse(UsbRoutes.fallback(context, mixer, failedAndroid = false))
        assertEquals(UsbRoute.Choice(UsbRoute.Way.ENGINE, manual = true), UsbRoutes.remembered(context, mixer))
    }

    @Test fun `each saved way tells Settings to read again`() {
        val before = UsbRoutes.changes.value
        UsbRoutes.choose(context, mixer, android = true)
        UsbRoutes.fallback(context, other, failedAndroid = false)
        assertEquals(before + 2, UsbRoutes.changes.value)
        // a failure that changes nothing doesn't bump it
        UsbRoutes.fallback(context, other, failedAndroid = true)
        assertEquals(before + 2, UsbRoutes.changes.value)
    }

    @Test fun `an unreadable saved value falls back to the default`() {
        val key = UsbRoute.key(android.os.Build.DEVICE.orEmpty(), 0x1397, 0x0507)
        context.getSharedPreferences("usb_route", Context.MODE_PRIVATE).edit().putString(key, "future:value").commit()
        assertNull(UsbRoutes.remembered(context, mixer))
        assertTrue(UsbRoutes.android(context, mixer, androidDefault = true))
    }

    @Test fun `no interface plugged shows the library's switch`() {
        assertEquals("", UsbRoutes.read(context, androidDefault = true).name)
        assertTrue(UsbRoutes.read(context, androidDefault = true).android)
        assertFalse(UsbRoutes.read(context, androidDefault = false).android)
    }

    @Test fun `the switch shows the plugged interface's way, or the default with none`() {
        assertTrue(UsbOutputUi().androidOr(true))
        assertFalse(UsbOutputUi().androidOr(false))
        assertFalse(UsbOutputUi(name = "Mixer", android = false).androidOr(true))
        assertTrue(UsbOutputUi(name = "Mixer", android = true).androidOr(false))
    }
}
