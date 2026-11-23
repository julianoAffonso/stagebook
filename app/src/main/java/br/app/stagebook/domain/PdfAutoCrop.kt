package br.app.stagebook.domain

/**
 * The trim that takes a page's blank margins off (user, 23/11: "an option to crop by itself, taking the white borders
 * off"): the smallest frame that holds everything drawn on the page, with a little air around it. It reads the page as
 * drawn on white paper, small: a margin is found well enough at a few hundred pixels.
 */
object PdfAutoCrop {
    /** A frame in fractions of the page. */
    data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Air left around what is drawn, as a fraction of the page: a frame touching the notes reads badly. */
    const val AIR = 0.015f

    // a pixel this much darker than white is drawn; lighter is paper (and a scan's grey haze)
    private const val INK_BELOW = 200
    // a row or column needs this share of drawn pixels to count: a speck of dust on a scan is not content
    private const val SPECK = 0.004f

    /**
     * The frame around what is drawn on a page of [width] x [height] ARGB [pixels] (row by row; a clear pixel is paper),
     * or null when the page is blank.
     */
    fun frame(pixels: IntArray, width: Int, height: Int): Frame? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val rows = IntArray(height)
        val columns = IntArray(width)
        for (y in 0 until height) {
            val at = y * width
            for (x in 0 until width) {
                val p = pixels[at + x]
                val alpha = p ushr 24
                if (alpha < 128) continue
                // the lightness the eye sees, from the three colours
                val light = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (light < INK_BELOW) { rows[y]++; columns[x]++ }
            }
        }
        val rowNeeds = (width * SPECK).toInt().coerceAtLeast(1)
        val columnNeeds = (height * SPECK).toInt().coerceAtLeast(1)
        val top = rows.indexOfFirst { it >= rowNeeds }
        if (top < 0) return null
        val bottom = rows.indexOfLast { it >= rowNeeds }
        val left = columns.indexOfFirst { it >= columnNeeds }
        if (left < 0) return null
        val right = columns.indexOfLast { it >= columnNeeds }
        return Frame(
            (left.toFloat() / width - AIR).coerceAtLeast(0f), (top.toFloat() / height - AIR).coerceAtLeast(0f),
            ((right + 1).toFloat() / width + AIR).coerceAtMost(1f), ((bottom + 1).toFloat() / height + AIR).coerceAtMost(1f),
        )
    }

    /** One frame for several pages: it holds what each of them has (blank pages aside). Null when all are blank. */
    fun around(frames: List<Frame?>): Frame? {
        val some = frames.filterNotNull()
        if (some.isEmpty()) return null
        return Frame(some.minOf { it.left }, some.minOf { it.top }, some.maxOf { it.right }, some.maxOf { it.bottom })
    }

    /** Which pages to read for a trim of all [count] pages: every one of a short PDF, [most] spread evenly over a long one. */
    fun sample(count: Int, most: Int = 24): List<Int> =
        if (count <= most) (0 until count).toList() else (0 until most).map { it * (count - 1) / (most - 1) }.distinct()
}
