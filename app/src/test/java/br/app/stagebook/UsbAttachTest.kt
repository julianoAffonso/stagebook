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

/** Plugging in the mixer, Android offers to open the app (user, 01/11). */
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

    // without it Android greys out "Always" for an interface that also records, and asks again on every plug (02/11)
    @Test fun `the app declares the recording permission Android requires to make it the interface's default`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(android.Manifest.permission.RECORD_AUDIO in info.requestedPermissions.orEmpty())
    }
}
