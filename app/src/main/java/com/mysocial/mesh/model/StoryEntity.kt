package com.mysocial.mesh.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Materialized Projection Layer: Ephemeral Disappearing 24-Hour Mesh Stories
 */
@Entity(
    tableName = "stories_view",
    indices = [
        Index(value = ["authorId"]),
        Index(value = ["expiresAt"]),
        Index(value = ["createdAt"])
    ]
)
data class StoryEntity(
    @PrimaryKey
    val storyId: String,
    val authorId: String,
    val authorDisplayName: String,
    val text: String,
    val mediaBlobHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
    val isViewed: Boolean = false
)

@Serializable
data class StoryPayload(
    val storyId: String,
    val authorId: String,
    val authorDisplayName: String,
    val text: String,
    val mediaBlobHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): StoryPayload =
            CanonicalJson.decodeFromString(json)
    }
}

