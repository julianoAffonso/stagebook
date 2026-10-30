package br.app.stagebook.domain

import br.app.stagebook.data.model.StageColumns

/** How large the lyrics can grow without the longest word having to break in the middle. */
object StageFit {
    private const val MARGIN = 0.98f   // slack for measurement rounding

    /** Minimum share of lines that must fit in half the width (without wrapping) to use 2 columns. */
    const val SHORT_LINES_SHARE = 0.9

    /**
     * How many columns on stage. Automatic, song by song: if it fits on one page with 1 column, it stays 1; otherwise 2
     * columns when nearly every line fits in half the width without wrapping and the zoom fits in half the screen
     * without breaking a word.
     *
     * @param lineWidthsPx width of each line (no section titles or blank lines) on a single line
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

    /** Max zoom (over the default size) for a word of `widestWordPx` to fit in `widthPx`. */
    fun zoomToFit(widestWordPx: Int, widthPx: Int): Float =
        if (widestWordPx <= 0 || widthPx <= 0) Float.MAX_VALUE else widthPx * MARGIN / widestWordPx
}
