package br.app.stagebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The limits of what may pass between devices (only lyrics, chords and setlists), at their edges. */
class BandContentEdgesTest {
    private val chart = "[G]Saio cedo pela [D]rua\nOnde a [Em]estrada vai dar"

    @Test fun `a song needs a title that isn't blank`() {
        assertFalse(BandContent.songOk("", "", "", chart))
        assertFalse(BandContent.songOk("   ", "", "", chart))
        assertTrue(BandContent.songOk("Hazard", "", "", chart))
    }

    @Test fun `title, author and year limits are inclusive`() {
        val max = "Ta ".repeat(BandContent.MAX_TITLE).take(BandContent.MAX_TITLE)
        assertTrue(BandContent.songOk(max, max, "1".repeat(BandContent.MAX_YEAR), chart))
        assertFalse(BandContent.songOk("Hazard", "A".repeat(BandContent.MAX_TITLE + 1), "", chart))
        assertFalse(BandContent.songOk("Hazard", "", "1".repeat(BandContent.MAX_YEAR + 1), chart))
    }

    @Test fun `the text limit is inclusive`() {
        val words = "la ".repeat(BandContent.MAX_TEXT / 3)
        assertTrue(BandContent.songOk("Hazard", "", "", words.take(BandContent.MAX_TEXT)))
        assertFalse(BandContent.songOk("Hazard", "", "", words.take(BandContent.MAX_TEXT) + "x"))
    }

    @Test fun `line breaks and tabs are plain text, other control characters are not`() {
        assertTrue(BandContent.isPlainText("um\r\ndois\tTrês\n"))
        assertFalse(BandContent.isPlainText("um\u0000dois"))
        assertFalse(BandContent.isPlainText("bell\u0007"))
        assertFalse(BandContent.isPlainText("esc\u001B[0m"))
        // a control character anywhere refuses the song, also in the author or year
        assertFalse(BandContent.songOk("Hazard", "Richard\u0001", "", chart))
        assertFalse(BandContent.songOk("Hazard", "", "19\u000088", chart))
    }

    @Test fun `an encoded block is caught from 200 characters on`() {
        val enc = "QUJD".repeat(60)
        assertTrue(BandContent.isPlainText(enc.take(199)))
        assertFalse(BandContent.isPlainText(enc.take(200)))
        // separated by any whitespace it is caught the same
        assertFalse(BandContent.isPlainText("linha\t" + enc + "\rfim"))
    }

    @Test fun `a long run of symbols that isn't encoded passes`() {
        assertTrue(BandContent.isPlainText("=".repeat(10) + "-".repeat(400)))
        assertTrue(BandContent.isPlainText("e|" + "-".repeat(300) + "|"))
    }

    @Test fun `url-safe base64 is caught too`() {
        assertFalse(BandContent.isPlainText("Zm9v_YmFy".repeat(25)))
    }

    @Test fun `the band on the network takes up to 30 setlists`() {
        assertTrue(BandContent.bandFits(BandContent.MAX_BAND_SETS))
        assertFalse(BandContent.bandFits(BandContent.MAX_BAND_SETS + 1))
    }

    @Test fun `the internet limit counts songs on their own too`() {
        assertFalse(BandContent.internetFits(lists = 0, songs = BandContent.MAX_SONGS + 1))
        assertFalse(BandContent.internetFits(lists = 59, songs = 1))
        assertTrue(BandContent.internetFits(lists = 58, songs = 1))
    }

    @Test fun `a setlist text over the size limit is refused even if readable`() {
        val titles = (1..2_000).joinToString("\n") { "Música número $it" }
        assertFalse(BandContent.listOk("STAGEBOOK1\n2000\nShow\n$titles"))
    }
}
