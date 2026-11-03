package br.app.stagebook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import br.app.stagebook.data.model.Look
import androidx.compose.ui.graphics.toArgb
import br.app.stagebook.ui.stage.ChordLook
import br.app.stagebook.ui.stage.SECTION_TAG
import br.app.stagebook.ui.stage.SectionNames
import br.app.stagebook.ui.stage.stageLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SectionLookTest {
    private val chords = ChordLook(true, 0.8f, Color.Yellow)

    private fun headerStyle(scale: Float) =
        stageLines("[Refrão]\nlinha", SectionNames(null), Color.Cyan, true, chords, scale).first().text.spanStyles.single().item

    @Test fun `the section title follows the size chosen in settings`() {
        assertEquals(1.2f.em, headerStyle(1.2f).fontSize)
        assertEquals(Look.DEFAULT_SECTION_SCALE.em, headerStyle(Look.DEFAULT_SECTION_SCALE).fontSize)
    }

    // the pill is drawn by ChordText from the section mark, not as a text background tight on the letters (user, 03/11)
    @Test fun `the section title has a light pill in the section color`() {
        val text = stageLines("[Refrão]\nlinha", SectionNames(null), Color.Cyan, true, chords, 1f).first().text
        assertEquals(Color.Unspecified, text.spanStyles.single().item.background)
        val pill = Color(text.getStringAnnotations(SECTION_TAG, 0, text.length).single().item.toInt())
        assertNotEquals(0f, pill.alpha)
        assertEquals(Color.Cyan.copy(alpha = pill.alpha).toArgb(), pill.toArgb())
    }

    @Test fun `hidden sections draw no title`() {
        val lines = stageLines("[Refrão]\nlinha", SectionNames(null), Color.Cyan, false, chords, 1.2f)
        assertEquals("linha", lines.first { it.text.isNotEmpty() }.text.text)
    }
}
