package br.app.stagebook

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.sp
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.stageChordStyle
import br.app.stagebook.ui.stage.stageTextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stage, the external screen and the two previews write the lyrics with one style (audit 22/11). */
class StageTextStyleTest {
    @Test fun `the lyrics never break a word with a hyphen, and the chords follow their own scale`() {
        val look = Look(bold = false, chordScale = 0.5f)
        val text = stageTextStyle(look, 40f)
        assertEquals(Hyphens.None, text.hyphens)
        assertEquals(40.sp, text.fontSize)
        assertEquals(50.sp, text.lineHeight)
        assertEquals(FontWeight.Normal, text.fontWeight)
        val chords = stageChordStyle(text, look)
        assertEquals(20.sp, chords.fontSize)
        assertEquals(FontWeight.Bold, chords.fontWeight)
        assertEquals(Hyphens.None, chords.hyphens)
    }
}
