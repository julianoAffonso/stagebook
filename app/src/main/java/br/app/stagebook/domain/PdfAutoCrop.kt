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

    // on white paper, a pixel this much darker than white is drawn; lighter is paper (and a light grey haze)
    private const val INK_BELOW = 200
    // a row or column needs this share of drawn pixels to count: a speck of dust on a scan is not content
    private const val SPECK = 0.004f

    // a scan's paper is rarely white: what counts as drawn is this much darker than the paper itself
    private const val INK_UNDER_PAPER = 30
    // the dark band a scanner leaves around the sheet: taken off when it runs along at least this share of the page's edge
    private const val BORDER_SHARE = 0.5f

    /**
     * The frame around what is drawn on a page of [width] x [height] ARGB [pixels] (row by row; a clear pixel is paper),
     * or null when the page is blank. A scanned page is read as one: its paper may be grey (the darkest that still counts
     * as paper is found from the page itself), and the dark band around the sheet is not content.
     */
    fun frame(pixels: IntArray, width: Int, height: Int): Frame? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val size = width * height
        // the lightness the eye sees, from the three colours; a clear pixel is white paper
        val light = IntArray(size) { i ->
            val p = pixels[i]
            if ((p ushr 24) < 128) 255 else (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        // the paper: the most common lightness among the light half of the page
        val seen = IntArray(256)
        for (v in light) seen[v]++
        var paper = 255
        for (v in 128..255) if (seen[v] > seen[paper]) paper = v
        val inkBelow = minOf(INK_BELOW, paper - INK_UNDER_PAPER)
        val ink = BooleanArray(size) { light[it] < inkBelow }
        dropScannerBand(ink, width, height)
        val rows = IntArray(height)
        val columns = IntArray(width)
        for (y in 0 until height) for (x in 0 until width) if (ink[y * width + x]) { rows[y]++; columns[x]++ }
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

    /**
     * Takes out of [ink] the dark band around a scanned sheet: everything dark that is joined to the page's edge, when
     * the edge is dark along most of its length. A line of the music that merely reaches the edge is left alone, and so
     * is a page dark all over (nothing would be left of it).
     */
    private fun dropScannerBand(ink: BooleanArray, width: Int, height: Int) {
        val edge = ArrayList<Int>(2 * (width + height))
        for (x in 0 until width) { edge += x; edge += (height - 1) * width + x }
        for (y in 1 until height - 1) { edge += y * width; edge += y * width + width - 1 }
        val dark = edge.count { ink[it] }
        if (dark < edge.size * BORDER_SHARE) return
        val band = BooleanArray(ink.size)
        val queue = IntArray(ink.size)
        var head = 0; var tail = 0
        for (i in edge) if (ink[i] && !band[i]) { band[i] = true; queue[tail++] = i }
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            if (x > 0 && ink[i - 1] && !band[i - 1]) { band[i - 1] = true; queue[tail++] = i - 1 }
            if (x < width - 1 && ink[i + 1] && !band[i + 1]) { band[i + 1] = true; queue[tail++] = i + 1 }
            if (i >= width && ink[i - width] && !band[i - width]) { band[i - width] = true; queue[tail++] = i - width }
            if (i < ink.size - width && ink[i + width] && !band[i + width]) { band[i + width] = true; queue[tail++] = i + width }
        }
        if (tail == ink.count { it }) return   // all of it is joined to the edge: a dark page, not a band
        for (k in 0 until tail) ink[queue[k]] = false
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
