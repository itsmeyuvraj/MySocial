package com.mysocial.mesh.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.media.ExifInterface
import com.mysocial.mesh.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.ceil

/**
 * Result of image processing for chunked content-addressed mesh sharing.
 */
data class ProcessedMedia(
    val blobHash: String,
    val totalBytes: Long,
    val chunkCount: Int,
    val mimeType: String = "image/webp",
    val microPreviewBase64: String,
    val localFile: File
)

/**
 * Image Compression & Micro-Preview Generator.
 * Handles high-performance WebP compression, micro-preview generation,
 * SHA-256 content-addressing, and chunked blob storage.
 */
object MediaProcessor {

    const val CHUNK_SIZE = 16384 // 16 KB fixed chunk size
    const val MAX_IMAGE_EDGE = 1280
    const val THUMBNAIL_SIZE = 24

    /**
     * Processes an image from an Android content Uri:
     * 1. Generates 24x24 WebP thumbnail at 20% quality and Base64-encodes it (~200 bytes)
     * 2. Downscales image to max 1280px longest edge, compresses with WebP at 75% quality
     * 3. Computes SHA-256 hash (blobHash)
     * 4. Saves compressed bytes to files/blobs/<blobHash>.blob
     */
    suspend fun processImageUri(context: Context, uri: Uri): ProcessedMedia = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream for Uri: $uri")

        val rawBytes = inputStream.use { it.readBytes() }
        processImageBytes(context, rawBytes)
    }

    /**
     * Processes raw image bytes.
     */
    suspend fun processImageBytes(context: Context, rawBytes: ByteArray): ProcessedMedia = withContext(Dispatchers.IO) {
        // Decode bitmap with orientation correction
        val originalBitmap = decodeWithOrientation(rawBytes)
            ?: throw IllegalArgumentException("Failed to decode image bitmap")

        try {
            // a. Generate tiny 24x24 WebP thumbnail at 20% quality
            val thumbBitmap = Bitmap.createScaledBitmap(originalBitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
            val thumbBos = ByteArrayOutputStream()
            compressToWebp(thumbBitmap, quality = 20, outputStream = thumbBos)
            val thumbBytes = thumbBos.toByteArray()
            val microPreviewBase64 = Base64.encodeToString(thumbBytes, Base64.NO_WRAP)
            if (thumbBitmap != originalBitmap) {
                thumbBitmap.recycle()
            }

            // b. Downscale full image so longest edge is max 1280px
            val srcW = originalBitmap.width
            val srcH = originalBitmap.height
            val scaledBitmap = if (srcW > MAX_IMAGE_EDGE || srcH > MAX_IMAGE_EDGE) {
                val ratio = srcW.toFloat() / srcH.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio >= 1.0f) {
                    targetW = MAX_IMAGE_EDGE
                    targetH = (MAX_IMAGE_EDGE / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetH = MAX_IMAGE_EDGE
                    targetW = (MAX_IMAGE_EDGE * ratio).toInt().coerceAtLeast(1)
                }
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val fullBos = ByteArrayOutputStream()
            compressToWebp(scaledBitmap, quality = 75, outputStream = fullBos)
            val compressedBytes = fullBos.toByteArray()

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }

            // Compute SHA-256 hex
            val blobHash = CryptoManager.sha256Hex(compressedBytes)

            // c. Save to files/blobs/<blobHash>.blob
            val blobsDir = getBlobsDirectory(context)
            val blobFile = File(blobsDir, "$blobHash.blob")
            blobFile.writeBytes(compressedBytes)

            val totalBytes = compressedBytes.size.toLong()
            val chunkCount = if (totalBytes == 0L) 0 else ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

            ProcessedMedia(
                blobHash = blobHash,
                totalBytes = totalBytes,
                chunkCount = chunkCount,
                mimeType = "image/webp",
                microPreviewBase64 = microPreviewBase64,
                localFile = blobFile
            )
        } finally {
            originalBitmap.recycle()
        }
    }

    /**
     * Compresses bitmap to WebP using WEBP_LOSSY on API 30+ or WEBP on older APIs.
     */
    fun compressToWebp(bitmap: Bitmap, quality: Int, outputStream: ByteArrayOutputStream) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, outputStream)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
        }
    }

    /**
     * Gets the blobs storage directory under files/blobs.
     */
    fun getBlobsDirectory(context: Context): File {
        val dir = File(context.filesDir, "blobs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Gets the file for a specific blobHash if it exists locally.
     */
    fun getBlobFile(context: Context, blobHash: String): File {
        return File(getBlobsDirectory(context), "$blobHash.blob")
    }

    /**
     * Reads a specific 16 KB chunk from a blob file.
     */
    fun readChunkFromFile(file: File, chunkIndex: Int, chunkSize: Int = CHUNK_SIZE): ByteArray? {
        if (!file.exists() || !file.canRead()) return null
        val offset = chunkIndex.toLong() * chunkSize
        val totalLength = file.length()
        if (offset >= totalLength) return null

        val toRead = minOf(chunkSize.toLong(), totalLength - offset).toInt()
        val chunkBytes = ByteArray(toRead)

        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(chunkBytes)
        }
        return chunkBytes
    }

    /**
     * Decodes Base64 micro-preview into an in-memory Bitmap.
     */
    fun decodeMicroPreview(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a local file into a Bitmap.
     */
    fun decodeBlobFile(file: File?): Bitmap? {
        if (file == null || !file.exists() || !file.canRead()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes bitmap from byte array with EXIF orientation correction.
     */
    private fun decodeWithOrientation(bytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val exif = ExifInterface(bytes.inputStream())
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }
}
