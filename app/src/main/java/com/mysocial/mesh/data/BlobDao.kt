package com.mysocial.mesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BlobDao {

    // -------------------------------------------------------------
    // MANIFEST OPERATIONS
    // -------------------------------------------------------------

    @Upsert
    suspend fun upsertManifest(manifest: BlobManifestEntity)

    @Query("SELECT * FROM blob_manifests WHERE blobHash = :blobHash LIMIT 1")
    suspend fun getManifest(blobHash: String): BlobManifestEntity?

    @Query("SELECT * FROM blob_manifests WHERE blobHash = :blobHash LIMIT 1")
    fun observeManifest(blobHash: String): Flow<BlobManifestEntity?>

    @Query("SELECT * FROM blob_manifests WHERE isComplete = 1")
    suspend fun getCompletedManifests(): List<BlobManifestEntity>

    @Query("SELECT blobHash FROM blob_manifests WHERE isComplete = 1")
    suspend fun getCompletedBlobHashes(): List<String>

    @Query("SELECT * FROM blob_manifests WHERE isComplete = 0")
    suspend fun getIncompleteManifests(): List<BlobManifestEntity>

    @Query("UPDATE blob_manifests SET isComplete = 1, localFilePath = :localPath WHERE blobHash = :blobHash")
    suspend fun markComplete(blobHash: String, localPath: String)

    @Query("UPDATE blob_manifests SET isComplete = 0, localFilePath = NULL WHERE blobHash = :blobHash")
    suspend fun markIncomplete(blobHash: String)

    @Query("DELETE FROM blob_manifests WHERE blobHash = :blobHash")
    suspend fun deleteManifest(blobHash: String)

    // -------------------------------------------------------------
    // CHUNK OPERATIONS
    // -------------------------------------------------------------

    @Upsert
    suspend fun upsertChunk(chunk: BlobChunkEntity)

    @Query("SELECT chunkIndex FROM blob_chunks WHERE blobHash = :blobHash ORDER BY chunkIndex ASC")
    suspend fun getReceivedChunkIndices(blobHash: String): List<Int>

    @Query("SELECT COUNT(*) FROM blob_chunks WHERE blobHash = :blobHash")
    suspend fun getReceivedChunksCount(blobHash: String): Int

    @Query("SELECT COUNT(*) FROM blob_chunks WHERE blobHash = :blobHash")
    fun observeReceivedChunksCount(blobHash: String): Flow<Int>

    @Query("SELECT * FROM blob_chunks WHERE blobHash = :blobHash AND chunkIndex = :chunkIndex LIMIT 1")
    suspend fun getChunk(blobHash: String, chunkIndex: Int): BlobChunkEntity?

    @Query("SELECT * FROM blob_chunks WHERE blobHash = :blobHash ORDER BY chunkIndex ASC")
    suspend fun getAllChunksForBlob(blobHash: String): List<BlobChunkEntity>

    @Query("DELETE FROM blob_chunks WHERE blobHash = :blobHash")
    suspend fun deleteChunksForBlob(blobHash: String)
}
