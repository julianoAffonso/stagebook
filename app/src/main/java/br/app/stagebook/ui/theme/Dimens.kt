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
    val icon = 24.dp            // a standard icon beside text
    val railLogo = 48.dp        // the app icon on top of the tablet's rail
    val fader = 200.dp          // vertical fader height (mixer and EQ on tablets)
    val meter = 8.dp            // width of the live meter bar
    val button = 56.dp          // main buttons (finger on stage, tablet far away)
    val row = 72.dp             // song row
    val rowDense = 56.dp        // a song row where many must fit (the setlist over the stage); still above the touch minimum
    val breakDense = 32.dp      // the break between them there: it isn't tapped
    val swipeTrigger = 56.dp    // row opened by dragging: only the icon button (24) + margin (16) on each side
    val swatch = 48.dp
    val strip = 60.dp           // least height of the band's strip, in every state: they all look equally tall
    val progressStroke = 2.dp   // a small circular progress indicator (the strip's search)
    val artistField = 200.dp    // artist or band (tablet): names are short; longer ones scroll inside the field
    val yearField = 96.dp       // song year: always 4 digits
    val tempoField = 88.dp      // BPM: up to 3 digits
    val tagFilter = 240.dp      // tag filter beside the search (tablet)
    val tagFilterNarrow = 168.dp   // and on phones ("Recentes" with the text 15% bigger needs it)
    val tagMenu = 320.dp        // its open list, wider than the field on phones
    val fontPreview = 160.dp    // fixed box for the lyrics size preview (doesn't push the screen)
    val fontPreviewWide = 300.dp   // the same box on a wide screen (tablet): the sample stays near its real size
    val wideScreen = 600.dp
    val settingsList = 360.dp   // settings section list on tablets
    val contentMax = 840.dp     // max content width on a tablet in landscape
    val pageDot = 28.dp         // numbered page dot in the stage footer
    val swipe = 64.dp           // dragging on stage: from here on, turns the page toward the drag
    val railBreakpoint = 600.dp // from here on, side rail instead of a bottom bar
}
