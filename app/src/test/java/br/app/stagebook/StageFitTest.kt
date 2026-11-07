package br.app.stagebook

import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.domain.Pagination
import br.app.stagebook.domain.StageFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageFitTest {
    @Test
    fun `max zoom makes the longest word fit the width`() {
        // a 500 px word at default size, 1000 px screen: it can grow up to ~2x
        val z = StageFit.zoomToFit(500, 1000)
        assertEquals(1.96f, z, 0.001f)
        assertTrue("500 px × zoom tem que caber em 1000 px", 500 * z <= 1000)
    }

    @Test
    fun `word wider than the screen at default size makes the lyrics smaller`() {
        assertTrue(StageFit.zoomToFit(1200, 1000) < 1f)
    }

    @Test
    fun `no words or no width does not limit`() {
        assertEquals(Float.MAX_VALUE, StageFit.zoomToFit(0, 1000))
        assertEquals(Float.MAX_VALUE, StageFit.zoomToFit(300, 0))
    }

    @Test
    fun `automatic decides by the song's lines`() {
        val shortLines = List(20) { 400 }
        assertEquals("frases curtas e música longa: 2 colunas", 2, StageFit.columns(StageColumns.AUTO, false, shortLines, 500, true))
        assertEquals("cabe numa página: 1 coluna", 1, StageFit.columns(StageColumns.AUTO, true, shortLines, 500, true))
        assertEquals("frases longas quebrariam: 1 coluna", 1, StageFit.columns(StageColumns.AUTO, false, List(20) { 700 }, 500, true))
        assertEquals("uma ou outra longa (até 10%) não impede", 2, StageFit.columns(StageColumns.AUTO, false, shortLines.dropLast(2) + listOf(700, 700), 500, true))
        assertEquals("zoom não cabe em meia tela: 1 coluna", 1, StageFit.columns(StageColumns.AUTO, false, shortLines, 500, false))
        assertEquals(2, StageFit.columns(StageColumns.TWO, true, emptyList(), 500, false))
        assertEquals(1, StageFit.columns(StageColumns.ONE, false, shortLines, 500, true))
    }

    /** A song of [lines] lines in verses of [verse], 100 px each at zoom 1, on a [page] px page. */
    private fun layout(lines: Int, verse: Int, page: Int) = { z: Float ->
        val measured = (0 until lines).flatMap { i ->
            val line = Pagination.Line((100 * z).toInt(), false)
            if (i > 0 && i % verse == 0) listOf(Pagination.Line((100 * z).toInt(), true), line) else listOf(line)
        }
        StageFit.Sheets(Pagination.paginate(measured, page).size, Pagination.cutsVerse(measured, page), false)
    }

    @Test
    fun `a short song grows up to the limit`() {
        assertEquals(1.5f, StageFit.autoZoom(0.6f, 1.5f, layout(4, 4, 1000)), 0.001f)
    }

    @Test
    fun `a song that fits one page shrinks to it and no further`() {
        val z = StageFit.autoZoom(0.6f, 1.5f, layout(14, 7, 1000))
        assertEquals(1, layout(14, 7, 1000)(z).count)
        assertTrue("usa a página: $z", z > 0.6f && layout(14, 7, 1000)(z + 0.05f).count > 1)
    }

    @Test
    fun `a long song keeps the fewest pages with every verse whole`() {
        val at = layout(40, 4, 1000)
        val z = StageFit.autoZoom(0.6f, 1.5f, at)
        assertEquals(at(0.6f).count, at(z).count)
        assertTrue(!at(z).cutsVerse)
    }

    @Test
    fun `lyrics without blank lines are one verse and only keep the pages`() {
        val at = layout(50, 50, 1000)
        assertTrue("o verso não cabe nem no mínimo", at(0.6f).cutsVerse)
        val z = StageFit.autoZoom(0.6f, 1.5f, at)
        assertEquals(at(0.6f).count, at(z).count)
    }

    @Test
    fun `a short first verse doesn't end up alone on the page`() {
        val verses = listOf(1, 8, 8)   // lines per verse: an intro and two long ones
        val at = { z: Float ->
            val measured = verses.flatMapIndexed { v, n ->
                val lines = List(n) { Pagination.Line((100 * z).toInt(), false) }
                if (v > 0) listOf(Pagination.Line((100 * z).toInt(), true)) + lines else lines
            }
            val pages = Pagination.paginate(measured, 1000)
            StageFit.Sheets(pages.size, Pagination.cutsVerse(measured, 1000), Pagination.thinFirstPage(measured, pages, 1000))
        }
        assertTrue("o mínimo aproveita a página", !at(0.6f).thinFirst)
        assertTrue(!at(StageFit.autoZoom(0.6f, 1.5f, at)).thinFirst)
    }

    @Test
    fun `the smallest size stays legible`() {
        assertEquals(22f / 30f, StageFit.autoMinZoom(30f), 0.001f)
        assertEquals(22f / 49f, StageFit.autoMinZoom(49f), 0.001f)
        assertEquals(1f, StageFit.autoMinZoom(18f), 0.001f)
        assertEquals(20f / 30f, StageFit.autoFewerZoom(30f), 0.001f)
    }

    @Test
    fun `measuring stays bounded`() {
        var calls = 0
        StageFit.autoZoom(0.6f, 1.5f) { calls++; layout(40, 4, 1000)(it) }
        assertTrue("$calls", calls <= 7)
    }

    @Test fun `the size only goes under the floor to keep a section whole`() {
        fun crossing(below: Float) = { z: Float -> StageFit.Sheets(3, false, crossesSheet = z > below) }
        assertEquals("nada atravessa", 0.5f, StageFit.autoFloor(0.5f, 0.4f) { StageFit.Sheets(3, false) }, 0f)
        assertEquals("atravessa de qualquer jeito", 0.5f, StageFit.autoFloor(0.5f, 0.4f, crossing(0.3f)), 0f)
        assertEquals("o maior que mantém inteira", 0.45f, StageFit.autoFloor(0.5f, 0.4f, crossing(0.45f)), 0.01f)
    }

    @Test fun `the size goes a bit lower only to save a page`() {
        fun pages(fitsBelow: Float) = { z: Float -> StageFit.Sheets(if (z > fitsBelow) 4 else 3, false) }
        assertEquals("poupa uma página", 0.46f, StageFit.autoPageFloor(0.5f, 0.45f, pages(0.46f)), 0.002f)
        assertEquals("nem no menor poupa", 0.5f, StageFit.autoPageFloor(0.5f, 0.45f, pages(0.4f)), 0f)
        assertEquals("sem espaço para descer", 0.5f, StageFit.autoPageFloor(0.5f, 0.5f, pages(0.46f)), 0f)
    }

    @Test fun `saving a page doesn't cut a verse or send a section over the turn`() {
        val cuts = { z: Float -> StageFit.Sheets(if (z > 0.46f) 4 else 3, cutsVerse = z <= 0.46f) }
        assertEquals(0.5f, StageFit.autoPageFloor(0.5f, 0.45f, cuts), 0f)
        val crosses = { z: Float -> StageFit.Sheets(if (z > 0.46f) 4 else 3, false, crossesSheet = z <= 0.46f) }
        assertEquals(0.5f, StageFit.autoPageFloor(0.5f, 0.45f, crosses), 0f)
    }

    @Test fun `what the lower size saved stays saved`() {
        val at = { z: Float -> StageFit.Sheets(if (z > 0.46f) 4 else 3, false) }
        val floor = StageFit.autoPageFloor(0.5f, 0.45f, at)
        assertEquals(3, at(StageFit.autoZoom(floor, 1f, at)).count)
    }

    @Test fun `growing doesn't send a section over the page turn`() {
        val zoom = StageFit.autoZoom(0.5f, 1f) { StageFit.Sheets(3, false, crossesSheet = it > 0.7f) }
        assertEquals(0.7f, zoom, 0.02f)
    }

    @Test fun `only the moment of the turn is told apart`() {
        val upright = StageFit.Room("384x832", 944, 1843)
        assertEquals("girou e o tamanho ainda é o velho", true, StageFit.midTurn(upright, upright.copy(screen = "832x384")))
        assertEquals("o tamanho novo chegou", false, StageFit.midTurn(upright.copy(screen = "832x384"), StageFit.Room("832x384", 2204, 674)))
        assertEquals("nada mudou", false, StageFit.midTurn(upright, upright))
    }

    @Test fun `a size is measured only once`() {
        var measures = 0
        val at = StageFit.measuredOnce { measures++; StageFit.Sheets(3, false, crossesSheet = it > 0.45f) }
        val floor = StageFit.autoFloor(0.5f, 0.4f, at)
        val before = measures
        at(floor)
        at(0.5f)
        assertEquals("o que já foi medido não mede de novo", before, measures)
    }
}
