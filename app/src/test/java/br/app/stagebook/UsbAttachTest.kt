package br.app.stagebook

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plugging in the mixer, Android offers to open the app (the Backing app did it; user, 01/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsbAttachTest {
    @Test fun `the main screen answers a USB device plugged in, with the audio interface filter`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).setPackage(context.packageName)
        val found = context.packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        assertEquals(MainActivity::class.java.name, found.single().activityInfo.name)
        assertTrue(found.single().activityInfo.metaData.getInt(UsbManager.ACTION_USB_DEVICE_ATTACHED) == R.xml.usb_audio_filter)
    }
}
