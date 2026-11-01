package br.app.stagebook.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material icons (Apache-2.0) drawn right here, without the whole icon library. The color comes from the Icon's `tint`;
 * the black below is only the mask.
 */
object AppIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply { paths.forEach { addPath(pathData = PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) } }
            .build()

    /** Same, with the even-odd rule: an inner shape cuts a hole in the outer one (rings, letters with a hole). */
    private fun holedIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            // one path: the even-odd rule only cuts holes between shapes of the same path
            .addPath(pathData = PathParser().parsePathString(paths.joinToString(" ")).toNodes(), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
            .build()

    val Edit by lazy {
        icon("edit", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z")
    }
    val Delete by lazy {
        icon("delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    }
    val DragHandle by lazy { icon("drag", "M20,9H4v2h16V9zM4,15h16v-2H4v2z") }
    val Add by lazy { icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val Menu by lazy { icon("menu", "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z") }
    val MenuOpen by lazy {
        icon("menuopen", "M3,18h13v-2H3v2zM3,13h10v-2H3v2zM3,6v2h13V6H3zM21,15.59L17.42,12 21,8.41 19.59,7l-5,5 5,5L21,15.59z")
    }
    val Search by lazy {
        icon("search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    }
    val Play by lazy { icon("play", "M8,5v14l11,-7z") }
    val Back by lazy { icon("back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    val Close by lazy {
        icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    }
    // outlined floppy disk: save (the check looked like "done"/"ok")
    val Save by lazy {
        icon("save", "M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM19,19H5V5h11.17L19,7.83V19zM12,12c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3zM6,6h9v4H6z")
    }
    val Check by lazy { icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val MusicNote by lazy {
        icon("music", "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z")
    }
    val QueueMusic by lazy {
        icon("queue", "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z")
    }
    val Tune by lazy {
        icon("tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")
    }
    val QrCode by lazy {
        icon("qr", "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13zM19,9h-4V5h4V9zM19,19h2v2h-2V19zM13,13h2v2h-2V13zM15,15h2v2h-2V15zM13,17h2v2h-2V17zM15,19h2v2h-2V19zM17,17h2v2h-2V17zM17,13h2v2h-2V13zM19,15h2v2h-2V15z")
    }
    val Bluetooth by lazy {
        icon("bluetooth", "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z")
    }
    val Public by lazy {
        icon("web", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z")
    }
    val Download by lazy { icon("download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val Upload by lazy { icon("upload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }
    val FormatSize by lazy { icon("size", "M9,4v3h5v12h3V7h5V4H9zM3,12h3v7h3v-7h3V9H3v3z") }
    // select all (selection top bar)
    val SelectAll by lazy {
        icon("selectall", "M3,5h2L5,3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2L3,11v2zM7,21h2v-2L7,19v2zM3,9h2L5,7L3,7v2zM13,3h-2v2h2L13,3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2zM5,21v-2L3,19c0,1.1 0.9,2 2,2zM3,17h2v-2L3,15v2zM9,3L7,3v2h2L9,3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2zM19,9h2L21,7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2L17,3h-2v2zM7,17h10L17,7L7,7v10zM9,9h6v6L9,15L9,9z")
    }
    // tag (label): song tags
    val Label by lazy {
        icon("label", "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19c0.67,0 1.27,-0.33 1.63,-0.84L22,12l-4.37,-6.16z")
    }
    // add to a list: add songs to a setlist
    val PlaylistAdd by lazy { icon("playlistadd", "M14,10H3v2h11v-2zM14,6H3v2h11V6zM18,14v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM3,16h7v-2H3v2z") }
    val ListBulleted by lazy {
        icon("list", "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5zM4,16.5c-0.83,0 -1.5,0.68 -1.5,1.5s0.68,1.5 1.5,1.5 1.5,-0.68 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM7,19h14v-2H7v2zM7,13h14v-2H7v2zM7,5v2h14V5H7z")
    }
    val Brightness by lazy {
        icon("brightness", "M20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69L23.31,12 20,8.69zM12,18c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6 6,2.69 6,6 -2.69,6 -6,6zM12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z")
    }
    val Folder by lazy {
        icon("folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }
    val MoreVert by lazy {
        icon("more", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    }
    val ChevronRight by lazy { icon("chevron", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val Warning by lazy { icon("warning", "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z") }
    // capo: four strings on the neck and the bar clamped on top (there's no Material icon for it)
    val Capo by lazy {
        icon("capo", "M6,3h1.5v18H6zM10,3h1.5v18H10zM14,3h1.5v18H14zM18,3h1.5v18H18zM3.5,9.5h17a1.5,1.5 0,0 1,1.5 1.5v1a1.5,1.5 0,0 1,-1.5 1.5h-17a1.5,1.5 0,0 1,-1.5 -1.5v-1a1.5,1.5 0,0 1,1.5 -1.5z")
    }
    val Remove by lazy { icon("remove", "M19,13H5v-2h14v2z") }
    val ArrowUp by lazy { icon("up", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z") }
    val ArrowDown by lazy { icon("down", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z") }
    val Visibility by lazy {
        icon("visibility", "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z")
    }
    val DropDown by lazy { icon("dropdown", "M7,10l5,5 5,-5z") }
    val DropUp by lazy { icon("dropup", "M7,14l5,-5 5,5z") }
    // outlined ⓘ (only the outline): settings explanations, kept quiet (user's request, 29/10)
    val InfoOutline by lazy { icon("infooutline", "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z") }
    val ExpandMore by lazy { icon("expandmore", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6z") }
    val ExpandLess by lazy { icon("expandless", "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z") }
    val ChevronLeft by lazy { icon("chevronleft", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z") }
    val CloudOff by lazy {
        icon("cloudoff", "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96zM3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27zM7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73z")
    }
    val Info by lazy {
        icon("info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z")
    }
    // translate (文A), the language icon in Google's apps: not confused with the import globe
    val Translate by lazy {
        icon("translate", "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z")
    }
    val Undo by lazy {
        icon("undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }
    // rounded play and pause (big auto-scroll button)
    val PlayRounded by lazy { icon("playrounded", "M8,19.14V4.86c0,-0.8 0.88,-1.28 1.55,-0.84l11.2,7.14c0.62,0.39 0.62,1.29 0,1.69l-11.2,7.14C8.88,20.42 8,19.94 8,19.14z") }
    val PauseRounded by lazy { icon("pauserounded", "M8,19c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v10c0,1.1 0.9,2 2,2zM14,7v10c0,1.1 0.9,2 2,2s2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2z") }
    // metronome: trapezoid body and tilted pendulum
    // slideshow (a screen with play): the editor's stage preview, Google's "present" symbol (an eye reads as show/hide)
    val Slideshow by lazy {
        icon("slideshow", "M10,8v8l5,-4 -5,-4zM19,3L5,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM19,19L5,19L5,5h14v14z")
    }
    // stage columns: the lyrics as one block of lines (no split), in two side-by-side blocks, and an "A" in a ring
    val OneColumn by lazy { icon("onecolumn", "M3,5h18v2H3zM3,9h18v2H3zM3,13h18v2H3zM3,17h12v2H3z") }
    val TwoColumns by lazy {
        icon("twocolumns", "M3,5h8v2H3zM3,9h8v2H3zM3,13h8v2H3zM3,17h6v2H3zM13,5h8v2h-8zM13,9h8v2h-8zM13,13h8v2h-8zM13,17h5v2h-5z")
    }
    val AutoColumns by lazy {
        holedIcon(
            "autocolumns",
            "M12,2a10,10 0,1 0,0.01 0zM12,4a8,8 0,1 1,-0.01 0z",
            "M11,7h2l3.5,10h-2l-0.8,-2.3h-3.4L9.5,17h-2zM10.9,13h2.2L12,9.7z",
        )
    }
    // share: Android's standard symbol (an up arrow read as "upload a file")
    val Share by lazy {
        icon("share", "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z")
    }
    // pedal: a page turner box with two buttons (Bluetooth said nothing about the USB MIDI pedal)
    val Pedal by lazy {
        holedIcon(
            "pedal",
            "M4,8h16a2,2 0,0 1,2 2v6a2,2 0,0 1,-2 2H4a2,2 0,0 1,-2 -2v-6a2,2 0,0 1,2 -2z",
            "M8,11a2,2 0,1 0,0.01 0z", "M16,11a2,2 0,1 0,0.01 0z",
        )
    }
    // computer: the editor on the computer (T37)
    val Computer by lazy { icon("computer", "M20,18c1.1,0 1.99,-0.9 1.99,-2L22,6c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2H0v2h24v-2h-4zM4,6h16v10H4V6z") }
    // coffee cup: the support button (a generic icon, no brand)
    val Coffee by lazy {
        icon("coffee", "M20,3H4v10c0,2.21 1.79,4 4,4h6c2.21,0 4,-1.79 4,-4v-3h2c1.11,0 2,-0.89 2,-2V5C22,3.89 21.11,3 20,3zM20,8h-2V5h2V8zM4,19h16v2H4V19z")
    }
    val Eco by lazy {
        icon("eco", "M6.05,8.05c-2.73,2.73 -2.73,7.15 -0.02,9.88c1.47,-3.4 4.09,-6.24 7.36,-7.93c-2.77,2.34 -4.71,5.61 -5.39,9.32c2.6,1.23 5.8,0.78 7.95,-1.37C19.43,14.47 20,4 20,4S9.53,4.57 6.05,8.05z")
    }
    val Metronome by lazy {
        icon(
            "metronome",
            "M9.2,2L11.2,2L6.8,21L4.8,21z", "M12.8,2L14.8,2L19.2,21L17.2,21z", "M9.2,2H14.8V4H9.2z", "M4.5,19.5H19.5V22H4.5z",
            "M10.8,16L16.4,5.6L18,6.5L12.4,16.9z",
        )
    }
    val Stop by lazy { icon("stop", "M8,6h8c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2H8c-1.1,0 -2,-0.9 -2,-2V8c0,-1.1 0.9,-2 2,-2z") }
    // two down arrows: stage auto-scroll
    val AutoScroll by lazy { icon("autoscroll", "M5,2.5L12,9.5L19,2.5L21,4.5L12,13.5L3,4.5z", "M5,10.5L12,17.5L19,10.5L21,12.5L12,21.5L3,12.5z") }   // thick stroke (request from 29/10)
    val Pause by lazy { icon("pause", "M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z") }
    val Group by lazy {
        icon("group", "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z")
    }
    val Sync by lazy {
        icon("sync", "M12,4L12,1L8,5l4,4L12,6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z")
    }
    val Copy by lazy {
        icon("copy", "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z")
    }
    val VolumeUp by lazy {
        icon("volume", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
    }
    val VolumeOff by lazy {
        icon("mute", "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z")
    }
}
