package br.app.stagebook.data

import android.content.Intent

/**
 * A web page shared to the app from a browser's Share (user, 22/11: "Share" in Chrome → Stagebook): its address, which
 * opens the import browser on that page. Chrome shares the page's title and its address in one text; only the address
 * counts, and a text with no web address is not a page.
 */
object SharedPages {
    private val ADDRESS = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    fun pageUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return pageUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    fun pageUrl(text: String?): String? = text?.let { ADDRESS.find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }
}
