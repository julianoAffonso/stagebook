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

    @Test fun `letra vazia`() = assertEquals("[Intro]\n|", show(insertSection(at("|"), Kind.INTRO)))

    @Test fun `no fim da letra entra depois, com respiro`() =
        assertEquals("linha um\n\n[Refrão]\n|", show(insertSection(at("linha um|"), Kind.CHORUS)))

    @Test fun `no meio da frase não corta a linha`() =
        assertEquals("linha um\n\n[Ponte]\n|linha dois", show(insertSection(at("lin|ha um\nlinha dois"), Kind.BRIDGE)))

    @Test fun `na linha em branco entre estrofes ocupa ela e mantém o respiro`() =
        assertEquals("a\n\n[Refrão]\n|b", show(insertSection(at("a\n|\nb"), Kind.CHORUS)))

    @Test fun `no começo de uma linha com letra entra antes dela`() =
        assertEquals("[Intro]\n|primeira", show(insertSection(at("|primeira"), Kind.INTRO)))

    @Test fun `verso numerado pelos que vêm antes`() =
        assertEquals("[Verso 1]\na\n\n[Verso 2]\n|", show(insertSection(at("[Verso 1]\na|"), Kind.VERSE)))

    @Test fun `verso em inglês também conta`() =
        assertEquals("[Verse]\na\n\n[Verso 2]\n|", show(insertSection(at("[Verse]\na|"), Kind.VERSE)))
}
