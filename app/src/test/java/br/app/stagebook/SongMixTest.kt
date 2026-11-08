package br.app.stagebook

import br.app.stagebook.domain.MixChannel
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.domain.forSong
import br.app.stagebook.domain.toSongMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A song's mix starts as a copy of the general mixer the first time it's changed on stage, and from then on it's
 * independent: the song always plays with its own volume and EQ (user, 31/10).
 */
class SongMixTest {
    private val general = MixerConfig(channels = 2, eqDb = listOf(0f, 0f, 0f, 0f, 0f)).withStrip(0) { it.copy(eq = true) }

    @Test fun `a song without its own mix plays with the general mixer`() = assertEquals(general, general.forSong(null))

    @Test fun `the first change starts from the general mixer`() {
        val changed = general.copy(strips = general.strips.toMutableList().also { it[0] = MixChannel(gainDb = -6f, eq = true) })
        val own = changed.toSongMix()
        assertEquals(-6f, general.forSong(own).strips[0].gainDb)
        assertEquals(general.eqOn, general.forSong(own).eqOn)
    }

    @Test fun `after that the song is independent of the general mixer`() {
        val own = general.copy(strips = general.strips.toMutableList().also { it[0] = MixChannel(gainDb = -6f, eq = true) }, eqDb = listOf(3f, 0f, 0f, 0f, 0f)).toSongMix()
        // the general mixer changes later: volume, EQ on/off and bands
        val generalLater = general.copy(strips = general.strips.toMutableList().also { it[0] = MixChannel(gainDb = 2f) }, eqDb = listOf(-9f, 0f, 0f, 0f, 0f))
        val played = generalLater.forSong(own)
        assertEquals(-6f, played.strips[0].gainDb)
        assertTrue(played.eqOn)
        assertEquals(3f, played.eqDb[0])
    }

    @Test fun `the EQ is on while a channel goes through it`() {
        val first = MixerConfig(channels = 2).withStrip(0) { it.copy(eq = true) }
        assertTrue(first.eqOn)
        assertTrue(first.withStrip(1) { it.copy(eq = true) }.withStrip(0) { it.copy(eq = false) }.eqOn)
        assertFalse(first.withStrip(0) { it.copy(eq = false) }.eqOn)
    }

    @Test fun `a channel beyond the file doesn't turn the EQ on`() {
        assertFalse(MixerConfig(channels = 2).withStrip(3) { it.copy(eq = true) }.eqOn)
    }

    @Test fun `outputs and channel count stay the general ones`() {
        val own = general.toSongMix()
        val generalLater = general.copy(channels = 4)
        assertEquals(4, generalLater.forSong(own).channels)
    }

    @Test fun `the engine always gets 5 EQ bands - a short list is padded flat`() {
        val short = br.app.stagebook.domain.MixerConfig(eqDb = listOf(3f, -2f, 1f))
        assertEquals(listOf(3f, -2f, 1f, 0f, 0f), short.eqGains().toList())
        assertEquals(5, br.app.stagebook.domain.MixerConfig(eqDb = List(7) { 1f }).eqGains().size)
    }
}
