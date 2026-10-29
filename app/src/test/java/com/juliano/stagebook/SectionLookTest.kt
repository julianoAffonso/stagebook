package com.juliano.stagebook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.ui.stage.ChordLook
import com.juliano.stagebook.ui.stage.SectionNames
import com.juliano.stagebook.ui.stage.stageLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SectionLookTest {
    private val chords = ChordLook(true, 0.8f, Color.Yellow)

    private fun headerStyle(scale: Float) =
        stageLines("[Refrão]\nlinha", SectionNames(null), Color.Cyan, true, chords, scale).first().text.spanStyles.single().item

    @Test fun `o título da seção segue o tamanho escolhido nos ajustes`() {
        assertEquals(1.2f.em, headerStyle(1.2f).fontSize)
        assertEquals(Look.DEFAULT_SECTION_SCALE.em, headerStyle(Look.DEFAULT_SECTION_SCALE).fontSize)
    }

    @Test fun `o título da seção tem fundo leve na cor da seção`() {
        val bg = headerStyle(1f).background
        assertNotEquals(Color.Unspecified, bg)
        assertEquals(Color.Cyan.copy(alpha = bg.alpha), bg)
    }

    @Test fun `seções escondidas não desenham título`() {
        val lines = stageLines("[Refrão]\nlinha", SectionNames(null), Color.Cyan, false, chords, 1.2f)
        assertEquals("linha", lines.first { it.text.isNotEmpty() }.text.text)
    }
}
