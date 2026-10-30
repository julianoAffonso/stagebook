package br.app.stagebook.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import br.app.stagebook.data.ExtractScript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Opens a chords page out of sight, as the app's import browser does, and runs the same [ExtractScript] on it: the
 * computer can't read the site itself (the site blocks a page from another address), the tablet can (T37f2).
 */
@Singleton
class WebPageReader @Inject constructor(@ApplicationContext private val context: Context) {
    /** The script's result for [url] (as evaluateJavascript returns it), or null if the page didn't load in time. */
    suspend fun read(url: String, script: String = ExtractScript.SOURCE): String? = withContext(Dispatchers.Main) {
        var view: WebView? = null
        try {
            withTimeoutOrNull(TIMEOUT_MS) { load(url, script) { view = it } }
        } finally {
            view?.apply { stopLoading(); destroy() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")   // the extraction is a script on the page, as in the import browser
    private suspend fun load(url: String, script: String, created: (WebView) -> Unit): String? = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        val web = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                private var done = false
                override fun onPageFinished(view: WebView, finished: String) {
                    if (done) return
                    done = true
                    // sites fill the lyrics in with scripts after loading: a moment before reading
                    main.postDelayed({ view.evaluateJavascript(script) { if (cont.isActive) cont.resume(it) } }, SETTLE_MS)
                }
            }
        }
        created(web)
        web.loadUrl(url)
    }

    companion object {
        /**
         * Search results on the page (Google or a site's own search) that point to one of [hosts]: JSON
         * `[{"title":…,"url":…}]`, Google's /url redirects unwrapped, no repeats.
         */
        fun resultsScript(hosts: List<String>): String = """
(function(hosts){
  var seen = {}, out = [];
  Array.prototype.forEach.call(document.querySelectorAll('a[href]'), function(a){
    try {
      var u = new URL(a.href);
      if (u.host.indexOf('google') >= 0 && u.pathname === '/url') u = new URL(u.searchParams.get('q') || u.searchParams.get('url'));
      var ok = hosts.some(function(h){ return u.host === h || u.host.endsWith('.' + h); });
      var title = (a.innerText || '').trim().split('\n')[0];
      if (!ok || !title || seen[u.href]) return;
      seen[u.href] = true;
      out.push({ title: title.slice(0, 120), url: u.href });
    } catch (e) {}
  });
  return JSON.stringify(out.slice(0, 15));
})(${'$'}{hosts.joinToString(",", "[", "]") { "\"" + it.replace("\"", "") + "\"" }})
""".trimIndent()

        private const val TIMEOUT_MS = 25_000L
        const val SETTLE_MS = 1_500L
    }
}
