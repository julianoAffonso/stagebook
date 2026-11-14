package br.app.stagebook.ui.editor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * A light, permanent trace of how the editor opens from the stage (the cursor that landed on another line now and then,
 * 14/11): only numbers, never the lyrics, and only in a debuggable build. `adb logcat -s EditorOpen` on the next occurrence.
 */
object EditorOpenLog {
    const val TAG = "EditorOpen"
    /** Selection changes are only worth a line in the first moments after opening. */
    const val WINDOW_MS = 1000L

    private fun on(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** The stage: the held line (on screen), the song line it comes from and the cursor sent to the editor. */
    fun stage(context: Context, line: Int?, source: Int?, cursor: Int) {
        if (on(context)) Log.d(TAG, "stage line=$line source=$source cursor=$cursor")
    }

    /** The editor: the route's cursor and the field's first selection. */
    fun opened(context: Context, routeCursor: Int, fieldCursor: Int, textLength: Int) {
        if (on(context)) Log.d(TAG, "opened route=$routeCursor field=$fieldCursor length=$textLength")
    }

    /** A selection that changed [sinceOpenMs] after opening: [reason] is "touch" or "ime", [kept] says if the route's cursor stayed. */
    fun selection(context: Context, sinceOpenMs: Long, reason: String, start: Int, end: Int, kept: Boolean) {
        if (sinceOpenMs <= WINDOW_MS && on(context)) Log.d(TAG, "selection +${sinceOpenMs}ms $reason $start-$end kept=$kept")
    }
}
