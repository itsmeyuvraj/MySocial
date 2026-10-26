package com.mysocial.mesh.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Manifest table tracking content-addressed media blobs and their transfer status.
 */
@Entity(
    tableName = "blob_manifests",
    indices = [
        Index(value = ["isComplete"])
    ]
)
data class BlobManifestEntity(
    @PrimaryKey
    val blobHash: String,
    val totalBytes: Long,
    val chunkCount: Int,
    val mimeType: String = "image/webp",
    val isComplete: Boolean,
    val localFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Partial chunk storage for resumable transfers across the mesh.
 * Tracks received 16 KB chunks for incomplete media posts.
 */
@Entity(
    tableName = "blob_chunks",
    primaryKeys = ["blobHash", "chunkIndex"],
    indices = [
        Index(value = ["blobHash"])
    ]
)
data class BlobChunkEntity(
    val blobHash: String,
    val chunkIndex: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlobChunkEntity

        if (blobHash != other.blobHash) return false
        if (chunkIndex != other.chunkIndex) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blobHash.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + data.contentHashCode()
        return result
    }
}
