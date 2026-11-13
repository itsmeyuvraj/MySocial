package com.mysocial.mesh.story

import android.util.Log
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.StoryEntity
import com.mysocial.mesh.model.StoryPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages ephemeral disappearing 24-hour mesh stories.
 * Stories auto-expire after 24 hours and are pruned from the local database.
 */
class StoryManager(
    private val db: MeshDatabase,
    private val cryptoManager: CryptoManager,
    private val syncEngine: GossipSyncEngine,
    private val onContentPublished: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "StoryManager"
        const val STORY_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun observeActiveStories(): Flow<List<StoryEntity>> =
        db.storyDao().observeActiveStories()

    fun observeStoriesForAuthor(authorId: String): Flow<List<StoryEntity>> =
        db.storyDao().observeStoriesForAuthor(authorId)

    suspend fun publishStory(
        text: String,
        authorDisplayName: String = "Me",
        mediaBlobHash: String? = null,
        mediaTotalBytes: Long? = null,
        mediaChunkCount: Int? = null,
        mediaMimeType: String? = null
    ): Result<StoryEntity> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && mediaBlobHash == null) {
            return@withContext Result.failure(IllegalArgumentException("Story cannot be empty"))
        }

        val profile = db.profileDao().getProfile(cryptoManager.authorId)
        val resolvedAuthorDisplayName = if (authorDisplayName.isNotBlank() && authorDisplayName != "Me") {
            authorDisplayName
        } else {
            profile?.displayName?.takeIf { it.isNotBlank() } ?: "Me"
        }

        val storyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + STORY_TTL_MS

        val payload = StoryPayload(
            storyId = storyId,
            authorId = cryptoManager.authorId,
            authorDisplayName = resolvedAuthorDisplayName,
            text = trimmed,
            mediaBlobHash = mediaBlobHash,
            mediaTotalBytes = mediaTotalBytes,
            mediaChunkCount = mediaChunkCount,
            mediaMimeType = mediaMimeType,
            createdAt = now,
            expiresAt = expiresAt
        )

        val entity = StoryEntity(
            storyId = storyId,
            authorId = cryptoManager.authorId,
            authorDisplayName = resolvedAuthorDisplayName,
            text = trimmed,
            mediaBlobHash = mediaBlobHash,
            createdAt = now,
            expiresAt = expiresAt,
            isViewed = true // Own stories are viewed by default
        )
        db.storyDao().insert(entity)

        val publishResult = syncEngine.createAndPublishLocalEntry(
            ContentType.STORY,
            payload.toJson()
        )

        if (publishResult.isSuccess) {
            Log.i(TAG, "Published 24-hour story: $storyId")
            onContentPublished?.invoke()
            Result.success(entity)
        } else {
            Result.failure(publishResult.exceptionOrNull() ?: IllegalStateException("Failed to publish story to mesh"))
        }
    }

    suspend fun markStoryViewed(storyId: String) = withContext(Dispatchers.IO) {
        db.storyDao().markViewed(storyId)
    }

    suspend fun pruneExpiredStories(): Int = withContext(Dispatchers.IO) {
        val pruned = db.storyDao().deleteExpiredStories()
        if (pruned > 0) {
            Log.d(TAG, "Pruned $pruned expired stories")
        }
        pruned
    }
}

