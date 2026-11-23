package br.app.stagebook.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads what comes from outside (a server's answer, an entry of a zip someone sent) up to [max] bytes: more than that is
 * not what the app expects, and it is refused in place of filling the memory (audit 22/11: these were read whole).
 */
internal fun InputStream.readCapped(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (true) {
        val n = read(buf)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > max) throw IOException("more than $max bytes")
        out.write(buf, 0, n)
    }
}

internal fun InputStream.readTextCapped(max: Int): String = readCapped(max).decodeToString()

/** A service's JSON answer: a few KB in practice. */
internal const val MAX_SERVICE_ANSWER = 2 * 1024 * 1024
