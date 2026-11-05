package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import br.app.stagebook.domain.StageFit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic zoom already found on this device: a song is measured once per screen, and app updates keep it. Apart from
 * the song on purpose, since it belongs to this screen: it doesn't go to backups or the band.
 */
@Singleton
class AutoZoomSizes @Inject constructor(@ApplicationContext context: Context) : StageFit.Sizes {
    private val prefs = context.getSharedPreferences("auto_zoom", Context.MODE_PRIVATE)

    override fun get(key: String): StageFit.Found? {
        val (zoom, columns) = prefs.getString(key, null)?.split('|')?.takeIf { it.size == 2 } ?: return null
        return StageFit.Found(zoom.toFloatOrNull() ?: return null, columns.toIntOrNull() ?: return null)
    }

    override fun put(key: String, found: StageFit.Found) {
        // an edited song leaves its old size behind: starting over costs only one measuring per song
        prefs.edit {
            if (prefs.all.size >= LIMIT) clear()
            putString(key, "${found.zoom}|${found.columns}")
        }
    }

    private companion object {
        const val LIMIT = 2000
    }
}
