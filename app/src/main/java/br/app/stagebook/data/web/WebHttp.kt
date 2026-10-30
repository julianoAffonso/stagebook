package br.app.stagebook.data.web

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * The little HTTP/1.1 the editor on the computer needs, without a server library (like the band on the network): one
 * request per connection, body only by Content-Length, with limits on the head and the body.
 */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),   // names in lower case
    val body: ByteArray = ByteArray(0),
    val remote: String = "",
) {
    /** A cookie sent by the browser, or null. */
    fun cookie(name: String): String? = headers["cookie"].orEmpty().split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')

    fun text(): String = body.toString(Charsets.UTF_8)

    override fun equals(other: Any?) = other is HttpRequest && method == other.method && path == other.path && query == other.query &&
        headers == other.headers && body.contentEquals(other.body) && remote == other.remote
    override fun hashCode() = 31 * (31 * method.hashCode() + path.hashCode()) + body.contentHashCode()
}

class HttpResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val contentType: String = "text/plain; charset=utf-8",
    val headers: Map<String, String> = emptyMap(),
) {
    fun text(): String = body.toString(Charsets.UTF_8)

    companion object {
        fun json(status: Int, json: String, headers: Map<String, String> = emptyMap()) =
            HttpResponse(status, json.toByteArray(), "application/json; charset=utf-8", headers)
        fun html(html: String) = HttpResponse(200, html.toByteArray(), "text/html; charset=utf-8")
        fun status(status: Int) = HttpResponse(status, reason(status).toByteArray())
    }
}

/** The request didn't follow the rules (too big, broken): the connection closes with this status. */
class HttpError(val status: Int) : Exception("HTTP $status")

object WebHttp {
    const val MAX_HEAD = 16 * 1024

    /** Reads one request. [maxBody] decides, by path, how big the body can be (backing tracks are bigger). */
    fun read(input: InputStream, remote: String = "", maxBody: (String) -> Int): HttpRequest {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input)
        val head = readHead(stream).split("\r\n")
        val first = head.first().split(' ')
        if (first.size < 3 || !first[2].startsWith("HTTP/1.")) throw HttpError(400)
        val target = first[1]
        val headers = head.drop(1).filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val path = target.substringBefore('?')
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        if (length < 0) throw HttpError(400)
        if (length > maxBody(path)) throw HttpError(413)
        val body = ByteArray(length.toInt())
        var read = 0
        while (read < body.size) {
            val n = stream.read(body, read, body.size - read)
            if (n < 0) throw HttpError(400)
            read += n
        }
        return HttpRequest(first[0].uppercase(), decode(path), query(target.substringAfter('?', "")), headers, body, remote)
    }

    fun write(output: OutputStream, response: HttpResponse) {
        val head = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
            append("Content-Type: ").append(response.contentType).append("\r\n")
            append("Content-Length: ").append(response.body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n")
            response.headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
            append("\r\n")
        }
        output.write(head.toByteArray())
        output.write(response.body)
        output.flush()
    }

    private fun readHead(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = stream.read()
            if (b < 0) throw HttpError(400)
            out.write(b)
            if (out.size() > MAX_HEAD) throw HttpError(431)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        return out.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
    }

    private fun query(q: String): Map<String, String> = q.split('&').filter { it.isNotEmpty() }
        .associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }

    private fun decode(s: String): String = runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
}

private fun reason(status: Int) = when (status) {
    200 -> "OK"
    204 -> "No Content"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    409 -> "Conflict"
    413 -> "Payload Too Large"
    429 -> "Too Many Requests"
    431 -> "Request Header Fields Too Large"
    else -> "Error"
}
