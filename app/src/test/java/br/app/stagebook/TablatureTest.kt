package br.app.stagebook

import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.Tablature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TablatureTest {
    private val tab = "e|---0---3---|\nB|---1---0---|"

    @Test fun `recognizes a tab line and does not mistake it for lyrics`() {
        assertTrue(Tablature.isTabLine("e|---0---3---|"))
        assertTrue(Tablature.isTabLine("|--5h7--5--|"))
        assertFalse(Tablature.isTabLine("Saio cedo pela rua"))
        assertFalse(Tablature.isTabLine("[G]linha"))
    }

    @Test fun `moves frets up and down without moving the columns`() {
        assertEquals("e|---2---5---|", Tablature.shiftLine("e|---0---3---|", 2))
        assertEquals("e|---10--12--|", Tablature.shiftLine("e|---8---10--|", 2))   // 8 becomes 10: eats a dash
        assertEquals("e|---9---10--|", Tablature.shiftLine("e|---10--11--|", -1))  // 10 becomes 9: gives a dash back
        assertEquals("e|---11--|", Tablature.shiftLine("e|---0---|", -1))          // negative goes up an octave (eats a dash)
        assertEquals("B|--5h7--|", Tablature.shiftLine("B|--3h5--|", 2))           // the B string isn't a fret
    }

    @Test fun `transposing the key takes the tab along`() {
        val text = "{key: G}\n[G]letra\n${Tablature.START}\n$tab\n${Tablature.END}"
        val up = Chords.transpose(text, 2)
        assertTrue(up.contains("e|---2---5---|"))
        assertTrue(up.contains("[A]letra"))
    }

    @Test fun `import - wraps a loose tab, or removes it`() {
        val raw = "letra\n$tab\nmais letra"
        assertEquals("letra\n${Tablature.START}\n$tab\n${Tablature.END}\nmais letra", Tablature.wrap(raw))
        assertEquals("letra\nmais letra", Tablature.strip(Tablature.wrap(raw)))
        assertTrue(Tablature.hasTab(raw))
    }

    @Test fun `on stage - tab in monospace, and gone in the lyrics only profile`() {
        val text = "[G]letra\n${Tablature.START}\n$tab\n${Tablature.END}"
        val names = br.app.stagebook.ui.stage.SectionNames(null)
        val withChords = br.app.stagebook.ui.stage.stageLines(text, names, androidx.compose.ui.graphics.Color.Cyan, true, br.app.stagebook.ui.stage.ChordLook(true, 0.8f, androidx.compose.ui.graphics.Color.Yellow))
        val tabLines = withChords.filter { it.text.hasStringAnnotations(br.app.stagebook.ui.stage.TAB_TAG, 0, it.text.length) }
        // the stage shows the strings as box-drawing lines; the saved text keeps the dashes
        assertEquals(listOf("e|───0───3───|", "B|───1───0───|"), tabLines.map { it.text.text })
        val lyricsOnly = br.app.stagebook.ui.stage.stageLines(text, names, androidx.compose.ui.graphics.Color.Cyan, true, br.app.stagebook.ui.stage.ChordLook(false, 0.8f, androidx.compose.ui.graphics.Color.Yellow))
        assertTrue(lyricsOnly.none { it.text.text.contains("|---") })
    }

    @Test fun `a long run of digits doesn't crash transposing and stays as it is`() {
        val line = "e|12121212121212---3---|"
        assertEquals("e|12121212121212---5---|", br.app.stagebook.domain.Tablature.shiftLine(line, 2))
    }
}
