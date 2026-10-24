package com.juliano.stagebook.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Troca o conteúdo do arquivo sem risco de deixá-lo pela metade: [write] grava em ".tmp" (com buffer)
 * e faz fsync, [ready] confere o temporário (e pode recusar: devolve false e nada muda), o anterior
 * vira [backup] por rename (sem copiar o arquivo inteiro de novo) e o novo entra com rename atômico.
 * Lança IOException se o disco falhar; o temporário é apagado e o anterior volta.
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
            if (keep) backup!!.renameTo(this)   // não fica sem biblioteca: o anterior volta
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
