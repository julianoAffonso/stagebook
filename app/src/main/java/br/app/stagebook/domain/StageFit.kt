package br.app.stagebook.domain

import br.app.stagebook.data.model.StageColumns

/** How large the lyrics can grow without the longest word having to break in the middle. */
object StageFit {
    private const val MARGIN = 0.98f   // slack for measurement rounding

    /** Minimum share of lines that must fit in half the width (without wrapping) to use 2 columns. */
    const val SHORT_LINES_SHARE = 0.9

    /** Goes up when the calculation changes: what songs have saved was found the old way. */
    const val SIZES_VERSION = 7

    /** Largest automatic zoom over the size in Settings. */
    const val AUTO_MAX_ZOOM = 1.5f
    private const val AUTO_MIN_FONT = 22f
    private const val AUTO_WHOLE_FONT = 18f   // only to keep a section on one sheet
    private const val AUTO_STEPS = 5

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

    /** How a song lays out at some zoom. */
    data class Sheets(val count: Int, val cutsVerse: Boolean, val thinFirst: Boolean = false, val crossesSheet: Boolean = false)

    /** Smallest automatic zoom for lyrics at [fontSp]: the page comes first, down to a size still legible on stage. */
    fun autoMinZoom(fontSp: Float): Float = minOf(1f, AUTO_MIN_FONT / fontSp)

    /** Smallest zoom that may be used to keep a section whole. */
    fun autoWholeZoom(fontSp: Float): Float = minOf(1f, AUTO_WHOLE_FONT / fontSp)

    /**
     * Where the automatic zoom starts: [min], or lower down to [whole] when a section would cross a page turn and there
     * it doesn't. A section that crosses anyway keeps [min]: shrinking wouldn't save it.
     */
    fun autoFloor(min: Float, whole: Float, at: (Float) -> Sheets): Float {
        if (whole >= min || !at(min).crossesSheet || at(whole).crossesSheet) return min
        var low = whole
        var high = min
        repeat(AUTO_STEPS) {
            val mid = (low + high) / 2
            if (at(mid).crossesSheet) high = mid else low = mid
        }
        return low
    }

    /**
     * Automatic zoom for a song nobody zoomed: the largest size with the fewest pages reachable, no verse cut. A song
     * with a verse too long for the page even at [min] (lyrics with no blank lines are one verse) only keeps the pages.
     * [at] measures the whole song, the expensive part: at most [AUTO_STEPS] + 2 calls.
     */
    fun autoZoom(min: Float, max: Float, at: (Float) -> Sheets): Float {
        if (max <= min) return max
        val floor = at(min)
        fun fits(s: Sheets) = s.count <= floor.count && (floor.cutsVerse || !s.cutsVerse) && (floor.thinFirst || !s.thinFirst) &&
            (floor.crossesSheet || !s.crossesSheet)
        if (fits(at(max))) return max
        var low = min
        var high = max
        repeat(AUTO_STEPS) {
            val mid = (low + high) / 2
            if (fits(at(mid))) low = mid else high = mid
        }
        return low
    }

    /** Max zoom (over the default size) for a word of `widestWordPx` to fit in `widthPx`. */
    fun zoomToFit(widestWordPx: Int, widthPx: Int): Float =
        if (widestWordPx <= 0 || widthPx <= 0) Float.MAX_VALUE else widthPx * MARGIN / widestWordPx
}
