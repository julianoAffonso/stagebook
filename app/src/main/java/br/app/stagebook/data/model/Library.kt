package br.app.stagebook.data.model

import androidx.compose.runtime.Immutable
import br.app.stagebook.domain.PedalAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The library saved in stagebook.json.

/** A song's stage zoom on one screen, over the size in Settings. [fit] empty = a pinch, the person's choice. */
@Immutable
@Serializable
data class ScreenZoom(
    val zoom: Float,
    val columns: Int = 0,
    val fit: String = "",   // calculated: what it was calculated for; lyrics or look changed, it's calculated again
)

/** A screen, as zooms are kept: its size says the position too, upright and on its side are two screens. */
fun screenKey(widthDp: Int, heightDp: Int) = "${widthDp}x$heightDp"

@Immutable
@Serializable
data class Song(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val year: String = "",
    val text: String = "",
    val zoom: Float = 1f,   // only old libraries: a pinch from when a song had one zoom for every screen
    val zooms: Map<String, ScreenZoom> = emptyMap(),   // stage zoom by screen size and position, see screenKey
    // backing track file copied into the app (name in filesDir/tracks); this device only: left out of export/QR
    val track: String = "",
    val trackName: String = "",   // the track file's own name when it was attached ("" = unknown), shown in the editor
    // this song's own mix, changed in the mixer over the stage (null = the general one); this device only, like the track
    val mix: br.app.stagebook.domain.SongMix? = null,
    // free tags (Nacional, Internacional, Rock…), to filter the lists and the setlist picker (vocalist, 01/11)
    val tags: List<String> = emptyList(),
    // MIDI sent when the song shows on stage and when it leaves (user, 07/11); goes in the backup, not to the band
    val midi: br.app.stagebook.domain.SongMidi? = null,
)

@Immutable
@Serializable
data class Setlist(
    val id: String = "",
    val name: String = "",
    val items: List<String> = emptyList(),   // song ids, in order
) {
    companion object {
        /** A new setlist with no name given: the date and time ("01/11/2026 21:30"). */
        fun defaultName(): String = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT).format(java.util.Date())
    }
}

@Serializable
enum class SectionLang {
    @SerialName("pt") PT,
    @SerialName("en") EN,
    @SerialName("orig") ORIGINAL,
}

@Serializable
/** Stage font: only the ones that come with Android (nothing to download; the Fire has no Google Play Services). */
enum class StageFont {
    @SerialName("default") DEFAULT,
    @SerialName("condensed") CONDENSED,
    @SerialName("serif") SERIF,
    @SerialName("mono") MONO,
}

/** Instrument for chord diagrams. Acoustic and electric guitar share the tuning (same diagrams). */
enum class Instrument(val chordPro: String) {
    @SerialName("violao") ACOUSTIC_GUITAR("guitar"),
    @SerialName("guitarra") ELECTRIC_GUITAR("guitar"),
    @SerialName("ukulele") UKULELE("ukulele"),
    @SerialName("cavaquinho") CAVAQUINHO("cavaquinho"),
    @SerialName("teclado") KEYBOARD("keyboard"),
}

/** How the chord tapped on stage sounds: picked, strummed or both. */
enum class ChordPlayMode {
    @SerialName("arpeggio") ARPEGGIO,
    @SerialName("chord") CHORD,
    @SerialName("both") BOTH,
}

/** Where the list with the song's chord diagrams shows. */
enum class ChordListPlace {
    @SerialName("none") NONE,
    @SerialName("top") TOP,
    @SerialName("bottom") BOTTOM,
}

/** Import site (lyrics or chords). */
@Immutable
@Serializable
data class ImportSite(val name: String, val url: String)

/** The sites that come with the app; the user picks the default, removes and adds others. */
object ImportSites {
    val LYRICS = listOf(
        ImportSite("Letras", "https://www.letras.mus.br/"),
        ImportSite("Vagalume", "https://www.vagalume.com.br/"),
        ImportSite("Genius", "https://genius.com/"),
    )
    val CHORDS = listOf(
        ImportSite("Cifra Club", "https://www.cifraclub.com.br/"),
        ImportSite("Cifras", "https://www.cifras.com.br/"),
        ImportSite("Ultimate Guitar", "https://www.ultimate-guitar.com/"),
        ImportSite("LaCuerda", "https://acordes.lacuerda.net/"),
    )
}

