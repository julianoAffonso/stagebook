package br.app.stagebook.data

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.core.content.edit
import br.app.stagebook.domain.ShareCode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receive links (`https://stagebook.app.br/r#47-violao-lua`, user 07/11): tapped in a message, or copied by the site
 * when the app wasn't installed yet. Remembers the codes already opened, and this device's own sends, so a link left in
 * the clipboard opens the receive screen only once.
 */
@Singleton
class ReceiveLinks @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("receive_links", Context.MODE_PRIVATE)

    private fun seen(): List<String> = prefs.getString(KEY_SEEN, null).orEmpty().split('\n').filter(String::isNotEmpty)

    /** A code that shouldn't open by itself again: opened already, or sent from here. */
    fun markSeen(code: String) = prefs.edit { putString(KEY_SEEN, (seen() - code + code).takeLast(MAX_SEEN).joinToString("\n")) }

    /** The code of a link that opened the app (Android's "open with"), marked as seen. */
    fun fromIntent(data: String?): String? = ShareCode.fromLink(data)?.also(::markSeen)

    /**
     * A receive link in the clipboard not opened before, or null. Only with the window focused: since Android 10 the
     * clipboard is empty otherwise. The text itself is read only when the clip changed since the last look and may be
     * a link, because Android 12+ tells the person every time an app reads it.
     */
    fun fromClipboard(): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val desc = runCatching { clipboard.primaryClipDescription }.getOrNull() ?: return null
        if (!desc.hasMimeType("text/*")) return null
        val stamp = desc.timestamp
        if (stamp != 0L && stamp == prefs.getLong(KEY_STAMP, 0L)) return null
        prefs.edit { putLong(KEY_STAMP, stamp) }
        if (surelyNotLink(desc) || sensitive(desc)) return null
        val text = runCatching { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() }.getOrNull()
        return newCode(text, seen())?.also(::markSeen)
    }

    // a clip the source marks as sensitive (a password manager's) is left alone (the constant is API 33; the key is the same before)
    private fun sensitive(desc: ClipDescription): Boolean = runCatching { desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true }.getOrDefault(false)

    // Android 12+ already classified the clip and found no link in it: no need to read it (and show the notice)
    private fun surelyNotLink(desc: ClipDescription): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
        desc.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE && desc.getConfidenceScore(TextClassifier.TYPE_URL) <= 0f
    }.getOrDefault(false)

    companion object {
        private const val KEY_SEEN = "seen"
        private const val KEY_STAMP = "clip_stamp"
        private const val MAX_SEEN = 50   // a code lasts 12 h: the latest few are enough

        /** The code of the receive link in [text] if it wasn't opened before ([seen]), else null. */
        fun newCode(text: String?, seen: Collection<String>): String? = ShareCode.fromLink(text)?.takeIf { it !in seen }
    }
}
