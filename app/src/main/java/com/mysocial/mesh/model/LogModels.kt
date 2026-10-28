package com.mysocial.mesh.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Deterministic JSON instance for canonical wire and hashing representations.
 */
val CanonicalJson = Json {
    encodeDefaults = true
    prettyPrint = false
    isLenient = false
    ignoreUnknownKeys = true
}

/**
 * Enumeration of allowable log entry action types.
 */
enum class ContentType {
    POST,
    REPLY,
    REACTION,
    PROFILE,
    FOLLOW,
    POST_EDIT,
    POST_DELETE
}

// -----------------------------------------------------------------------------------------
// PAYLOAD SCHEMAS (contentJson)
// -----------------------------------------------------------------------------------------

@Serializable
data class MediaAttachment(
    val blobHash: String,
    val mimeType: String = "image/webp",
    val totalBytes: Long,
    val chunkCount: Int,
    val microPreviewBase64: String
)

@Serializable
data class PostPayload(
    val text: String,
    val tags: List<String> = emptyList(),
    val media: MediaAttachment? = null,
    val isPublic: Boolean = true,
    val expiresAt: Long? = null,
    val isSos: Boolean = false,
    val pollOptions: List<String>? = null
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): PostPayload = CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class PostEditPayload(
    val targetHash: String,
    val newText: String
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): PostEditPayload = CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class PostDeletePayload(
    val targetHash: String
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): PostDeletePayload = CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class ReplyPayload(
    val text: String,
    val rootHash: String,
    val parentHash: String
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): ReplyPayload = CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class ReactionPayload(
    val targetHash: String,
    val emoji: String = "❤️",
    val pollOptionIndex: Int? = null
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): ReactionPayload = CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class ProfilePayload(
    val displayName: String,
    val bio: String,
    val avatarSeed: String
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): ProfilePayload = CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class FollowPayload(
    val targetAuthorId: String,
    val following: Boolean
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): FollowPayload = CanonicalJson.decodeFromString(json)
    }
}

// -----------------------------------------------------------------------------------------
// IMMUTABLE RAW LOG ENTRY (Domain & Network Model)
// -----------------------------------------------------------------------------------------

@Serializable
data class LogEntry(
    val authorId: String,
    val sequence: Long,
    val previousHash: String?,
    val timestamp: Long,
    val contentType: ContentType,
    val contentJson: String,
    val signature: String,
    val entryHash: String
) {
    fun toEntity(
        hopDistance: Int = 0,
        hopCount: Int = 0,
        transportSource: String = "LOCAL"
    ): LogEntryEntity = LogEntryEntity(
        entryHash = entryHash,
        authorId = authorId,
        sequence = sequence,
        previousHash = previousHash,
        timestamp = timestamp,
        contentType = contentType,
        contentJson = contentJson,
        signature = signature,
        hopDistance = hopDistance,
        hopCount = hopCount,
        transportSource = transportSource
    )
}

// -----------------------------------------------------------------------------------------
// ROOM DATABASE ENTITIES (Ledger Layer & Materialized Projection Layer)
// -----------------------------------------------------------------------------------------

/**
 * Ledger Layer: Immutable Raw Log Store
 */
@Entity(
    tableName = "log_entries",
    indices = [
        Index(value = ["authorId", "sequence"], unique = true),
        Index(value = ["entryHash"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class LogEntryEntity(
    @PrimaryKey
    val entryHash: String,
    val authorId: String,
    val sequence: Long,
    val previousHash: String?,
    val timestamp: Long,
    val contentType: ContentType,
    val contentJson: String,
    val signature: String,
    val hopDistance: Int = 0, // 0 = self/local, 1 = 1-hop direct, >=2 = mule
    val hopCount: Int = 0,
    val transportSource: String = "LOCAL" // LOCAL, BLE, WIFI
) {
    fun toDomain(): LogEntry = LogEntry(
        authorId = authorId,
        sequence = sequence,
        previousHash = previousHash,
        timestamp = timestamp,
        contentType = contentType,
        contentJson = contentJson,
        signature = signature,
        entryHash = entryHash
    )
}

/**
 * Materialized Projection Layer: User Profiles
 */
@Entity(tableName = "profiles_view")
data class ProfileViewEntity(
    @PrimaryKey
    val authorId: String,
    val displayName: String,
    val bio: String,
    val avatarSeed: String,
    val updatedAtSeq: Long,
    val isVerifiedInPerson: Boolean = false
)

/**
 * Materialized Projection Layer: Posts & Replies
 */
@Entity(
    tableName = "posts_view",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["authorId"]),
        Index(value = ["replyToHash"]),
        Index(value = ["rootHash"]),
        Index(value = ["expiresAt"]),
        Index(value = ["isSos"])
    ]
)
data class PostViewEntity(
    @PrimaryKey
    val entryHash: String,
    val authorId: String,
    val text: String,
    val tagsJson: String = "[]",
    val timestamp: Long,
    val replyToHash: String?,
    val rootHash: String?,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val hopDistance: Int = 0,
    val mediaBlobHash: String? = null,
    val mediaMimeType: String? = null,
    val mediaTotalBytes: Long? = null,
    val mediaChunkCount: Int? = null,
    val microPreviewBase64: String? = null,
    val isPublic: Boolean = true,
    val hopCount: Int = 0,
    val transportSource: String = "LOCAL",
    val expiresAt: Long? = null,
    val isSos: Boolean = false,
    val pollOptionsJson: String? = null
)

/**
 * Materialized Projection Layer: Reactions (De-duplicating likes per author)
 */
@Entity(
    tableName = "reactions_view",
    primaryKeys = ["targetHash", "authorId", "emoji"],
    indices = [
        Index(value = ["targetHash"]),
        Index(value = ["authorId"])
    ]
)
data class ReactionEntity(
    val targetHash: String,
    val authorId: String,
    val emoji: String,
    val timestamp: Long,
    val pollOptionIndex: Int? = null
)

/**
 * Materialized Projection Layer: Follow Relationships
 */
@Entity(
    tableName = "follows_view",
    primaryKeys = ["followerId", "followeeId"],
    indices = [
        Index(value = ["followerId"]),
        Index(value = ["followeeId"])
    ]
)
data class FollowViewEntity(
    val followerId: String,
    val followeeId: String,
    val isFollowing: Boolean,
    val updatedAtSeq: Long,
    val isVerifiedInPerson: Boolean = false
)

/**
 * Materialized Projection Layer: Peer Sync State & Vector Clock Tracking
 */
@Entity(tableName = "peer_sync_state")
data class PeerSyncStateEntity(
    @PrimaryKey
    val authorId: String,
    val highestSequence: Long,
    val lastHash: String,
    val lastSyncedAt: Long
)

// -----------------------------------------------------------------------------------------
// QUERY PROJECTIONS
// -----------------------------------------------------------------------------------------

/**
 * Combined Post View with author profile information for Compose UI rendering.
 */
data class PostWithAuthor(
    @Embedded val post: PostViewEntity,
    @Relation(
        parentColumn = "authorId",
        entityColumn = "authorId"
    )
    val authorProfile: ProfileViewEntity?
) {
    val isAuthorVerifiedInPerson: Boolean
        get() = authorProfile?.isVerifiedInPerson == true
}

/**
 * Vector Clock tuple representation.
 */
data class AuthorSequenceTuple(
    val authorId: String,
    val highestSequence: Long
)
