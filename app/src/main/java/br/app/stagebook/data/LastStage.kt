package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What was on stage last: a setlist, or one song opened on its own. The app opens on it, where the show stopped. Kept
 * apart from the library on purpose (this device's, never in a backup, QR or band package).
 */
@Singleton
class LastStage @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("last_stage", Context.MODE_PRIVATE)

    /** [setId] set: a setlist's stage; otherwise [songId]: a song opened alone. */
    data class Place(val setId: String?, val songId: String?)

    fun record(setId: String?, songId: String?) {
        if (setId == null && songId == null) return
        prefs.edit { putString(SET, setId); putString(SONG, if (setId == null) songId else null) }
    }

    fun place(): Place? {
        val set = prefs.getString(SET, null)
        val song = prefs.getString(SONG, null)
        return if (set == null && song == null) null else Place(set, song)
    }

    private companion object {
        const val SET = "set"
        const val SONG = "song"
    }
}
