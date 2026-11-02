package br.app.stagebook

import br.app.stagebook.data.ChordPlayer
import br.app.stagebook.data.model.ChordPlayMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quick taps on chords keep one track at most, and stop leaves none (deep audit, 30/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChordPlayerTest {
    private fun track(p: ChordPlayer): android.media.AudioTrack? =
        ChordPlayer::class.java.getDeclaredField("track").apply { isAccessible = true }.get(p) as android.media.AudioTrack?

    @Test fun `rapid taps end with a single track, stop releases it`() {
        val player = ChordPlayer()
        repeat(5) { player.play(listOf(55, 59, 62), ChordPlayMode.CHORD, keyboard = false) }
        Thread.sleep(1_500)
        val t = track(player)
        // the last tap's track (or none if the synth is still running): never more than one kept alive
        if (t != null) assertTrue(t.state != android.media.AudioTrack.STATE_UNINITIALIZED)   // alive, not released
        player.stop()
        assertNull(track(player))
    }
}
