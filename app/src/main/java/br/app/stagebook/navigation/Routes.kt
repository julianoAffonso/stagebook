package br.app.stagebook.navigation

import kotlinx.serialization.Serializable

// Routes carry only ids; each screen fetches the rest from the repository.

@Serializable
object Home

@Serializable
data class SongEditor(
    val songId: String? = null,
    val fromImport: Boolean = false,
    val cursor: Int = -1,   // opened from the stage: position in the lyrics of the line the user held
    val fromMenu: Boolean = false,   // opened from the Songs tab: saving or leaving brings the side menu again
)

@Serializable
// the setlist's edit (the stage opens by tapping the setlist); share: opens with the Share sheet, from the stage's menu
data class SetlistDetail(val setId: String, val share: Boolean = false)

@Serializable
data class PickSongs(val setId: String)

/** Scan a setlist QR, or fetch by the internet [code], and check before creating. */
@Serializable
// inbox: what the band sent; fromLink: the code came in a link (whoever tapped it never saw the 3 words)
data class ScanSetlist(val code: String = "", val inbox: String = "", val fromLink: Boolean = false)

@Serializable
data class Stage(
    val setId: String? = null,
    val songId: String? = null,
    val start: Int = 0,   // setlist: opens the stage on this song (the one the user tapped in the list)
    // following the band without the leader's song: a stage with the leader's break alone, gone when the break is
    val leaderBreak: Boolean = false,
    // opened from the setlist's open break row: the stage comes back on that break (a tap on the song before it never does)
    val atBreak: Boolean = false,
    // opened from the Songs list: the editor opened from it steps through that list (with its search and filters)
    val fromList: Boolean = false,
    // the stage the app opened by itself at launch (the last one): nobody chose this song now, so it only counts as played
    // once something is done on it
    val resumed: Boolean = false,
)

/** The list of setlists, the start of the library drawer's own navigation (it also holds [SetlistDetail] and [PickSongs]). */
@Serializable
object SetlistsList

/** Settings, a page of its own: [section] opens on that section's name (empty: the list); [alone]: only that section, as About from the logo; [fromMenu]: opened from the side menu, which opens again on leaving; [item]: the setting a search result in the menu points at, which flashes on arrival. */
@Serializable
data class Settings(val section: String = "", val alone: Boolean = false, val fromMenu: Boolean = false, val item: String = "")

@Serializable
data class Importer(val query: String = "", val queued: Boolean = false, val kind: String = "", val fromMenu: Boolean = false)   // queued: batch importing into a setlist
