package br.app.stagebook.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Replaces the file's content without risking leaving it half written: [write] writes to ".tmp" (buffered) and fsyncs,
 * [ready] checks the temp file (and can refuse: returns false and nothing changes), the previous one becomes [backup]
 * by rename (without copying the whole file again) and the new one goes in with an atomic rename. Throws IOException if
 * the disk fails; the temp file is deleted and the previous one comes back.
 */
internal fun File.replaceAtomically(backup: File? = null, ready: (tmp: File) -> Boolean = { true }, write: (OutputStream) -> Unit): Boolean {
    val tmp = File(parentFile, "$name.tmp")
    try {
        FileOutputStream(tmp).use { out ->
            val buffered = BufferedOutputStream(out, 64 * 1024)
            write(buffered)
            buffered.flush()
            out.fd.sync()
        }
        if (!ready(tmp)) {
            tmp.delete()
            return false
        }
        val keep = backup != null && exists() && renameTo(backup)
        if (!tmp.renameTo(this)) {
            if (keep) backup!!.renameTo(this)   // never left without a library: the previous one comes back
            throw IOException("não consegui trocar $name")
        }
        return true
    } catch (e: IOException) {
        tmp.delete()
        throw e
    }
}

internal fun File.replaceAtomically(bytes: ByteArray, backup: File? = null, ready: (tmp: File) -> Boolean = { true }): Boolean =
    replaceAtomically(backup, ready) { it.write(bytes) }
