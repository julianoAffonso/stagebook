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
)

@Serializable
data class SetlistDetail(val setId: String)

@Serializable
data class PickSongs(val setId: String)

/** Scan a setlist QR, or fetch by the internet [code], and check before creating. */
@Serializable
data class ScanSetlist(val code: String = "", val inbox: String = "")   // inbox: what the band sent

@Serializable
data class Stage(
    val setId: String? = null,
    val songId: String? = null,
    val start: Int = 0,   // setlist: opens the stage on this song (the one the user tapped in the list)
)

@Serializable
data class Importer(val query: String = "", val queued: Boolean = false)   // queued: batch importing into a setlist
