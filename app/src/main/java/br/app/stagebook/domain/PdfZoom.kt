package br.app.stagebook.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The zoom of a PDF page on the stage: how far it goes, how far the page can be moved and at what size to draw it.
 * The page is drawn at the width it takes on the screen times a whole number (its [bucket]), so it stays sharp when
 * enlarged, but never past [MAX_PIXELS]: a weak tablet has no room for a page of 100 MB.
 */
object PdfZoom {
    const val MAX_SCALE = 4f
    /** The most pixels a drawn page may have (about 24 MB as a bitmap). */
    const val MAX_PIXELS = 6_000_000L

    /** The largest zoom for a page that takes [fitW] x [fitH] pixels on the screen: sharp up to what [MAX_PIXELS] allows, at least 1.5. */
    fun maxScale(fitW: Int, fitH: Int): Float {
        val room = sqrt(MAX_PIXELS.toDouble() / (fitW.toLong() * fitH).coerceAtLeast(1)).toFloat()
        return room.coerceIn(1.5f, MAX_SCALE)
    }

    /** The whole number of times the fitted width to draw at, for a zoom of [scale] (1 = as it fits). */
    fun bucket(scale: Float, maxScale: Float): Int =
        min(ceil(scale - 0.001f).toInt(), floor(maxScale).toInt()).coerceAtLeast(1)

    /** The zoom after a pinch of [change], kept between 1 and [maxScale]. */
    fun scaled(scale: Float, change: Float, maxScale: Float): Float = (scale * change).coerceIn(1f, maxScale)

    /**
     * Where the page can sit: moved by [offset] from the centre, it never leaves a gap beside it while it is larger than
     * the box ([boxW] x [boxH]); smaller than the box, it stays centred.
     */
    fun clamp(offset: Pair<Float, Float>, scale: Float, fitW: Float, fitH: Float, boxW: Float, boxH: Float): Pair<Float, Float> {
        val slackX = max(0f, (fitW * scale - boxW) / 2f)
        val slackY = max(0f, (fitH * scale - boxH) / 2f)
        return offset.first.coerceIn(-slackX, slackX) to offset.second.coerceIn(-slackY, slackY)
    }
}