/** How the lyrics move on stage: turn pages (default) or scroll (user's request, 29/10). */
enum class StageMode {
    @SerialName("pages") PAGES,
    @SerialName("scroll") SCROLL,
}

/** What the stage clock shows; a tap moves to the next (user, 07/11). */
enum class StageClockMode {
    @SerialName("clock") CLOCK,
    @SerialName("elapsed") ELAPSED,   // how long the show has run
    @SerialName("left") LEFT,         // how much of the show is left
}

enum class StageColumns {
    @SerialName("auto") AUTO,   // 2 columns on a tablet in landscape, 1 elsewhere
    @SerialName("one") ONE,
    @SerialName("two") TWO,
}

/**
 * Essential mode (T19): band, internet, player and stage extras off; lyrics, chords (with the diagram) and pedal stay.
 */
val Look.essential: Boolean get() = !bandOn && !internetOn && !playerMode && !extrasOn

/** Some part is off (band, internet, player, extras): the alert on open lists which. */
val Look.anyOff: Boolean get() = !bandOn || !internetOn || !playerMode || !extrasOn

/** The song with [zoom] on [screen], or back to automatic with null. The old single zoom goes: it isn't known where it came from. */
fun Song.withZoom(screen: String, zoom: ScreenZoom?): Song =
    copy(zoom = 1f, zooms = if (zoom == null) zooms - screen else zooms + (screen to zoom))

/** The old single zoom belongs to the first screen that opens the song: it's the person's choice, and the others go automatic. */
fun Song.claimOldZoom(screen: String): Song =
    if (zoom == 1f || screen in zooms) this else withZoom(screen, ScreenZoom(zoom))

