package br.app.stagebook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.ChordLook
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

    @Test fun `the section title has a light background in the section color`() {
        val bg = headerStyle(1f).background
        assertNotEquals(Color.Unspecified, bg)
        assertEquals(Color.Cyan.copy(alpha = bg.alpha), bg)
    }

    @Test fun `hidden sections draw no title`() {
        val lines = stageLines("[Refrão]\nlinha", SectionNames(null), Color.Cyan, false, chords, 1.2f)
        assertEquals("linha", lines.first { it.text.isNotEmpty() }.text.text)
    }
}
