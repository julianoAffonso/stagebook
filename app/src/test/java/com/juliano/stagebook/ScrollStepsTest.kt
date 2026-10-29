package com.juliano.stagebook

import com.juliano.stagebook.domain.Pagination.Line
import com.juliano.stagebook.domain.ScrollSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollStepsTest {
    private fun lines(vararg h: Int) = h.map { Line(it, blank = it == 10) }

    @Test fun `cabe tudo - um passo só`() = assertEquals(1, ScrollSteps.steps(lines(50, 50), 300).size)

    @Test fun `rola até a primeira linha que não cabia inteira e acende ela no topo`() {
        // linhas de 100 numa tela de 250: a 3ª (top 200) não cabia inteira
        val steps = ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250)
        assertEquals(200, steps[1].offset)
        assertEquals(0, steps[1].markY)
        assertEquals(100, steps[1].markHeight)
    }

    @Test fun `o último passo para no fim da música e a linha acesa fica mais abaixo`() {
        val steps = ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250)
        val last = steps.last()
        assertEquals(250, last.offset)          // 500 de música - 250 de tela
        assertEquals(400 - 250, last.markY)     // a 5ª linha (top 400) que não cabia no passo anterior
    }

    @Test fun `linha em branco entre estrofes não recebe o destaque`() {
        val steps = ScrollSteps.steps(lines(100, 100, 10, 100, 100, 100), 205)
        assertEquals(210, steps[1].offset)      // pula a linha em branco (top 200) e vai para a de texto (top 210)
    }

    @Test fun `linha maior que a tela rola a tela inteira`() {
        val steps = ScrollSteps.steps(lines(600, 100), 250)
        assertEquals(250, steps[1].offset)
    }

    @Test fun `pausa no meio - a posição vira passo e o próximo parte dela`() {
        // linhas de 100, tela de 250; pausou em 130 (entre os passos 0 e 200)
        val steps = ScrollSteps.steps(lines(100, 100, 100, 100, 100, 100), 250, anchor = 130)
        assertEquals(listOf(0, 130, 300, 350), steps.map { it.offset })
        assertEquals(0, steps[1].markHeight)    // o ponto da pausa não acende: a letra não se mexe
        assertEquals(300, steps[2].offset)      // a 4ª linha (top 300) não cabia inteira a partir de 130 (vai até 380)
    }

    @Test fun `pausa no fim ou num passo exato não duplica`() {
        val base = ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250).map { it.offset }
        assertEquals(base, ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250, anchor = 200).map { it.offset })
        assertEquals(base.last(), ScrollSteps.steps(lines(100, 100, 100, 100, 100), 250, anchor = 9999).last().offset)
    }
}
