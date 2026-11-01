package br.app.stagebook.data.web

import kotlinx.coroutines.sync.withLock
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
    // one hidden page at a time: each WebView costs tens of MB on the Fire, and searches/imports can come together
    private val oneAtATime = kotlinx.coroutines.sync.Mutex()
    private val main = Handler(Looper.getMainLooper())

    suspend fun read(url: String, script: String = ExtractScript.SOURCE): String? = oneAtATime.withLock {
        withContext(Dispatchers.Main) {
            var view: WebView? = null
            try {
                withTimeoutOrNull(TIMEOUT_MS) { load(url, script) { view = it } }
            } finally {
                main.removeCallbacksAndMessages(null)   // a pending read never runs on a destroyed page
                view?.apply { stopLoading(); destroy() }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")   // the extraction is a script on the page, as in the import browser
    private suspend fun load(url: String, script: String, created: (WebView) -> Unit): String? = suspendCancellableCoroutine { cont ->
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
        // a screen's size for the hidden page: without a layout some pages don't build their results (B-25)
        web.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(HIDDEN_WIDTH, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(HIDDEN_HEIGHT, android.view.View.MeasureSpec.EXACTLY),
        )
        web.layout(0, 0, HIDDEN_WIDTH, HIDDEN_HEIGHT)
        created(web)
        web.loadUrl(url)
    }

    companion object {
        /**
         * Search results on the page (Google or a site's own search) that point to one of [hosts]: JSON
         * `[{"title":…,"url":…}]`, Google's /url redirects unwrapped, no repeats.
         */
        // the hosts go in as a JS array (31/10: an escaped "$" left the Kotlin call as text, a syntax error in the page,
        // so the computer's search never found anything)
        fun resultsScript(hosts: List<String>): String = """
(function(hosts){
  var seen = {}, out = [];
  Array.prototype.forEach.call(document.querySelectorAll('a[href]'), function(a){
    try {
      var u = new URL(a.href);
      if (u.host.indexOf('google') >= 0 && u.pathname === '/url') u = new URL(u.searchParams.get('q') || u.searchParams.get('url'));
      var ok = hosts.some(function(h){ return u.host === h || u.host.endsWith('.' + h); });
      // the hidden page isn't on screen: innerText can come back empty there, so the result's h3 or its plain text (B-25)
      var h3 = a.querySelector('h3');
      var title = ((h3 && h3.textContent) || a.innerText || a.textContent || '').trim().split('\n')[0];
      if (!ok || !title || seen[u.href]) return;
      seen[u.href] = true;
      out.push({ title: title.slice(0, 120), url: u.href });
    } catch (e) {}
  });
  return JSON.stringify(out.slice(0, 15));
})(${hosts.joinToString(",", "[", "]") { "\"" + it.replace("\"", "") + "\"" }})
""".trimIndent()

        private const val TIMEOUT_MS = 25_000L
        private const val HIDDEN_WIDTH = 1080
        private const val HIDDEN_HEIGHT = 2000
        const val SETTLE_MS = 1_500L
    }
}
