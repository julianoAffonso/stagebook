package br.app.stagebook.domain

import kotlin.math.pow

/**
 * Contrast between two colors (WCAG ratio, from 1 to 21). Below [LEGIBLE] the text gets hard to read on stage: settings
 * warn (color equal to the background, or too similar, like dark gray on black).
 */
object Contrast {
    const val LEGIBLE = 3.0

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    fun legible(text: Int, background: Int) = ratio(text, background) >= LEGIBLE
}
