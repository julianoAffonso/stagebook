package br.app.stagebook

import br.app.stagebook.domain.InternalMarks
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app's own lines (hidden chords, picked shapes) stay out of the editors and come back on save (user, 02/11). */
class InternalMarksTest {
    private val song = "{x_hide_chords: G D}\n{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\n{key: G}\n[G]Saio [D]cedo"

    @Test fun `the editor sees the song without the app's lines`() {
        val (shown, internal) = InternalMarks.split(song)
        assertEquals("{key: G}\n[G]Saio [D]cedo", shown)
        assertEquals(listOf("{x_hide_chords: G D}", "{define-guitar: C base-fret 3 frets x 1 3 3 3 1}"), internal)
    }

    @Test fun `saving puts them back and keeps the edit`() {
        val (_, internal) = InternalMarks.split(song)
        assertEquals("{x_hide_chords: G D}\n{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\n{key: G}\n[G]Saio [D]cedo pela rua",
            InternalMarks.join("{key: G}\n[G]Saio [D]cedo pela rua", internal))
    }

    @Test fun `a line the person typed again isn't doubled - comments and lyrics are never internal`() {
        assertEquals("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\nx", InternalMarks.join("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\nx", listOf("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}")))
        assertEquals(false, InternalMarks.isInternal("{c: Solo de sax}"))
        assertEquals(false, InternalMarks.isInternal("[G]defines a letra"))
    }

    // the same thing typed again (another shape for the same chord): the typed line wins (audit 02/11)
    @Test fun `a typed line for the same chord replaces the hidden one`() {
        val saved = "{define-guitar: G base-fret 1 frets 3 2 0 0 0 3}\n[G]Saio"
        val edited = "{define-guitar: G base-fret 3 frets x 1 3 3 3 1}\n[G]Saio"
        assertEquals(edited, InternalMarks.restore(edited, saved))
        // another chord's hidden shape stays
        assertEquals("{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\n" + edited, InternalMarks.restore(edited, "{define-guitar: C base-fret 3 frets x 1 3 3 3 1}\n[G]Saio"))
    }
}
