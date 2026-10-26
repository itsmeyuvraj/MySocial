package com.mysocial.mesh.ui.qr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Real-time offline QR code analyzer using CameraX and ZXing.
 * Converts camera YUV frames to LuminanceSource and decodes QR codes without network access.
 */
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    private var hasFoundCode = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasFoundCode) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val yPlane = mediaImage.planes[0]
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            // Extract Y channel (luminance)
            val yBytes = ByteArray(buffer.remaining())
            buffer.get(yBytes)

            // Try decoding current frame
            var decodedText: String? = decodeFrame(yBytes, width, height, rowStride)

            // If not found and rotation is 90 or 270, try rotated buffer
            if (decodedText == null && (rotationDegrees == 90 || rotationDegrees == 270)) {
                val rotatedBytes = rotateYBytes(yBytes, width, height, rotationDegrees)
                decodedText = decodeFrame(rotatedBytes, height, width, height)
            }

            if (decodedText != null && !hasFoundCode) {
                hasFoundCode = true
                onQrCodeScanned(decodedText)
            }
        }

        imageProxy.close()
    }

    private fun decodeFrame(data: ByteArray, width: Int, height: Int, rowStride: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(
                data,
                rowStride,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(bitmap)
            result.text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun rotateYBytes(data: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
        val rotated = ByteArray(width * height)
        if (degrees == 90) {
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    val srcIndex = y * width + x
                    if (srcIndex < data.size) {
                        rotated[i] = data[srcIndex]
                    }
                    i++
                }
            }
        } else if (degrees == 270) {
            var i = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    val srcIndex = y * width + x
                    if (srcIndex < data.size) {
                        rotated[i] = data[srcIndex]
                    }
                    i++
                }
            }
        }
        return rotated
    }
}
