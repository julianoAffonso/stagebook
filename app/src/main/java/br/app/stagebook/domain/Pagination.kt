package br.app.stagebook.domain

/**
 * Divide a letra em páginas do palco (o pedal vira a página inteira, sem rolagem).
 * Quebra entre estrofes; o título de seção vai junto com a estrofe dele. Só uma estrofe maior que a
 * página é cortada, por linha.
 */
object Pagination {
    /** Uma linha da letra já medida na largura da tela (a altura conta a quebra automática). */
    data class Line(val height: Int, val blank: Boolean)

    /** Faixas de linhas de cada página (sem linhas em branco na borda). */
    fun paginate(lines: List<Line>, pageHeight: Int): List<IntRange> {
        val blocks = blocks(lines)
        if (blocks.isEmpty()) return listOf(IntRange.EMPTY)
        val gap = lines.firstOrNull { it.blank }?.height ?: 0
        val pages = ArrayList<IntRange>()
        var start = -1
        var end = -1
        var used = 0

        fun flush() {
            if (start >= 0) pages += start..end
            start = -1; end = -1; used = 0
        }

        for (block in blocks) {
            val h = block.sumOf { lines[it].height }
            val need = if (start < 0) h else used + gap + h
            when {
                need <= pageHeight -> {
                    if (start < 0) start = block.first
                    end = block.last
                    used = need
                }
                h <= pageHeight -> {
                    flush()
                    start = block.first; end = block.last; used = h
                }
                else -> {
                    // estrofe maior que a página: corta por linha
                    flush()
                    for (i in block) {
                        val lh = lines[i].height
                        if (start >= 0 && used + lh > pageHeight) flush()
                        if (start < 0) start = i
                        end = i
                        used += lh
                    }
                }
            }
        }
        flush()
        return pages
    }

    /**
     * Folhas do palco com `columns` colunas: as colunas são preenchidas em ordem (esquerda, depois
     * direita), cada uma com a altura da página; o pedal vira a folha inteira.
     */
    fun sheets(lines: List<Line>, columnHeight: Int, columns: Int): List<List<IntRange>> =
        paginate(lines, columnHeight).chunked(columns.coerceAtLeast(1))

    /** Estrofes: linhas seguidas não vazias. */
    private fun blocks(lines: List<Line>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].blank) { i++; continue }
            val s = i
            while (i < lines.size && !lines[i].blank) i++
            out += s until i
        }
        return out
    }
}
