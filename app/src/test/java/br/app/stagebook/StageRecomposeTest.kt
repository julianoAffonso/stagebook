package br.app.stagebook

import br.app.stagebook.domain.Sections
import br.app.stagebook.ui.stage.SectionNames
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stage keeps its lyrics between recompositions (deep audit, 30/10). */
class StageRecomposeTest {
    @Test fun `the same section names are the same remember key`() {
        val a = SectionNames(mapOf(Sections.Kind.CHORUS to "Refrão"))
        val b = SectionNames(mapOf(Sections.Kind.CHORUS to "Refrão"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(SectionNames(null), SectionNames(null))
    }
}
