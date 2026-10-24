package com.juliano.stagebook.domain

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Desenha o QR em preto e branco (o leitor precisa do contraste; não segue o tema do app). */
object QrEncoder {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Bytes no QR em modo binário: um caractere ISO-8859-1 por byte, sem ECI (o Backing lê os bytes crus). */
    fun encode(bytes: ByteArray, size: Int): Bitmap = encode(String(bytes, Charsets.ISO_8859_1), size, "ISO-8859-1")

    private fun encode(payload: String, size: Int, charset: String): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to charset,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        val m = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) BLACK else WHITE }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}
