package br.app.stagebook.player

import android.hardware.usb.UsbDevice
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

/** Through Android, the media volume at zero plays nothing (seen on the TCL): the stage says so, in the short note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class MutedUsbTest {
    private val service by lazy { Robolectric.buildService(PlayerService::class.java).create().get() }
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val audio get() = context.getSystemService(AudioManager::class.java)

    @Test fun `through Android at zero it's muted`() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        service.androidUsb = Shadow.newInstanceOf(UsbDevice::class.java)
        assertTrue(service.muted())
    }

    @Test fun `with volume it isn't`() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        service.androidUsb = Shadow.newInstanceOf(UsbDevice::class.java)
        assertFalse(service.muted())
    }

    @Test fun `the engine doesn't use the media volume`() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        assertFalse(service.muted())
    }

    @Test fun `it fits the short note`() = assertTrue(context.getString(R.string.player_usb_muted).length <= 40)
}
