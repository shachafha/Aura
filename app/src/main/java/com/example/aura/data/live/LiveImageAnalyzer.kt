package com.example.aura.data.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Base64
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * CameraX Analyzer that extracts frames for the Gemini Live API.
 * 
 * Compresses images and limits framerate to save bandwidth.
 */
class LiveImageAnalyzer(
    private val onFrameAnalyzed: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTime = 0L
    private val frameIntervalMs = 1000L // 1 frame per second

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastAnalyzedTime >= frameIntervalMs) {
            val image = imageProxy.image
            if (image != null && image.format == ImageFormat.YUV_420_888) {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    val base64 = bitmapToBase64(bitmap)
                    onFrameAnalyzed(base64)
                    lastAnalyzedTime = currentTime
                }
            }
        }
        
        imageProxy.close()
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 80, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Scale down to max 512x512 for Gemini
        val maxDim = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxDim > 512) {
            val scale = 512f / maxDim
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
