package com.juliano.stagebook.domain

/**
 * Modo rolagem do palco: cada aperto do pedal rola até a primeira linha que não cabia inteira (nenhuma linha fica cortada
 * entre um passo e outro). [Step.markY]/[Step.markHeight]: onde fica, na tela, a linha em que a parte nova começa, para
 * acender por um instante e o olho achar onde continuar. O último passo para no fim da música.
 *
 * [anchor]: onde a rolagem automática pausou. Os passos seguintes partem dali (sem drift: o próximo pedal vai para a
 * primeira linha que não cabia a partir da posição real, não de um passo calculado lá do começo).
 */
object ScrollSteps {
    data class Step(val offset: Int, val markY: Int, val markHeight: Int)

    fun steps(lines: List<Pagination.Line>, viewport: Int, anchor: Int = 0): List<Step> {
        val first = Step(0, 0, 0)
        if (lines.isEmpty() || viewport <= 0) return listOf(first)
        val tops = IntArray(lines.size)
        var y = 0
        lines.forEachIndexed { i, l -> tops[i] = y; y += l.height }
        val max = (y - viewport).coerceAtLeast(0)
        val stop = anchor.coerceIn(0, max)
        val out = mutableListOf(first)
        var pos = 0
        var anchored = stop == 0
        while (pos < max) {
            var i = lines.indices.firstOrNull { tops[it] + lines[it].height > pos + viewport } ?: break
            // o destaque vai na primeira linha com texto (pula as linhas em branco entre estrofes)
            while (i < lines.lastIndex && lines[i].blank) i++
            var next = tops[i]
            if (next <= pos) next = pos + viewport   // uma linha maior que a tela: rola a tela inteira
            next = next.coerceAtMost(max)
            if (next <= pos) break
            // passou do ponto da pausa: ele entra como passo (sem destaque: a letra não se mexe) e a conta segue dali
            if (!anchored && next >= stop) {
                anchored = true
                if (next != stop) { out += Step(stop, 0, 0); pos = stop; continue }
            }
            out += Step(next, tops[i] - next, lines[i].height)
            pos = next
        }
        return out
    }
}
