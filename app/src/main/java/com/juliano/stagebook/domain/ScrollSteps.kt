package com.juliano.stagebook.domain

/**
 * Modo rolagem do palco: cada aperto do pedal rola até a primeira linha que não cabia inteira (nenhuma linha fica cortada
 * entre um passo e outro). [Step.markY]/[Step.markHeight]: onde fica, na tela, a linha em que a parte nova começa, para
 * acender por um instante e o olho achar onde continuar. O último passo para no fim da música.
 */
object ScrollSteps {
    data class Step(val offset: Int, val markY: Int, val markHeight: Int)

    fun steps(lines: List<Pagination.Line>, viewport: Int): List<Step> {
        val first = Step(0, 0, 0)
        if (lines.isEmpty() || viewport <= 0) return listOf(first)
        val tops = IntArray(lines.size)
        var y = 0
        lines.forEachIndexed { i, l -> tops[i] = y; y += l.height }
        val max = (y - viewport).coerceAtLeast(0)
        val out = mutableListOf(first)
        var pos = 0
        while (pos < max) {
            var i = lines.indices.firstOrNull { tops[it] + lines[it].height > pos + viewport } ?: break
            // o destaque vai na primeira linha com texto (pula as linhas em branco entre estrofes)
            while (i < lines.lastIndex && lines[i].blank) i++
            var next = tops[i]
            if (next <= pos) next = pos + viewport   // uma linha maior que a tela: rola a tela inteira
            next = next.coerceAtMost(max)
            if (next <= pos) break
            out += Step(next, tops[i] - next, lines[i].height)
            pos = next
        }
        return out
    }
}
