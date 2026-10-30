package br.app.stagebook.domain

import br.app.stagebook.data.model.StageColumns

/** Até onde a letra pode crescer sem que a palavra mais longa precise ser quebrada no meio. */
object StageFit {
    private const val MARGIN = 0.98f   // folga para arredondamento da medida

    /** Fração mínima das frases que precisam caber em meia largura (sem quebrar linha) para usar 2 colunas. */
    const val SHORT_LINES_SHARE = 0.9

    /**
     * Quantas colunas no palco. Automático, música por música: se ela cabe numa página com 1 coluna,
     * fica 1; senão 2 colunas quando quase todas as frases cabem em meia largura sem quebrar linha e o
     * zoom cabe em meia tela sem quebrar palavra.
     *
     * @param lineWidthsPx largura de cada frase (sem títulos de seção nem linhas em branco) numa linha só
     */
    fun columns(
        setting: StageColumns,
        fitsOnePageInOneColumn: Boolean,
        lineWidthsPx: List<Int>,
        columnWidthPx: Int,
        zoomFitsTwoColumns: Boolean,
    ): Int = when (setting) {
        StageColumns.ONE -> 1
        StageColumns.TWO -> 2
        StageColumns.AUTO -> {
            val short = lineWidthsPx.count { it <= columnWidthPx }
            val mostlyShort = lineWidthsPx.isNotEmpty() && short >= lineWidthsPx.size * SHORT_LINES_SHARE
            if (!fitsOnePageInOneColumn && mostlyShort && zoomFitsTwoColumns) 2 else 1
        }
    }

    /** Zoom máximo (sobre o tamanho padrão) para uma palavra de `widestWordPx` caber em `widthPx`. */
    fun zoomToFit(widestWordPx: Int, widthPx: Int): Float =
        if (widestWordPx <= 0 || widthPx <= 0) Float.MAX_VALUE else widthPx * MARGIN / widestWordPx
}
