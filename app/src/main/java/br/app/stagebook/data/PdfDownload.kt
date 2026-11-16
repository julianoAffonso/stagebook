package br.app.stagebook.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A PDF the person tapped in the importer's browser: fetched here (https only, no more than [PdfStore.MAX_BYTES], the page's
 * cookies and agent, never an invented one) and handed to [PdfImporter], which checks it like any PDF. Nothing is fetched
 * unless the person tapped a link that the browser says is a PDF.
 */
object PdfDownload {
    sealed interface Fetched {
        class Bytes(val name: String, val bytes: ByteArray) : Fetched
        data object TooBig : Fetched
        data object Failed : Fetched
    }

    /** Whether the browser's download is a PDF worth taking: by its type, or by the name when the type is vague. */
    fun isPdf(url: String, mime: String?, disposition: String?): Boolean {
        if (mime?.lowercase()?.startsWith("application/pdf") == true) return true
        val name = nameOf(url, disposition)
        val vague = mime.isNullOrBlank() || mime.startsWith("application/octet-stream") || mime.startsWith("binary/")
        return vague && name.endsWith(".pdf", ignoreCase = true)
    }

    /** The file's name from the server's header, else from the address (the system's own guess changes the extension to the type's). */
    fun nameOf(url: String, disposition: String?): String {
        val header = disposition?.let { Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val fromUrl = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        return (header ?: fromUrl).let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }.trim().ifEmpty { "PDF" }
    }

    suspend fun fetch(url: String, userAgent: String?, cookies: String?, disposition: String?, mime: String?): Fetched = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) return@withContext Fetched.Failed
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                userAgent?.let { c.setRequestProperty("User-Agent", it) }
                cookies?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Cookie", it) }
                if (c.responseCode != 200) return@withContext Fetched.Failed
                if (c.contentLengthLong > PdfStore.MAX_BYTES) return@withContext Fetched.TooBig
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                c.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > PdfStore.MAX_BYTES) return@withContext Fetched.TooBig
                    }
                }
                Fetched.Bytes(nameOf(url, disposition), out.toByteArray())
            } finally { c.disconnect() }
        } catch (_: java.io.IOException) { Fetched.Failed }
    }
}
