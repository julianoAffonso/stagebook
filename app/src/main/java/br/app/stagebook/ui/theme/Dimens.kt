package br.app.stagebook.ui.theme

import androidx.compose.ui.unit.dp

/** 8dp grid. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Sizes {
    val touch = 48.dp           // minimum touch target
    val fader = 200.dp          // vertical fader height (mixer and EQ on tablets)
    val meter = 8.dp            // width of the live meter bar
    val button = 56.dp          // main buttons (finger on stage, tablet far away)
    val row = 72.dp             // song row
    val swipeTrigger = 56.dp    // row opened by dragging: only the icon button (24) + margin (16) on each side
    val swatch = 48.dp
    val tagFilter = 240.dp      // tag filter beside the search (tablet)
    val tagFilterNarrow = 144.dp   // and on phones
    val fontPreview = 160.dp    // fixed box for the lyrics size preview (doesn't push the screen)
    val fontPreviewWide = 300.dp   // the same box on a wide screen (tablet): the sample stays near its real size
    val wideScreen = 600.dp
    val settingsList = 360.dp   // settings section list on tablets
    val contentMax = 840.dp     // max content width on a tablet in landscape
    val pageDot = 28.dp         // numbered page dot in the stage footer
    val swipe = 64.dp           // dragging on stage: from here on, turns the page toward the drag
    val railBreakpoint = 600.dp // from here on, side rail instead of a bottom bar
}
