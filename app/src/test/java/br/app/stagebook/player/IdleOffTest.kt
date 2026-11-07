package br.app.stagebook.player

import android.content.Intent
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The player stayed on for 16 h on the S26 with its notification gone (07/11): with nothing playing it turns off after
 * 10 min out of sight or without the USB interface, and swiping the notification away turns it off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class IdleOffTest {
    private val service by lazy { Robolectric.buildService(PlayerService::class.java).create().get() }
    private val tenMinutes = Duration.ofMillis(PlayerService.IDLE_OFF_MS)

    @Before fun on() {
        PlayerService.running = true
        PlayerService.appVisible = true
        service   // created first: playing() and setAppVisible() reach it through its instance
    }

    @After fun reset() {
        PlayerService.running = false
        PlayerService.appVisible = true
    }

    private fun wait(d: Duration) = shadowOf(Looper.getMainLooper()).idleFor(d)

    @Test fun `out of sight with nothing playing it turns off after 10 min`() {
        service.checkIdle()
        PlayerService.setAppVisible(false)
        wait(tenMinutes.minusSeconds(1))
        assertTrue(PlayerService.running)
        wait(Duration.ofSeconds(2))
        assertFalse(PlayerService.running)
    }

    @Test fun `playing out of sight it stays on`() {
        PlayerService.playing(true)
        PlayerService.setAppVisible(false)
        wait(tenMinutes.multipliedBy(2))
        assertTrue(PlayerService.running)
    }

    @Test fun `coming back before 10 min keeps it on`() {
        PlayerService.setAppVisible(false)
        wait(Duration.ofMinutes(9))
        PlayerService.setAppVisible(true)
        wait(tenMinutes)
        assertTrue(PlayerService.running)
    }

    @Test fun `the USB interface gone for 10 min turns it off, on screen too`() {
        service.hadUsb = true
        service.checkIdle()
        wait(tenMinutes.plusSeconds(1))
        assertFalse(PlayerService.running)
    }

    @Test fun `on screen on the device output it stays on`() {
        service.deviceOut = true
        service.checkIdle()
        wait(tenMinutes.multipliedBy(2))
        assertTrue(PlayerService.running)
    }

    @Test fun `swiping the notification away with nothing playing turns it off`() {
        service.onStartCommand(Intent(PlayerService.ACTION_DISMISSED), 0, 1)
        assertFalse(PlayerService.running)
    }

    @Test fun `swiping it away while playing brings it back`() {
        PlayerService.playing(true)
        wait(Duration.ZERO)
        service.onStartCommand(Intent(PlayerService.ACTION_DISMISSED), 0, 1)
        assertTrue(PlayerService.running)
        assertNotNull(shadowOf(service.getSystemService(android.app.NotificationManager::class.java)).getNotification(1))
    }

    @Test fun `the notification says when it's swiped away`() =
        assertNotNull(service.buildNotification("").deleteIntent)
}
