package dev.naominet.empurple.utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Color
import java.awt.image.BufferedImage

object QrCodeRenderer {
    fun render(content: String, size: Int): BufferedImage {
        val border = 1
        val qrSize = size - border * 2
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
        return BufferedImage(size, size, BufferedImage.TYPE_INT_RGB).apply {
            val white = Color.WHITE.rgb
            val black = Color(35, 31, 45).rgb
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val qrX = x - border
                    val qrY = y - border
                    val isBlack = qrX in 0 until qrSize && qrY in 0 until qrSize && matrix[qrX, qrY]
                    setRGB(x, y, if (isBlack) black else white)
                }
            }
        }
    }
}
