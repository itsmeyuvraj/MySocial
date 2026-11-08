package com.mysocial.mesh.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Materialized Projection Layer: Decentralized Private Group Spaces
 * Encrypted using symmetric group sender keys (MLS / TreeKEM inspired root key).
 */
@Entity(
    tableName = "groups_view",
    indices = [
        Index(value = ["creatorAuthorId"]),
        Index(value = ["createdAt"])
    ]
)
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val groupName: String,
    val creatorAuthorId: String,
    val senderKeyBase64: String, // Shared 256-bit AES-GCM group root key
    val createdAt: Long = System.currentTimeMillis(),
    val memberCount: Int = 1,
    val avatarBlobHash: String? = null,
    val description: String = ""
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "authorId"],
    indices = [
        Index(value = ["authorId"])
    ]
)
data class GroupMemberEntity(
    val groupId: String,
    val authorId: String,
    val displayName: String = "Member",
    val role: String = "MEMBER", // "ADMIN" or "MEMBER"
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_messages",
    indices = [
        Index(value = ["groupId", "timestamp"]),
        Index(value = ["senderAuthorId"])
    ]
)
data class GroupMessageEntity(
    @PrimaryKey
    val messageId: String,
    val groupId: String,
    val senderAuthorId: String,
    val senderDisplayName: String,
    val ciphertextBase64: String,
    val plaintextSnippet: String?,
    val mediaBlobHash: String? = null,
    val mediaType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val status: Int = 0 // 0=Sending, 1=Sent to Mesh, 2=Relayed, 3=Confirmed
)

@Serializable
data class GroupMessagePayload(
    val messageId: String,
    val groupId: String,
    val senderAuthorId: String,
    val senderDisplayName: String,
    val ciphertextBase64: String, // AES-256-GCM encrypted payload
    val ivBase64: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaBlobHash: String? = null,
    val mediaType: String? = null
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): GroupMessagePayload =
            CanonicalJson.decodeFromString(json)
    }
}

@Serializable
data class GroupPlaintextContent(
    val text: String,
    val mediaBlobHash: String? = null,
    val mediaType: String? = null
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): GroupPlaintextContent =
            CanonicalJson.decodeFromString(json)
    }
}