/** Stage look: applies to every song. Colors in ARGB. */
@Immutable
@Serializable
data class Look(
    val fontSp: Float = 30f,
    val fg: Int = 0xFFFFFFFF.toInt(),
    val bg: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
    val sectionLang: SectionLang = SectionLang.PT,   // kept only to read old libraries: sections are always in Portuguese
    val sectionColor: Int = 0xFF4AF6CD.toInt(),
    val showSections: Boolean = true,
    val sectionScale: Float = DEFAULT_SECTION_SCALE,   // section title size relative to the lyrics (adjustable, 29/10)
    val keepScreenOn: Boolean = true,   // screen doesn't dim with the app open (show); the stage always stays lit
    val stageDnd: Boolean = true,   // Do not disturb while the stage is in front (user, 07/11)
    val clockMode: StageClockMode = StageClockMode.CLOCK,
    val showMinutes: Int = br.app.stagebook.domain.ShowTimer.DEFAULT_MINUTES,   // the show's length, in half hours
    val showStart: Long = 0L,   // when the show's counter started (epoch ms); 0 = not started
    val trackStops: Boolean = true,   // the stage's track button stops and goes back to the start; off = pauses (user, 31/10)
    val pedalName: String = "",   // name of the Bluetooth pedal recognized from the last button pressed
    val columns: StageColumns = StageColumns.AUTO,
    val stageMode: StageMode = StageMode.PAGES,
    val autoScroll: Int = DEFAULT_AUTO_SCROLL,   // auto-scroll speed (1 to 10), in scroll mode
    // stage metronome: 1-2-3-4 count-in before starting and sound (off = blink only)
    val metroShown: Boolean = true,   // metronome icon on the stage; off hides it (user, 31/10)
    val metroCount: Boolean = true,
    val metroSound: Boolean = true,
    val chordPlay: ChordPlayMode = ChordPlayMode.BOTH,   // playing the chord on stage (T20)
    val keyboardCapoHintSeen: Boolean = false,   // the "chords in the sounding key" note already showed for keyboard
    val announce: Boolean = true,   // Now Playing: announce the current song on the local network (OBS, Resolume)
    val metroGlow: Boolean = true,   // glow on the stage edge on the beat
    val metroGlowColor: Int = 0xFF4AF6CD.toInt(),   // glow color
    // where the metronome sounds (user, 02/11): -1 = the device (headphones, speaker); otherwise the USB interface's
    // output (the first one, and the next too when [metroUsbPair]), so the drummer hears it through the desk
    val metroUsbOut: Int = -1,
    val metroUsbPair: Boolean = true,
    // play on the USB interface through Android's audio instead of talking to USB directly (user, 02/11, B-33): for
    // devices where the direct way gets no packets back, like the S26, while Android plays there. Off by default
    val usbViaAndroid: Boolean = false,
    val pedalKeys: Map<Int, PedalAction> = emptyMap(),   // buttons set to another command in settings (key → command)
    val pedalSeen: List<Int> = emptyList(),   // the pedal's buttons pressed in settings: listed with what each does (user, 01/11)
    val midiBtAddress: String = "",   // Bluetooth MIDI pedal chosen in Settings (the app reconnects by itself)
    val midiBtName: String = "",
    // MIDI pedal over USB: when off, the app opens nothing on USB (it stays free for the USB audio interface). Off by
    // default (user, 02/11): it's turned on by whoever has a USB pedal; saved libraries keep their value
    val midiUsb: Boolean = false,
    // chords (step 3): "lyrics and chords" profile (false = lyrics only), chord size relative to the lyrics and color
    val chords: Boolean = true,
    val chordScale: Float = 0.8f,
    val chordColor: Int = 0xFFFFC107.toInt(),
    // highlighted phrases (==…==): a see-through marker, chosen in Stage look (user, 01/11)
    val highlightColor: Int = 0x66FFEB3B,
    // recognized sections in the app's language (the saved lyrics don't change); false = as written
    val translateSections: Boolean = true,
    val font: StageFont = StageFont.DEFAULT,        // lyrics font
    val chordFont: StageFont = StageFont.DEFAULT,   // chord font
    val sectionFont: StageFont = StageFont.DEFAULT, // section titles font (user, 01/11)
    val instrument: Instrument = Instrument.ACOUSTIC_GUITAR,   // chord diagrams (popup and list)
    val chordList: ChordListPlace = ChordListPlace.NONE,
    val chordTap: Boolean = true,   // tapping a chord on stage shows the diagram (off: the tap turns the page)
    // player mode (tracks, USB output, mixer): when off, nothing from the player loads, not the engine, the USB or the
    // service. A new install starts with it on (user's request, 29/10); saved libraries keep their value
    // (encodeDefaults)
    val playerMode: Boolean = true,
    // essential mode (T19): what stays on. When off, it leaves the screen and stops running. These three and the player
    // off = essential mode
    val bandOn: Boolean = true,       // band on the network: search, sending to the band, Lead the band
    val internetOn: Boolean = true,   // 3 words, band inbox, resends, new version, BPM and song details
    val extrasOn: Boolean = true,     // stage: chord sound, Details (harmonic field), metronome, Now Playing
    val essentialAlertOff: Boolean = false,
    val webEditorOn: Boolean = false,   // editor on the computer (T37): off by default, essential mode turns it off   // "Don't show again" on the alert on open; touching any part clears the mark
    // player mixer (Settings → Player): app-wide, applies to every backing track; per song only the measurement
    val mixer: br.app.stagebook.domain.MixerConfig = br.app.stagebook.domain.MixerConfig(),
    // import: the sites for each kind; the first in each list is the default (where the importer opens)
    val lyricSites: List<ImportSite> = ImportSites.LYRICS,
    val chordSites: List<ImportSite> = ImportSites.CHORDS,
    // Setlists tab in A to Z instead of newest first (user, 03/11)
    val setsAbc: Boolean = false,
) {
    companion object {
        const val MIN_FONT = 16f
        const val MAX_FONT = 72f
        const val MAX_STAGE_FONT = 120f   // with zoom on stage it can go over the settings maximum
        const val MIN_CHORD_SCALE = 0.5f
        const val DEFAULT_SECTION_SCALE = 0.9f
        const val DEFAULT_AUTO_SCROLL = 3
        const val MAX_AUTO_SCROLL = 10
        const val AUTO_SCROLL_DP_PER_LEVEL = 8f   // level 3 = 24 dp per second
        const val MIN_SECTION_SCALE = 0.6f
        const val MAX_SECTION_SCALE = 1.3f
        const val MAX_CHORD_SCALE = 1.2f
    }
}

@Immutable
@Serializable
data class Library(
    val version: Int = 1,
    val songs: List<Song> = emptyList(),
    val sets: List<Setlist> = emptyList(),
    val look: Look = Look(),
) {
    // by id in one step (a setlist screen looked up each of its songs in the whole list on every library change); not
    // serialized, built the first time it's needed for this library
    private val byId: Map<String, Song> by lazy { songs.associateBy { it.id } }

    fun song(id: String): Song? = byId[id]
    fun setlist(id: String): Setlist? = sets.firstOrNull { it.id == id }
}
