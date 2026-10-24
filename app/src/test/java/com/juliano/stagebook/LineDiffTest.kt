package com.juliano.stagebook

import com.juliano.stagebook.domain.LineDiff
import com.juliano.stagebook.domain.LineDiff.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/** O que mudou numa letra, para o usuário decidir se fica com a atualização (texto inventado). */
class LineDiffTest {
    @Test fun `linha trocada aparece como retirada e nova, no lugar dela`() {
        val d = LineDiff.of("um\ndois\ntrês", "um\ndois corrigido\ntrês")
        assertEquals(listOf(Kind.SAME, Kind.ADDED, Kind.REMOVED, Kind.SAME).sorted(), d.map { it.kind }.sorted())
        assertEquals(listOf("um", "três"), d.filter { it.kind == Kind.SAME }.map { it.text })
        assertEquals("dois corrigido", d.single { it.kind == Kind.ADDED }.text)
        assertEquals("dois", d.single { it.kind == Kind.REMOVED }.text)
    }

    @Test fun `seção acrescentada no começo é só uma linha nova`() {
        val d = LineDiff.changes("linha a\nlinha b\nlinha c\nlinha d", "[Verso 1]\nlinha a\nlinha b\nlinha c\nlinha d")
        assertEquals(listOf(Kind.ADDED to "[Verso 1]", Kind.SAME to "linha a", Kind.GAP to ""), d.map { it.kind to it.text })
    }

    @Test fun `mostra só as mudanças com uma linha de contexto, o resto vira reticências (antes e depois)`() {
        val before = (1..10).joinToString("\n") { "linha $it" }
        val after = before + "\n\n[Teste]\nlinha nova"
        val d = LineDiff.changes(before, after)
        assertEquals(listOf(Kind.GAP to "", Kind.SAME to "linha 10", Kind.ADDED to "", Kind.ADDED to "[Teste]", Kind.ADDED to "linha nova"), d.map { it.kind to it.text })
    }

    @Test fun `letras iguais não têm mudança`() {
        assertEquals(emptyList<LineDiff.Line>(), LineDiff.changes("a\nb", "a\nb"))
    }
}
