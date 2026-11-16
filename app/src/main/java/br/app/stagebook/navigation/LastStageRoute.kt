package br.app.stagebook.navigation

import br.app.stagebook.data.LastStage
import br.app.stagebook.data.model.Library
import br.app.stagebook.domain.StageStart

/**
 * The stage the app opens on: the one that was open last, if its setlist (with songs in it) or song is still in the
 * library; null when there is nothing to open on (first use, or what was open was deleted). [resume] says where a
 * setlist's stage opens (the song where the show stopped today).
 */
internal fun lastStageRoute(place: LastStage.Place?, library: Library, resume: (setId: String) -> StageStart): Stage? {
    place ?: return null
    place.setId?.let { id ->
        val set = library.setlist(id)?.takeIf { it.items.isNotEmpty() } ?: return null
        val at = resume(set.id)
        return Stage(setId = set.id, start = at.start, atBreak = at.atBreak)
    }
    val song = place.songId?.takeIf { id -> library.songs.any { it.id == id } } ?: return null
    return Stage(songId = song)
}
