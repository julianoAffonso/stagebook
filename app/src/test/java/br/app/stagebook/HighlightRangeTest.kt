package br.app.stagebook

import br.app.stagebook.domain.HighlightRange
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stage highlighter: the words the finger ran over, as shown, get `==` in the saved line (user, 02/11). */
class HighlightRangeTest {
    @Test fun `plain line - from the middle of one word to another highlights whole words`() {
        val line = "Saio cedo pela rua"
        // shown the same; finger from "ce|do" to "pe|la"
        assertEquals("Saio ==cedo pela== rua", HighlightRange.toggle(line, line, 7, 12))
    }

    @Test fun `a tap on one word highlights just it`() {
        assertEquals("Saio ==cedo== pela rua", HighlightRange.toggle("Saio cedo pela rua", "Saio cedo pela rua", 6, 6))
    }

    @Test fun `with chords - shown without them and with padding, the saved line keeps its chords`() {
        val saved = "[G]Saio cedo pela [D]rua"
        val shown = "Saio cedo pela rua   "   // the stage draws the chords above and pads for room
        assertEquals("[G]Saio ==cedo pela== [D]rua", HighlightRange.toggle(saved, shown, 5, 13))
        // a word that starts on a chord: the mark goes after the chord (the stage moves the chord to the next letter)
        assertEquals("[G]Saio cedo pela [D]==rua==", HighlightRange.toggle(saved, shown, 16, 16))
    }

    @Test fun `a chord in the middle of a word stays inside the highlight`() {
        assertEquals("==Pri[D]meira== linha", HighlightRange.toggle("Pri[D]meira linha", "Pri  meira linha", 1, 1))
    }

    @Test fun `running over a highlight takes it off`() {
        assertEquals("Saio cedo pela rua", HighlightRange.toggle("Saio ==cedo pela== rua", "Saio cedo pela rua", 6, 6))
    }

    @Test fun `a piece of a wrapped line is found in the saved line`() {
        val saved = "Uma linha muito comprida que quebrou em duas no palco"
        assertEquals("Uma linha muito comprida que quebrou ==em== duas no palco", HighlightRange.toggle(saved, "em duas no palco", 0, 1))
    }

    @Test fun `text that isn't in the saved line changes nothing`() {
        assertEquals("Saio cedo", HighlightRange.toggle("Saio cedo", "outra coisa", 0, 3))
    }

    // a wrapped line's second piece repeating the first: the word is marked where the finger was (audit 02/11)
    @Test fun `a repeated word in the second piece of a wrapped line is marked there`() {
        val saved = "oh oh oh oh"
        // the stage split it as "oh oh " + "oh oh": 4 letters before the second piece
        assertEquals("oh oh ==oh== oh", HighlightRange.toggle(saved, "oh oh", 0, 0, after = 4))
        assertEquals("==oh== oh oh oh", HighlightRange.toggle(saved, "oh oh", 0, 0))
    }
}
