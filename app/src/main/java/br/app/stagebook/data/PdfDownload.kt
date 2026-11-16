package br.app.stagebook.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A PDF the person chose in the importer's browser: fetched here (https only, no more than [PdfStore.MAX_BYTES], the page's
 * cookies and agent, never an invented one) and handed to [PdfImporter], which checks it like any PDF. The screen asks the
 * person before anything is fetched: a page can start a download by itself.
 */
object PdfDownload {
    sealed interface Fetched {
        class Bytes(val name: String, val bytes: ByteArray) : Fetched
        data object TooBig : Fetched
        data object Failed : Fetched
    }

    /**
     * Whether the browser's download is a PDF worth taking: by its type, or by its name whatever the type says (score sites
     * serve PDFs as "force-download" and the like). The bytes are checked afterwards in any case.
     */
    fun isPdf(url: String, mime: String?, disposition: String?): Boolean {
        val type = mime?.lowercase().orEmpty()
        if (type.startsWith("application/pdf") || type.startsWith("application/x-pdf")) return true
        // a page is never a PDF, whatever its address ends in
        if (type.startsWith("text/")) return false
        return nameOf(url, disposition).endsWith(".pdf", ignoreCase = true)
    }

    /** The file's name from the server's header, else from the address (the system's own guess changes the extension to the type's). */
    fun nameOf(url: String, disposition: String?): String {
        val header = disposition?.let { Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val fromUrl = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        return (header ?: fromUrl).let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            // a name to show and to make a title of: no folders, nothing that isn't text, short (a longer one could never be shared)
            .substringAfterLast('/').substringAfterLast('\\').filter { !it.isISOControl() }.trim()
            .take(br.app.stagebook.domain.BandContent.MAX_PDF_NAME).ifEmpty { "PDF" }
    }

    /**
     * [cookiesFor] gives the browser's cookies of an address: asked again for each address a redirect leads to, so one site's
     * session never goes to another host (score sites send downloads to mirrors).
     */
    suspend fun fetch(
        url: String, userAgent: String?, disposition: String?, cookiesFor: (String) -> String?,
        connect: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
        deadlineMs: Long = DEADLINE_MS,
    ): Fetched = withContext(Dispatchers.IO) {
        val until = System.nanoTime() + deadlineMs * 1_000_000
        var at = url
        try {
            repeat(MAX_HOPS) {
                if (!at.startsWith("https://", ignoreCase = true)) return@withContext Fetched.Failed
                val c = connect(at)
                try {
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 15_000; c.readTimeout = 30_000
                    userAgent?.let { c.setRequestProperty("User-Agent", it) }
                    cookiesFor(at)?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Cookie", it) }
                    when (c.responseCode) {
                        200 -> {
                            if (c.contentLengthLong > PdfStore.MAX_BYTES) return@withContext Fetched.TooBig
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(16 * 1024)
                            c.inputStream.use { input ->
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    if (out.size() > PdfStore.MAX_BYTES) return@withContext Fetched.TooBig
                                    // a server that trickles the file must not hold the screen for ever
                                    if (System.nanoTime() > until) return@withContext Fetched.Failed
                                }
                            }
                            return@withContext Fetched.Bytes(nameOf(at, disposition.takeIf { at == url } ?: c.getHeaderField("Content-Disposition")), out.toByteArray())
                        }
                        301, 302, 303, 307, 308 -> at = URL(URL(at), c.getHeaderField("Location") ?: return@withContext Fetched.Failed).toString()
                        else -> return@withContext Fetched.Failed
                    }
                } finally { c.disconnect() }
            }
            Fetched.Failed   // too many redirects
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // also what isn't an I/O error: a cookie the connection refuses as a header, a bad address in a redirect
            Fetched.Failed
        }
    }

    private const val MAX_HOPS = 5
    private const val DEADLINE_MS = 120_000L
}
