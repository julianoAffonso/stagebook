package br.app.stagebook

import br.app.stagebook.domain.StageAnnouncement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Now Playing (T23): the current song announcement in a small, open JSON; it never carries the lyrics. */
class StageAnnouncementTest {
    @Test fun `the announcement goes and comes back and carries what Now Playing shows`() {
        val a = StageAnnouncement(title = "Hazard", artist = "Richard Marx", set = "Show", index = 3, count = 12, key = "G", bpm = 92)
        val json = a.encode()
        assertTrue(json, json.contains("\"title\":\"Hazard\""))
        assertTrue(json, json.contains("\"app\":\"stagebook\""))
        assertEquals(a, StageAnnouncement.decode(json))
    }

    @Test fun `a packet from another program is not read as an announcement`() {
        assertNull(StageAnnouncement.decode("{\"app\":\"outro\",\"title\":\"x\"}"))
        assertNull(StageAnnouncement.decode("lixo"))
    }
}
