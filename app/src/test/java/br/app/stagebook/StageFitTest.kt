package br.app.stagebook

import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.domain.StageFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageFitTest {
    @Test
    fun `zoom máximo faz a maior palavra caber na largura`() {
        // palavra de 500 px no tamanho padrão, tela de 1000 px: pode crescer até ~2x
        val z = StageFit.zoomToFit(500, 1000)
        assertEquals(1.96f, z, 0.001f)
        assertTrue("500 px × zoom tem que caber em 1000 px", 500 * z <= 1000)
    }

    @Test
    fun `palavra maior que a tela já no tamanho padrão faz a letra diminuir`() {
        assertTrue(StageFit.zoomToFit(1200, 1000) < 1f)
    }

    @Test
    fun `sem palavras ou sem largura não limita`() {
        assertEquals(Float.MAX_VALUE, StageFit.zoomToFit(0, 1000))
        assertEquals(Float.MAX_VALUE, StageFit.zoomToFit(300, 0))
    }

    @Test
    fun `automático decide pelas frases da música`() {
        val curtas = List(20) { 400 }
        assertEquals("frases curtas e música longa: 2 colunas", 2, StageFit.columns(StageColumns.AUTO, false, curtas, 500, true))
        assertEquals("cabe numa página: 1 coluna", 1, StageFit.columns(StageColumns.AUTO, true, curtas, 500, true))
        assertEquals("frases longas quebrariam: 1 coluna", 1, StageFit.columns(StageColumns.AUTO, false, List(20) { 700 }, 500, true))
        assertEquals("uma ou outra longa (até 10%) não impede", 2, StageFit.columns(StageColumns.AUTO, false, curtas.dropLast(2) + listOf(700, 700), 500, true))
        assertEquals("zoom não cabe em meia tela: 1 coluna", 1, StageFit.columns(StageColumns.AUTO, false, curtas, 500, false))
        assertEquals(2, StageFit.columns(StageColumns.TWO, true, emptyList(), 500, false))
        assertEquals(1, StageFit.columns(StageColumns.ONE, false, curtas, 500, true))
    }
}
