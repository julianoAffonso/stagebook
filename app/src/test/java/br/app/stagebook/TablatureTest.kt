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
        assertEquals(listOf("e|---0---3---|", "B|---1---0---|"), tabLines.map { it.text.text })
        val lyricsOnly = br.app.stagebook.ui.stage.stageLines(text, names, androidx.compose.ui.graphics.Color.Cyan, true, br.app.stagebook.ui.stage.ChordLook(false, 0.8f, androidx.compose.ui.graphics.Color.Yellow))
        assertTrue(lyricsOnly.none { it.text.text.contains("|---") })
    }

    @Test fun `a long run of digits doesn't crash transposing and stays as it is`() {
        val line = "e|12121212121212---3---|"
        assertEquals("e|12121212121212---5---|", br.app.stagebook.domain.Tablature.shiftLine(line, 2))
    }

    // a Cifra Club style tab (only the tab lines, no lyrics): each string line counts, the chord row above doesn't
    @org.junit.Test fun `a loose six-string tab without brackets is recognised line by line`() {
        val tab = listOf(
            "E|------------5----------9----------------7--------------5---------------| ",
            "B|------7---------------------10---------------8--------------5----------| ",
            "G|----7---7-------7----9---9-------9----7---7-----7----6---6----7-6-4----| ",
            "D|--------------------------------------------------------------------7--| ",
            "A|-----------------------------------------------------------------------| ",
            "E|-----------------------------------------------------------------------| ",
        )
        val text = (listOf("       D                        A                  G               A", "[Tab - Intro]") + tab).joinToString("\n")
        val flags = Tablature.tabLines(text)
        assertEquals(listOf(false, false, true, true, true, true, true, true), flags)
        // transposing up a half step moves every fret and keeps the columns
        val up = Tablature.shift(text, 1)
        assertEquals(text.lines().size, up.lines().size)
        // the strings stay aligned with each other (where a fret gains a digit, every string gains the column)
        assertTrue(up, up.lines().drop(2).map { it.length }.distinct().size == 1)
    }
    @Test fun `a half step down and back up gives the same tab, open strings included (device test B-15)`() {
        val tab = listOf(
            "E|-1---1---3-3----1---3-3-3----3-3------|",
            "B|-1---1---3-3----1---3-3-3----3-3------|",
            "G|-2---2---4-4----2---4-4-4----4-4------|",
            "D|-0-------0--------0--------0----------|",
        ).joinToString("\n")
        val down = Tablature.shift(tab, -1)
        // the open D can't go lower: the whole block goes up an octave and keeps its shape
        assertTrue(down, down.lines()[3].startsWith("D|-11"))
        assertTrue(down, down.lines()[0].startsWith("E|-12"))
        // the notes come back, and the text too
        val notes = { s: String -> s.lines().map { l -> Regex("""\d+""").findAll(l.substringAfter('|')).map { it.value }.toList() } }
        assertEquals(notes(tab), notes(Tablature.shift(down, 1)))
        assertEquals(tab, Tablature.shift(down, 1))
        assertEquals(tab, Tablature.shift(Tablature.shift(tab, 1), -1))
    }
    @Test fun `without the tablature, a tab-only part goes whole, title included (device test B-04)`() {
        val page = listOf(
            "[Intro]", "Dm7  G/D  Dm7  G/D", "",
            "[Tab - Intro]", "", "Parte 1 de 2", "Dm7  G/D", "E|-1---1---3-3---|", "B|-1---1---3-3---|", "",
            "[Primeira Parte]", "D7M(9)", "Vou cantar aqui", "",
            "[Tab - Passagem]", "E|-5-3-1---|", "",
            "[Segunda Parte]", "Gm7  C/Bb", "Outra linha",
        ).joinToString("\n")
        val out = Tablature.strip(page)
        assertTrue(out, "Tab" !in out && "Parte 1 de 2" !in out && "E|" !in out)
        assertTrue(out, "[Intro]" in out && "[Primeira Parte]" in out && "Vou cantar aqui" in out && "[Segunda Parte]" in out && "Outra linha" in out)
    }
    @Test fun `a tab part ends at the first lyrics line, even with no title after it (S26 retest, Under Pressure)`() {
        val page = listOf(
            "[Intro]", "D  A/D  D  A/D", "",
            "[Tab - Intro]", "Parte 1 de 2", "D       A/D", "E|-------------|", "B|-------------|", "",
            "D", "Uma linha inventada de exemplo", "A/D", "Outra linha da letra aqui", "", "G", "Mais uma linha cantada",
        ).joinToString("\n")
        val out = Tablature.strip(page)
        assertTrue(out, "Tab" !in out && "Parte 1 de 2" !in out && "E|" !in out)
        assertTrue(out, "Uma linha inventada de exemplo" in out && "Outra linha da letra aqui" in out && "Mais uma linha cantada" in out)
        // the first lyrics line keeps its chord
        assertTrue(out, "D\nUma linha inventada de exemplo" in out)
    }

    @Test fun `without the tab, its captions go and a part left with nothing goes too (user, 11-11)`() {
        val page = listOf(
            "[Intro]", "", "[Tab - Intro]", "", "(Guitarra Base)", "", "Riff Principal", "E|--------------|", "B|-0h1---1---0h1|", "",
            "Variação 1 do Riff Principal", "E|--------------|", "B|-0h1-1-1---0h1|", "", "(Guitarra Solo)", "", "E|-14--14-------|", "",
            "[Refrão]", "", "[Tab - Refrão]", "", "E|-0h1---1---0h2|", "", "Uma linha inventada de exemplo", "Outra linha da letra aqui", "",
            "[Primeira Parte]", "", "(Riff Principal)", "", "Mais uma linha cantada", "",
            "[Solo]", "", "[Tab - Solo]", "", "(Guitarra Solo)", "", "E|-12b14--------|", "  ~", "",
            "[Refrão]", "", "[Final]", "", "[Tab - Final]", "", "E|-0------------|",
        ).joinToString("\n")
        val out = Tablature.strip(page)
        assertEquals("[Refrão]\n\nUma linha inventada de exemplo\nOutra linha da letra aqui\n\n[Primeira Parte]\n\nMais uma linha cantada\n\n[Refrão]", out.trim())
    }
}
