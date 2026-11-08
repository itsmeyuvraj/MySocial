package com.mysocial.mesh.group

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.GroupEntity
import com.mysocial.mesh.model.GroupMemberEntity
import com.mysocial.mesh.model.GroupMessageEntity
import com.mysocial.mesh.model.GroupMessagePayload
import com.mysocial.mesh.model.GroupPlaintextContent
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages decentralized private multi-party mesh group spaces.
 * Messages within a group are encrypted with an AES-256-GCM symmetric sender key
 * shared among group members out-of-band or via QR/invite links.
 */
class GroupManager(
    private val db: MeshDatabase,
    private val cryptoManager: CryptoManager,
    private val syncEngine: GossipSyncEngine,
    private val onContentPublished: (() -> Unit)? = null
) {
    private val secureRandom = SecureRandom()

    fun observeGroups(): Flow<List<GroupEntity>> = db.groupDao().observeAllGroups()

    fun observeGroupMessages(groupId: String): Flow<List<GroupMessageEntity>> =
        db.groupDao().observeMessagesForGroup(groupId)

    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>> =
        db.groupDao().observeGroupMembers(groupId)

    suspend fun createGroup(
        name: String,
        description: String = "",
        myDisplayName: String = "Admin"
    ): GroupEntity = withContext(Dispatchers.IO) {
        val groupId = UUID.randomUUID().toString()
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

        val group = GroupEntity(
            groupId = groupId,
            groupName = name.trim(),
            creatorAuthorId = cryptoManager.authorId,
            senderKeyBase64 = keyBase64,
            createdAt = System.currentTimeMillis(),
            memberCount = 1,
            description = description.trim()
        )
        db.groupDao().upsertGroup(group)

        val member = GroupMemberEntity(
            groupId = groupId,
            authorId = cryptoManager.authorId,
            displayName = myDisplayName,
            role = "ADMIN",
            joinedAt = System.currentTimeMillis()
        )
        db.groupDao().upsertMember(member)

        group
    }

    fun generateInviteUri(group: GroupEntity): String {
        return "mysocial://group_invite?gid=${Uri.encode(group.groupId)}&name=${Uri.encode(group.groupName)}&key=${Uri.encode(group.senderKeyBase64)}"
    }

    suspend fun joinGroupFromInvite(
        inviteUri: String,
        myDisplayName: String = "Member"
    ): GroupEntity? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(inviteUri)
            val groupId = uri.getQueryParameter("gid") ?: return@withContext null
            val groupName = uri.getQueryParameter("name") ?: "Joined Group"
            val keyBase64 = uri.getQueryParameter("key") ?: return@withContext null

            val existing = db.groupDao().getGroup(groupId)
            if (existing != null) {
                return@withContext existing
            }

            val group = GroupEntity(
                groupId = groupId,
                groupName = groupName,
                creatorAuthorId = "REMOTE",
                senderKeyBase64 = keyBase64,
                createdAt = System.currentTimeMillis(),
                memberCount = 1,
                description = "Joined via invite"
            )
            db.groupDao().upsertGroup(group)

            val member = GroupMemberEntity(
                groupId = groupId,
                authorId = cryptoManager.authorId,
                displayName = myDisplayName,
                role = "MEMBER",
                joinedAt = System.currentTimeMillis()
            )
            db.groupDao().upsertMember(member)

            group
        } catch (e: Exception) {
            Log.e("GroupManager", "Failed to parse group invite URI", e)
            null
        }
    }

    suspend fun sendGroupMessage(
        groupId: String,
        text: String,
        myDisplayName: String = "Me",
        mediaBlobHash: String? = null,
        mediaType: String? = null
    ): Result<GroupMessageEntity> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && mediaBlobHash == null) {
            return@withContext Result.failure(IllegalArgumentException("Message content cannot be blank"))
        }

        val group = db.groupDao().getGroup(groupId)
            ?: return@withContext Result.failure(IllegalStateException("Group not found: $groupId"))

        try {
            val keyBytes = Base64.decode(group.senderKeyBase64, Base64.NO_WRAP)
            val ivBytes = ByteArray(12)
            secureRandom.nextBytes(ivBytes)

            val plainJson = GroupPlaintextContent(
                text = trimmed,
                mediaBlobHash = mediaBlobHash,
                mediaType = mediaType
            ).toJson()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, ivBytes))
            val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)

            val payload = GroupMessagePayload(
                messageId = messageId,
                groupId = groupId,
                senderAuthorId = cryptoManager.authorId,
                senderDisplayName = myDisplayName,
                ciphertextBase64 = ciphertextBase64,
                ivBase64 = ivBase64,
                timestamp = now,
                mediaBlobHash = mediaBlobHash,
                mediaType = mediaType
            )

            // Local outgoing entity
            val entity = GroupMessageEntity(
                messageId = messageId,
                groupId = groupId,
                senderAuthorId = cryptoManager.authorId,
                senderDisplayName = myDisplayName,
                ciphertextBase64 = ciphertextBase64,
                plaintextSnippet = trimmed,
                mediaBlobHash = mediaBlobHash,
                mediaType = mediaType,
                timestamp = now,
                isOutgoing = true,
                status = 0
            )
            db.groupDao().insertMessage(entity)

            // Publish to mesh log
            val result = syncEngine.createAndPublishLocalEntry(
                ContentType.GROUP_MESSAGE,
                payload.toJson()
            )

            if (result.isSuccess) {
                db.groupDao().updateMessageStatus(messageId, 1) // 1 = Sent
                onContentPublished?.invoke()
                Result.success(entity.copy(status = 1))
            } else {
                db.groupDao().updateMessageStatus(messageId, 2) // 2 = Failed
                Result.failure(result.exceptionOrNull() ?: IllegalStateException("Failed to publish group message"))
            }
        } catch (e: Exception) {
            Log.e("GroupManager", "Failed to encrypt/send group message", e)
            Result.failure(e)
        }
    }
}

