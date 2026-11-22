package br.app.stagebook.data

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File

/**
 * Makes a big PDF smaller on attach (user, 22/11), with PdfBox-Android (Apache 2.0): only its large pictures are
 * recompressed (a scanned score shrinks a lot), the text and the notes drawn as vectors stay as they are. Black and white
 * scans (1 bit, already small) and pictures with transparency (a JPEG would lose it) are left alone. Gives the smaller
 * copy only when it is at least [KEEP_RATIO] of the original or less; otherwise, or on any failure, null (the original
 * stays: this never costs a PDF).
 */
object PdfShrink {
    const val MIN_BYTES = 2L * 1024 * 1024
    const val KEEP_RATIO = 0.7
    const val MAX_SIDE = 2000
    const val JPEG_QUALITY = 0.7f

    /** [minBytes]: smaller files are left alone (0 on sending over the internet: every byte counts there); [out]: where the copy goes. */
    fun shrunk(context: Context, file: File, minBytes: Long = MIN_BYTES, out: File = File(file.parentFile, file.name + ".small")): File? {
        if (file.length() < minBytes) return null
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val changed = PDDocument.load(file).use { doc ->
                if (doc.isEncrypted) return null
                var any = false
                for (page in doc.pages) {
                    val res = page.resources ?: continue
                    for (name in res.xObjectNames.toList()) {
                        val image = res.getXObject(name) as? PDImageXObject ?: continue
                        if (image.bitsPerComponent <= 1 || image.softMask != null || image.isStencil) continue
                        val side = maxOf(image.width, image.height)
                        val bitmap = image.image ?: continue
                        val scale = minOf(1f, MAX_SIDE.toFloat() / side)
                        val fitted = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                        res.put(name, JPEGFactory.createFromImage(doc, fitted, JPEG_QUALITY))
                        if (fitted !== bitmap) fitted.recycle()
                        bitmap.recycle()
                        any = true
                    }
                }
                if (any) doc.save(out)
                any
            }
            if (changed && out.length() in 1..(file.length() * KEEP_RATIO).toLong()) out else { out.delete(); null }
        } catch (e: Exception) {
            out.delete(); null
        } catch (e: OutOfMemoryError) {
            out.delete(); null   // a huge picture on a small device: the PDF goes in as it was
        }
    }
}
