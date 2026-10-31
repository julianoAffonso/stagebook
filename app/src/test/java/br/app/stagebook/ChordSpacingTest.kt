package br.app.stagebook

import androidx.compose.ui.graphics.Color
import br.app.stagebook.ui.stage.CHORD_TAG
import br.app.stagebook.ui.stage.ChordLook
import br.app.stagebook.ui.stage.chordLine
import br.app.stagebook.ui.stage.emWidth
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chords close together never land on top of each other (user, 31/10: A Little Respect). */
class ChordSpacingTest {
    private fun room(raw: String, scale: Float) {
        val line = chordLine(raw, ChordLook(true, scale, Color.Yellow))
        val marks = line.getStringAnnotations(CHORD_TAG, 0, line.length)
        marks.zipWithNext().forEach { (a, b) ->
            val between = line.text.substring(a.start, b.start)
            assertTrue("${a.item} has no room before ${b.item}", emWidth(between) >= emWidth(a.item) * scale * 1.1f)
        }
    }

    @Test fun `sus chords two letters apart get room`() = room("x[Csus4] ab c[C]d [Csus4]efghij[C]kl", 0.8f)

    @Test fun `bigger chords get more room`() = room("[Fsus4]il [C]ab[Csus4] cd [C]ef", 1.2f)

    @Test fun `lyrics already wide enough are not changed`() {
        val line = chordLine("[C]long enough words [G]here", ChordLook(true, 0.8f, Color.Yellow))
        assertTrue(line.text == "long enough words here")
    }
}
