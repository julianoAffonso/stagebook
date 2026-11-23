package br.app.stagebook.ui.stage

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

internal const val PAGE_IN_MS = 220   // the new page coming in (and the dragged one going back with it)
private const val PAGE_OUT_MS = 160

/**
 * Turning a page on the stage, the same for lyrics and for a PDF (user, 23/11: the PDF's pages just swapped): the new
 * page comes in from the side of the movement while the old one leaves by the other.
 */
internal fun AnimatedContentTransitionScope<*>.pageTurn(forward: Boolean, sheet: Boolean = false): ContentTransform {
    val dir = if (forward) 1 else -1
    val turn = if (sheet) {
        // a PDF page is an opaque sheet: fading two of them over each other left a washed band at the edges (user,
        // 23/11). One pushes the other the whole way, side by side, both solid
        slideInHorizontally(tween(PAGE_IN_MS)) { it * dir } togetherWith slideOutHorizontally(tween(PAGE_IN_MS)) { -it * dir }
    } else {
        (slideInHorizontally(tween(PAGE_IN_MS)) { it / 6 * dir } + fadeIn(tween(PAGE_IN_MS))) togetherWith
            (slideOutHorizontally(tween(PAGE_OUT_MS)) { -it / 6 * dir } + fadeOut(tween(PAGE_OUT_MS)))
    }
    // the pages are the same size: no size to animate, and no cut at the page's own width while it slides
    return turn.using(SizeTransform(clip = false))
}
