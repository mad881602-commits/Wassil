package com.example.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeManager {

    /**
     * Generates a high-contrast QR Code Bitmap for the pairing session payload.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            val brandDarkBlue = Color.rgb(10, 88, 202) // #0A58CA
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) brandDarkBlue else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * CameraX ImageAnalysis.Analyzer that decodes QR codes in real time from YUV_420_888 frames.
     */
    class QrImageAnalyzer(
        private val onQrDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.CHARACTER_SET to "UTF-8",
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }

        @Volatile
        private var isDelivered = false

        override fun analyze(image: ImageProxy) {
            if (isDelivered) {
                image.close()
                return
            }
            try {
                val plane = image.planes.firstOrNull()
                if (plane != null) {
                    val buffer = plane.buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    val width = image.width
                    val height = image.height
                    val source = PlanarYUVLuminanceSource(
                        data,
                        width,
                        height,
                        0,
                        0,
                        width,
                        height,
                        false
                    )
                    val bitmap = BinaryBitmap(HybridBinarizer(source))
                    val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
                    if (result != null && result.text.isNotBlank()) {
                        isDelivered = true
                        onQrDetected(result.text)
                    }
                }
            } catch (_: Exception) {
                // Frame did not contain a QR code; continue scanning next frame
            } finally {
                reader.reset()
                image.close()
            }
        }
    }
}
