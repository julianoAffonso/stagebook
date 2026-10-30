package br.app.stagebook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.domain.Sections.Kind
import br.app.stagebook.ui.editor.insertSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Atalhos de seção do editor: onde o título entra e onde o cursor fica (| = cursor). */
class SectionInsertTest {
    private fun at(marked: String) = TextFieldValue(marked.replace("|", ""), TextRange(marked.indexOf('|')))
    private fun show(v: TextFieldValue) = v.text.substring(0, v.selection.start) + "|" + v.text.substring(v.selection.start)

    @Test fun `empty lyrics`() = assertEquals("[Intro]\n|", show(insertSection(at("|"), Kind.INTRO)))

    @Test fun `at the end of the lyrics it goes after, with breathing room`() =
        assertEquals("linha um\n\n[Refrão]\n|", show(insertSection(at("linha um|"), Kind.CHORUS)))

    @Test fun `in the middle of a line it does not split the line`() =
        assertEquals("linha um\n\n[Ponte]\n|linha dois", show(insertSection(at("lin|ha um\nlinha dois"), Kind.BRIDGE)))

    @Test fun `on the blank line between verses it takes that line and keeps the breathing room`() =
        assertEquals("a\n\n[Refrão]\n|b", show(insertSection(at("a\n|\nb"), Kind.CHORUS)))

    @Test fun `at the start of a line with lyrics it goes before it`() =
        assertEquals("[Intro]\n|primeira", show(insertSection(at("|primeira"), Kind.INTRO)))

    @Test fun `verse numbered by the ones before`() =
        assertEquals("[Verso 1]\na\n\n[Verso 2]\n|", show(insertSection(at("[Verso 1]\na|"), Kind.VERSE)))

    @Test fun `verse in English counts too`() =
        assertEquals("[Verse]\na\n\n[Verso 2]\n|", show(insertSection(at("[Verse]\na|"), Kind.VERSE)))
}
