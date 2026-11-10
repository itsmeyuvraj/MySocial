package com.mysocial.mesh.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.withTransaction
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.ProofOfWorkEngine
import com.mysocial.mesh.model.AuthorSequenceTuple
import com.mysocial.mesh.model.BlockedAuthorEntity
import com.mysocial.mesh.model.CanonicalJson
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.FollowViewEntity
import com.mysocial.mesh.model.GroupEntity
import com.mysocial.mesh.model.GroupMemberEntity
import com.mysocial.mesh.model.GroupMessageEntity
import com.mysocial.mesh.model.GroupMessagePayload
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.LogEntryEntity
import com.mysocial.mesh.model.PeerSyncStateEntity
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.PostEditPayload
import com.mysocial.mesh.model.PostDeletePayload
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.model.ProfilePayload
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.model.ReactionEntity
import com.mysocial.mesh.model.ReactionPayload
import com.mysocial.mesh.model.ReplyPayload
import com.mysocial.mesh.model.StoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

/**
 * Room TypeConverters for custom types and enums.
 */
class Converters {
    @TypeConverter
    fun fromContentType(type: ContentType): String = type.name

    @TypeConverter
    fun toContentType(name: String): ContentType = ContentType.valueOf(name)
}

// -----------------------------------------------------------------------------------------
// DATA ACCESS OBJECTS (DAOs)
// -----------------------------------------------------------------------------------------

@Dao
interface LogEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LogEntryEntity)

    @Query("SELECT * FROM log_entries WHERE entryHash = :hash LIMIT 1")
    suspend fun getEntryByHash(hash: String): LogEntryEntity?

    @Query("DELETE FROM log_entries WHERE entryHash = :hash")
    suspend fun deleteEntry(hash: String)

    @Query("SELECT * FROM log_entries WHERE authorId = :authorId ORDER BY sequence DESC LIMIT 1")
    suspend fun getLatestEntryForAuthor(authorId: String): LogEntryEntity?

    @Query("SELECT MAX(sequence) FROM log_entries WHERE authorId = :authorId")
    fun observeLatestSequenceForAuthor(authorId: String): Flow<Long?>

    @Query("SELECT * FROM log_entries WHERE authorId = :authorId AND sequence = :sequence LIMIT 1")
    suspend fun getEntryBySequence(authorId: String, sequence: Long): LogEntryEntity?

    @Query("SELECT * FROM log_entries WHERE authorId = :authorId AND sequence > :afterSequence ORDER BY sequence ASC LIMIT :limit")
    suspend fun getEntriesForAuthorAfter(authorId: String, afterSequence: Long, limit: Int = 100): List<LogEntryEntity>

    @Query("SELECT authorId, MAX(sequence) as highestSequence FROM log_entries GROUP BY authorId")
    suspend fun getAllKnownAuthorsWithMaxSeq(): List<AuthorSequenceTuple>

    @Query("SELECT COUNT(*) FROM log_entries")
    fun observeTotalEntriesCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT authorId) FROM log_entries")
    fun observeTotalAuthorsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun getTotalEntriesCount(): Int

    @Query("SELECT COUNT(DISTINCT authorId) FROM log_entries")
    suspend fun getTotalAuthorsCount(): Int

    @Query("SELECT * FROM log_entries WHERE contentType = 'PTT_VOICE_BURST' ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentPttEntries(): Flow<List<LogEntryEntity>>

    @Query("DELETE FROM log_entries WHERE contentType = 'PTT_VOICE_BURST'")
    suspend fun clearAllPttEntries()
}

@Dao
interface ProfileDao {
    @Upsert
    suspend fun upsert(profile: ProfileViewEntity)

    @Query("SELECT * FROM profiles_view WHERE authorId = :authorId LIMIT 1")
    suspend fun getProfile(authorId: String): ProfileViewEntity?

    @Query("SELECT * FROM profiles_view WHERE authorId = :authorId LIMIT 1")
    fun observeProfile(authorId: String): Flow<ProfileViewEntity?>

    @Query("SELECT * FROM profiles_view")
    suspend fun getAllProfiles(): List<ProfileViewEntity>

    @Query("SELECT * FROM profiles_view")
    fun observeAllProfiles(): Flow<List<ProfileViewEntity>>

    @Query("UPDATE profiles_view SET isVerifiedInPerson = :verified WHERE authorId = :authorId")
    suspend fun setVerifiedInPerson(authorId: String, verified: Boolean)
}

@Dao
interface PostViewDao {
    @Upsert
    suspend fun upsert(post: PostViewEntity)

    @Query("SELECT * FROM posts_view WHERE entryHash = :hash LIMIT 1")
    suspend fun getPost(hash: String): PostViewEntity?

    @Query("SELECT * FROM posts_view WHERE mediaBlobHash = :blobHash LIMIT 1")
    suspend fun getPostByBlobHash(blobHash: String): PostViewEntity?

    @Query("DELETE FROM posts_view WHERE entryHash = :hash OR replyToHash = :hash")
    suspend fun deletePost(hash: String)

    @Query("DELETE FROM posts_view WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun deleteExpiredPosts(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT mediaBlobHash FROM posts_view WHERE expiresAt IS NOT NULL AND expiresAt <= :now AND mediaBlobHash IS NOT NULL")
    suspend fun getExpiredBlobHashes(now: Long = System.currentTimeMillis()): List<String>

    @Query("UPDATE posts_view SET gatewayRelayedAt = :relayedAt WHERE entryHash = :hash")
    suspend fun markGatewayRelayed(hash: String, relayedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM posts_view WHERE isSos = 1 AND gatewayRelayedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getPendingGatewaySosPosts(): List<PostViewEntity>

    @Transaction
    @Query("""
        SELECT * FROM posts_view 
        WHERE replyToHash IS NULL 
        AND (expiresAt IS NULL OR expiresAt > :now)
        AND authorId NOT IN (SELECT authorId FROM blocked_authors)
        AND (tagsJson LIKE '%' || :channelTag || '%' OR text LIKE '%' || :channelTag || '%')
        ORDER BY isSos DESC, timestamp DESC
    """)
    fun observeChannelFeed(channelTag: String, now: Long = System.currentTimeMillis()): Flow<List<PostWithAuthor>>

    @Query("UPDATE posts_view SET text = :newText WHERE entryHash = :hash")
    suspend fun updatePostText(hash: String, newText: String)

    @Query("UPDATE posts_view SET likeCount = likeCount + 1 WHERE entryHash = :targetHash")
    suspend fun incrementLikeCount(targetHash: String)

    @Query("UPDATE posts_view SET replyCount = replyCount + 1 WHERE entryHash = :parentHash")
    suspend fun incrementReplyCount(parentHash: String)

    @Transaction
    @Query("""
        SELECT * FROM posts_view 
        WHERE replyToHash IS NULL 
        AND (expiresAt IS NULL OR expiresAt > :now)
        AND authorId NOT IN (SELECT authorId FROM blocked_authors)
        AND (
            isPublic = 1 
            OR authorId = :myAuthorId 
            OR :myAuthorId IN (SELECT followeeId FROM follows_view WHERE followerId = authorId AND isFollowing = 1)
            OR authorId IN (SELECT followeeId FROM follows_view WHERE followerId = :myAuthorId AND isFollowing = 1)
        )
        ORDER BY isSos DESC, timestamp DESC
    """)
    fun observeAllFeed(myAuthorId: String, now: Long = System.currentTimeMillis()): Flow<List<PostWithAuthor>>

    @Transaction
    @Query("""
        SELECT * FROM posts_view 
        WHERE replyToHash IS NULL 
        AND (expiresAt IS NULL OR expiresAt > :now)
        AND authorId != :myAuthorId
        AND authorId NOT IN (SELECT authorId FROM blocked_authors)
        AND (
            authorId IN (SELECT followeeId FROM follows_view WHERE followerId = :myAuthorId AND isFollowing = 1)
            OR :myAuthorId IN (SELECT followeeId FROM follows_view WHERE followerId = authorId AND isFollowing = 1)
            OR authorId IN (
                SELECT f2.followeeId FROM follows_view f1 
                JOIN follows_view f2 ON f1.followeeId = f2.followerId 
                WHERE f1.followerId = :myAuthorId AND f1.isFollowing = 1 AND f2.isFollowing = 1
            )
        )
        AND (
            isPublic = 1 
            OR :myAuthorId IN (SELECT followeeId FROM follows_view WHERE followerId = authorId AND isFollowing = 1)
            OR authorId IN (SELECT followeeId FROM follows_view WHERE followerId = :myAuthorId AND isFollowing = 1)
        )
        ORDER BY isSos DESC, timestamp DESC
    """)
    fun observeFollowedAndTwoHopFeed(myAuthorId: String, now: Long = System.currentTimeMillis()): Flow<List<PostWithAuthor>>

    @Transaction
    @Query("""
        SELECT * FROM posts_view 
        WHERE (replyToHash = :parentHash OR rootHash = :parentHash)
        AND authorId NOT IN (SELECT authorId FROM blocked_authors)
        ORDER BY timestamp ASC
    """)
    fun observeReplies(parentHash: String): Flow<List<PostWithAuthor>>

    @Transaction
    @Query("""
        SELECT * FROM posts_view 
        WHERE authorId = :authorId AND replyToHash IS NULL 
        AND (
            isPublic = 1 
            OR :authorId = :myAuthorId 
            OR :myAuthorId IN (SELECT followeeId FROM follows_view WHERE followerId = :authorId AND isFollowing = 1)
            OR :authorId IN (SELECT followeeId FROM follows_view WHERE followerId = :myAuthorId AND isFollowing = 1)
        )
        ORDER BY timestamp DESC
    """)
    fun observePostsByAuthor(authorId: String, myAuthorId: String): Flow<List<PostWithAuthor>>

    @Query("SELECT COUNT(*) FROM posts_view")
    suspend fun getTotalPostsCount(): Int
}

@Dao
interface ReactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReaction(reaction: ReactionEntity): Long

    @Query("SELECT COUNT(*) FROM reactions_view WHERE targetHash = :targetHash AND authorId = :authorId AND emoji = :emoji")
    suspend fun hasUserReacted(targetHash: String, authorId: String, emoji: String): Int

    @Query("SELECT COUNT(*) FROM reactions_view WHERE targetHash = :targetHash AND authorId = :authorId AND pollOptionIndex IS NULL")
    fun observeHasUserLiked(targetHash: String, authorId: String): Flow<Int>

    @Query("SELECT targetHash FROM reactions_view WHERE authorId = :authorId AND pollOptionIndex IS NULL")
    fun observeUserLikedPostHashes(authorId: String): Flow<List<String>>

    @Query("SELECT * FROM reactions_view WHERE targetHash = :targetHash")
    fun observeReactionsForPost(targetHash: String): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions_view WHERE targetHash = :targetHash AND pollOptionIndex IS NOT NULL")
    fun observePollVotesForPost(targetHash: String): Flow<List<ReactionEntity>>

    @Query("DELETE FROM reactions_view WHERE targetHash = :targetHash AND authorId = :authorId AND pollOptionIndex IS NOT NULL")
    suspend fun deleteExistingPollVote(targetHash: String, authorId: String)
}

@Dao
interface FollowDao {
    @Upsert
    suspend fun upsert(follow: FollowViewEntity)

    @Query("UPDATE follows_view SET isVerifiedInPerson = :verified WHERE followeeId = :authorId OR followerId = :authorId")
    suspend fun setVerifiedInPerson(authorId: String, verified: Boolean)

    @Query("""
        SELECT isVerifiedInPerson FROM follows_view 
        WHERE (followerId = :followerId AND followeeId = :targetId) 
           OR (followerId = :targetId AND followeeId = :followerId)
        ORDER BY isVerifiedInPerson DESC LIMIT 1
    """)
    suspend fun isVerifiedInPerson(followerId: String, targetId: String): Boolean?

    @Query("""
        SELECT isFollowing FROM follows_view 
        WHERE (followerId = :followerId AND followeeId = :targetId) 
           OR (followerId = :targetId AND followeeId = :followerId)
        ORDER BY isFollowing DESC LIMIT 1
    """)
    suspend fun isFollowing(followerId: String, targetId: String): Boolean?

    @Query("""
        SELECT followeeId FROM follows_view WHERE followerId = :followerId AND isFollowing = 1
        UNION
        SELECT followerId FROM follows_view WHERE followeeId = :followerId AND isFollowing = 1
    """)
    suspend fun getFollowedAuthorIds(followerId: String): List<String>

    @Query("""
        SELECT followeeId FROM follows_view WHERE followerId = :followerId AND isFollowing = 1
        UNION
        SELECT followerId FROM follows_view WHERE followeeId = :followerId AND isFollowing = 1
    """)
    fun observeFollowedAuthorIds(followerId: String): Flow<List<String>>

    @Query("""
        SELECT DISTINCT f2.followeeId FROM follows_view f1
        JOIN follows_view f2 ON f1.followeeId = f2.followerId
        WHERE (f1.followerId = :myAuthorId OR f1.followeeId = :myAuthorId) 
          AND f1.isFollowing = 1 AND f2.isFollowing = 1
    """)
    suspend fun getTwoHopFollowedAuthorIds(myAuthorId: String): List<String>
}

@Dao
interface PeerSyncStateDao {
    @Upsert
    suspend fun upsert(state: PeerSyncStateEntity)

    @Query("SELECT * FROM peer_sync_state WHERE authorId = :authorId LIMIT 1")
    suspend fun getState(authorId: String): PeerSyncStateEntity?

    @Query("SELECT * FROM peer_sync_state")
    suspend fun getAllSyncStates(): List<PeerSyncStateEntity>

    @Query("SELECT authorId, highestSequence FROM peer_sync_state")
    suspend fun getVectorClockTuples(): List<AuthorSequenceTuple>
}

@Dao
interface DirectMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: DirectMessageEntity): Long

    @Query("SELECT * FROM direct_messages WHERE conversationPeerId = :peerId ORDER BY timestamp ASC")
    fun observeMessagesForPeer(peerId: String): Flow<List<DirectMessageEntity>>

    @Query("""
        SELECT * FROM direct_messages 
        WHERE messageId IN (
            SELECT messageId FROM direct_messages 
            GROUP BY conversationPeerId 
            HAVING timestamp = MAX(timestamp)
        )
        ORDER BY timestamp DESC
    """)
    fun observeRecentConversations(): Flow<List<DirectMessageEntity>>

    @Query("UPDATE direct_messages SET isRead = 1 WHERE conversationPeerId = :peerId AND isOutgoing = 0")
    suspend fun markConversationAsRead(peerId: String)

    @Query("SELECT COUNT(*) FROM direct_messages WHERE isRead = 0 AND isOutgoing = 0")
    fun observeTotalUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM direct_messages WHERE conversationPeerId = :peerId AND isRead = 0 AND isOutgoing = 0")
    fun observeUnreadCountForPeer(peerId: String): Flow<Int>

    @Query("DELETE FROM direct_messages WHERE conversationPeerId = :peerId")
    suspend fun deleteConversation(peerId: String)

    @Query("DELETE FROM direct_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("UPDATE direct_messages SET status = :newStatus WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, newStatus: Int)

    @Query("UPDATE direct_messages SET status = :newStatus WHERE messageId = :messageId AND status < :newStatus")
    suspend fun updateMessageStatusIfHigher(messageId: String, newStatus: Int)

    @Query("SELECT * FROM direct_messages WHERE conversationPeerId = :peerId AND isOutgoing = 0 AND isRead = 0")
    suspend fun getUnreadIncomingMessagesForPeer(peerId: String): List<DirectMessageEntity>

    @Query("SELECT COUNT(*) FROM direct_messages WHERE mediaBlobHash = :blobHash")
    suspend fun isDirectMessageBlob(blobHash: String): Int
}

@Dao
interface BlockedAuthorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blocked: BlockedAuthorEntity)

    @Query("DELETE FROM blocked_authors WHERE authorId = :authorId")
    suspend fun unblock(authorId: String)

    @Query("SELECT COUNT(*) FROM blocked_authors WHERE authorId = :authorId")
    suspend fun isAuthorBlocked(authorId: String): Int

    @Query("SELECT * FROM blocked_authors ORDER BY blockedAt DESC")
    fun observeBlockedAuthors(): Flow<List<BlockedAuthorEntity>>

    @Query("SELECT * FROM blocked_authors")
    suspend fun getAllBlockedAuthors(): List<BlockedAuthorEntity>

    @Query("SELECT authorId FROM blocked_authors")
    suspend fun getAllBlockedAuthorIds(): List<String>
}

// -----------------------------------------------------------------------------------------
// VALIDATION & PROJECTION ENGINE
// -----------------------------------------------------------------------------------------

sealed interface ValidationResult {
    object Valid : ValidationResult
    object AlreadyExists : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

object LogValidator {

    /**
     * Strictly verifies incoming LogEntry according to SSb-inspired append-only log rules:
     * 1. Cryptographic Ed25519 signature validity against authorId and canonical payload string.
     * 2. Sequence continuity (starts at 1, no gaps).
     * 3. Previous hash verification (null if seq 1, matches sequence - 1 entryHash if seq > 1).
     * 4. entryHash matches computed SHA-256 over entire signed entry.
     * 5. Canonical JSON schema conformance for contentType.
     */
    suspend fun validate(entry: LogEntry, db: MeshDatabase): ValidationResult {
        // Check if entry is already present in database (idempotent handling)
        val existing = db.logEntryDao().getEntryByHash(entry.entryHash)
        if (existing != null) {
            return ValidationResult.AlreadyExists
        }

        // Drop packets immediately if author is locally blocked or Sybil-evicted
        if (db.blockedAuthorDao().isAuthorBlocked(entry.authorId) > 0) {
            return ValidationResult.Invalid("Author ${entry.authorId} is blocked or Sybil-evicted")
        }

        // Sequence must be >= 1
        if (entry.sequence < 1L) {
            return ValidationResult.Invalid("Sequence must be >= 1, received: ${entry.sequence}")
        }

        // 1. Verify Entry Hash matches computed SHA-256
        val computedHash = CryptoManager.computeEntryHash(
            authorId = entry.authorId,
            sequence = entry.sequence,
            previousHash = entry.previousHash,
            timestamp = entry.timestamp,
            contentType = entry.contentType.name,
            contentJson = entry.contentJson,
            signature = entry.signature
        )
        if (computedHash != entry.entryHash) {
            return ValidationResult.Invalid("entryHash mismatch: computed $computedHash != ${entry.entryHash}")
        }

        // 2. Verify Cryptographic Ed25519 Signature
        val canonicalPayload = CryptoManager.buildCanonicalPayload(
            authorId = entry.authorId,
            sequence = entry.sequence,
            previousHash = entry.previousHash,
            timestamp = entry.timestamp,
            contentType = entry.contentType.name,
            contentJson = entry.contentJson
        )
        val isSignatureValid = CryptoManager.verify(
            authorId = entry.authorId,
            canonicalPayload = canonicalPayload,
            signatureBase64 = entry.signature
        )
        if (!isSignatureValid) {
            return ValidationResult.Invalid("Ed25519 signature verification failed for author ${entry.authorId}")
        }

        // 2b. Verify Hashcash Proof-of-Work Anti-Spam Gate
        val isSos = entry.contentType == ContentType.POST && entry.contentJson.contains("\"isSos\":true")
        val isPowValid = ProofOfWorkEngine.verifyEntry(entry, isSos)
        if (!isPowValid) {
            return ValidationResult.Invalid("Hashcash Proof-of-Work verification failed for author ${entry.authorId} (nonce=${entry.powNonce})")
        }

        // 3. Verify Sequence & Previous Hash Chain
        if (entry.sequence == 1L) {
            if (entry.previousHash != null) {
                return ValidationResult.Invalid("Sequence 1 entry must have null previousHash, received: ${entry.previousHash}")
            }
            // Check if there is already a sequence 1 for this author
            val existingSeq1 = db.logEntryDao().getEntryBySequence(entry.authorId, 1L)
            if (existingSeq1 != null) {
                return ValidationResult.Invalid("Author ${entry.authorId} already has sequence 1 with different hash: ${existingSeq1.entryHash}")
            }
        } else {
            if (entry.previousHash == null) {
                return ValidationResult.Invalid("Sequence ${entry.sequence} entry must have non-null previousHash")
            }
            val previousEntry = db.logEntryDao().getEntryBySequence(entry.authorId, entry.sequence - 1L)
                ?: return ValidationResult.Invalid("Missing sequence ${entry.sequence - 1L} for author ${entry.authorId} (out of order sync)")

            if (previousEntry.entryHash != entry.previousHash) {
                return ValidationResult.Invalid("previousHash mismatch: expected ${previousEntry.entryHash}, received ${entry.previousHash}")
            }
        }

        // 4. Validate Content JSON Schema
        val schemaValid = validateContentJson(entry.contentType, entry.contentJson)
        if (!schemaValid) {
            return ValidationResult.Invalid("Malformed contentJson for type ${entry.contentType}")
        }

        return ValidationResult.Valid
    }

    private fun validateContentJson(contentType: ContentType, json: String): Boolean {
        return try {
            when (contentType) {
                ContentType.POST -> {
                    val p = PostPayload.fromJson(json)
                    p.text.isNotBlank() || p.media != null || !p.pollOptions.isNullOrEmpty()
                }
                ContentType.REPLY -> {
                    val r = ReplyPayload.fromJson(json)
                    r.text.isNotBlank() && r.parentHash.isNotBlank() && r.rootHash.isNotBlank()
                }
                ContentType.REACTION -> {
                    val rx = ReactionPayload.fromJson(json)
                    rx.targetHash.isNotBlank() && (rx.emoji.isNotBlank() || rx.pollOptionIndex != null)
                }
                ContentType.PROFILE -> {
                    val pr = ProfilePayload.fromJson(json)
                    pr.displayName.isNotBlank()
                }
                ContentType.FOLLOW -> {
                    val f = FollowPayload.fromJson(json)
                    f.targetAuthorId.isNotBlank()
                }
                ContentType.POST_EDIT -> {
                    val pe = PostEditPayload.fromJson(json)
                    pe.targetHash.isNotBlank() && pe.newText.isNotBlank()
                }
                ContentType.POST_DELETE -> {
                    val pd = PostDeletePayload.fromJson(json)
                    pd.targetHash.isNotBlank()
                }
                ContentType.DIRECT_MESSAGE -> {
                    val dm = com.mysocial.mesh.crypto.DirectMessagePayload.fromJson(json)
                    dm.recipientId.isNotBlank() && dm.ciphertextBase64.isNotBlank() && dm.ephemeralPublicKeyBase64.isNotBlank()
                }
                ContentType.DIRECT_MESSAGE_RECEIPT -> {
                    val r = com.mysocial.mesh.model.DirectMessageReceiptPayload.fromJson(json)
                    r.messageId.isNotBlank() && r.originalSenderId.isNotBlank() && r.recipientId.isNotBlank() && (r.status == 2 || r.status == 3)
                }
                ContentType.GROUP_MESSAGE -> {
                    val gm = GroupMessagePayload.fromJson(json)
                    gm.groupId.isNotBlank() && gm.messageId.isNotBlank() && gm.ciphertextBase64.isNotBlank()
                }
                ContentType.PTT_VOICE_BURST -> {
                    val ptt = com.mysocial.mesh.model.PttVoiceBurstPayload.fromJson(json)
                    ptt.burstId.isNotBlank() && ptt.audioBase64.isNotBlank()
                }
                ContentType.GATEWAY_DELIVERY_RECEIPT -> {
                    val g = com.mysocial.mesh.model.GatewayDeliveryReceiptPayload.fromJson(json)
                    g.targetHash.isNotBlank() && g.gatewayAuthorId.isNotBlank()
                }
                ContentType.STORY -> {
                    val s = com.mysocial.mesh.model.StoryPayload.fromJson(json)
                    s.storyId.isNotBlank() && s.authorId.isNotBlank()
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}

object LogProjector {

    /**
     * Synchronously projects a verified LogEntry into the query-optimized materialized view tables.
     */
    suspend fun project(
        entry: LogEntry,
        db: MeshDatabase,
        hopDistance: Int = 0,
        hopCount: Int = 0,
        transportSource: String = "LOCAL"
    ) {
        projectSingleWithoutSyncState(
            entry = entry,
            db = db,
            hopDistance = hopDistance,
            hopCount = hopCount,
            transportSource = transportSource
        )

        // Update peer_sync_state Vector Clock entry for this author
        val existingState = db.peerSyncStateDao().getState(entry.authorId)
        if (existingState == null || entry.sequence > existingState.highestSequence) {
            db.peerSyncStateDao().upsert(
                PeerSyncStateEntity(
                    authorId = entry.authorId,
                    highestSequence = entry.sequence,
                    lastHash = entry.entryHash,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Projects a verified LogEntry without touching peer_sync_state (used during batch processing).
     */
    suspend fun projectSingleWithoutSyncState(
        entry: LogEntry,
        db: MeshDatabase,
        hopDistance: Int = 0,
        hopCount: Int = 0,
        transportSource: String = "LOCAL"
    ) {
        // 1. Insert into raw ledger log_entries table
        db.logEntryDao().insert(
            entry.toEntity(
                hopDistance = hopDistance,
                hopCount = hopCount,
                transportSource = transportSource
            )
        )

        // 2. Project into Materialized Views based on ContentType
        when (entry.contentType) {
            ContentType.PROFILE -> {
                val payload = ProfilePayload.fromJson(entry.contentJson)
                val current = db.profileDao().getProfile(entry.authorId)
                if (current == null || entry.sequence >= current.updatedAtSeq) {
                    db.profileDao().upsert(
                        ProfileViewEntity(
                            authorId = entry.authorId,
                            displayName = payload.displayName,
                            bio = payload.bio,
                            avatarSeed = payload.avatarSeed,
                            updatedAtSeq = entry.sequence,
                            isVerifiedInPerson = current?.isVerifiedInPerson ?: false
                        )
                    )
                }
            }

            ContentType.POST -> {
                val payload = PostPayload.fromJson(entry.contentJson)
                val tagsJson = CanonicalJson.encodeToString(payload.tags)
                val pollOptionsJson = payload.pollOptions?.let { CanonicalJson.encodeToString(it) }
                val media = payload.media
                db.postViewDao().upsert(
                    PostViewEntity(
                        entryHash = entry.entryHash,
                        authorId = entry.authorId,
                        text = payload.text,
                        tagsJson = tagsJson,
                        timestamp = entry.timestamp,
                        replyToHash = null,
                        rootHash = null,
                        likeCount = 0,
                        replyCount = 0,
                        hopDistance = hopDistance,
                        mediaBlobHash = media?.blobHash,
                        mediaMimeType = media?.mimeType,
                        mediaTotalBytes = media?.totalBytes,
                        mediaChunkCount = media?.chunkCount,
                        microPreviewBase64 = media?.microPreviewBase64,
                        isPublic = payload.isPublic,
                        hopCount = hopCount,
                        transportSource = transportSource,
                        expiresAt = payload.expiresAt,
                        isSos = payload.isSos,
                        pollOptionsJson = pollOptionsJson
                    )
                )

                if (media != null) {
                    val existingManifest = db.blobDao().getManifest(media.blobHash)
                    if (existingManifest == null) {
                        db.blobDao().upsertManifest(
                            BlobManifestEntity(
                                blobHash = media.blobHash,
                                totalBytes = media.totalBytes,
                                chunkCount = media.chunkCount,
                                mimeType = media.mimeType,
                                isComplete = false,
                                localFilePath = null
                            )
                        )
                    }
                }
            }

            ContentType.REPLY -> {
                val payload = ReplyPayload.fromJson(entry.contentJson)
                db.postViewDao().upsert(
                    PostViewEntity(
                        entryHash = entry.entryHash,
                        authorId = entry.authorId,
                        text = payload.text,
                        tagsJson = "[]",
                        timestamp = entry.timestamp,
                        replyToHash = payload.parentHash,
                        rootHash = payload.rootHash,
                        likeCount = 0,
                        replyCount = 0,
                        hopDistance = hopDistance,
                        hopCount = hopCount,
                        transportSource = transportSource
                    )
                )
                // Increment reply counts on parent and root posts
                db.postViewDao().incrementReplyCount(payload.parentHash)
                if (payload.rootHash != payload.parentHash) {
                    db.postViewDao().incrementReplyCount(payload.rootHash)
                }
            }

            ContentType.REACTION -> {
                val payload = ReactionPayload.fromJson(entry.contentJson)
                if (payload.pollOptionIndex != null) {
                    db.reactionDao().deleteExistingPollVote(payload.targetHash, entry.authorId)
                }
                val emojiValue = if (payload.pollOptionIndex != null) "poll:${payload.pollOptionIndex}" else payload.emoji
                val insertedRow = db.reactionDao().insertReaction(
                    ReactionEntity(
                        targetHash = payload.targetHash,
                        authorId = entry.authorId,
                        emoji = emojiValue,
                        timestamp = entry.timestamp,
                        pollOptionIndex = payload.pollOptionIndex
                    )
                )
                // Only increment likeCount if this reaction was newly inserted and not a poll vote
                if (insertedRow != -1L && payload.pollOptionIndex == null) {
                    db.postViewDao().incrementLikeCount(payload.targetHash)
                }
            }

            ContentType.FOLLOW -> {
                val payload = FollowPayload.fromJson(entry.contentJson)
                val isVerified = db.followDao().isVerifiedInPerson(entry.authorId, payload.targetAuthorId) ?: false
                db.followDao().upsert(
                    FollowViewEntity(
                        followerId = entry.authorId,
                        followeeId = payload.targetAuthorId,
                        isFollowing = payload.following,
                        updatedAtSeq = entry.sequence,
                        isVerifiedInPerson = isVerified
                    )
                )
            }

            ContentType.POST_EDIT -> {
                val payload = PostEditPayload.fromJson(entry.contentJson)
                val post = db.postViewDao().getPost(payload.targetHash)
                if (post == null || post.authorId == entry.authorId) {
                    db.postViewDao().updatePostText(payload.targetHash, payload.newText)
                }
            }

            ContentType.POST_DELETE -> {
                val payload = PostDeletePayload.fromJson(entry.contentJson)
                val post = db.postViewDao().getPost(payload.targetHash)
                if (post == null || post.authorId == entry.authorId) {
                    db.postViewDao().deletePost(payload.targetHash)
                }
            }

            ContentType.DIRECT_MESSAGE -> {
                try {
                    val payload = com.mysocial.mesh.crypto.DirectMessagePayload.fromJson(entry.contentJson)
                    val crypto = db.cryptoManager
                    val myAuthorId = crypto?.authorId
                    if (myAuthorId != null) {
                        if (payload.recipientId == myAuthorId) {
                            // Inbound direct message for me: attempt E2EE decryption
                            val decryptedText = crypto.decryptDirectMessage(payload)
                            if (decryptedText != null) {
                                val content = com.mysocial.mesh.model.DirectMessageContent.fromDecryptedString(decryptedText)

                                // If message contains media blob, register incomplete manifest so gossip sync downloads it
                                if (content.mediaBlobHash != null) {
                                    val existingManifest = db.blobDao().getManifest(content.mediaBlobHash)
                                    if (existingManifest == null) {
                                        db.blobDao().upsertManifest(
                                            BlobManifestEntity(
                                                blobHash = content.mediaBlobHash,
                                                totalBytes = content.mediaTotalBytes ?: 0L,
                                                chunkCount = content.mediaChunkCount ?: 1,
                                                mimeType = content.mediaMimeType ?: (if (content.mediaType == "AUDIO") "audio/m4a" else "image/webp"),
                                                isComplete = false,
                                                localFilePath = null
                                            )
                                        )
                                    }
                                    // Trigger immediate prioritized blob download over active connections
                                    db.onBlobDiscovered?.invoke(content.mediaBlobHash)
                                }

                                db.directMessageDao().insertMessage(
                                    DirectMessageEntity(
                                        messageId = payload.messageId,
                                        conversationPeerId = entry.authorId,
                                        senderId = entry.authorId,
                                        recipientId = myAuthorId,
                                        text = content.text,
                                        timestamp = payload.timestamp,
                                        isOutgoing = false,
                                        isRead = false,
                                        status = 1,
                                        mediaType = content.mediaType,
                                        mediaBlobHash = content.mediaBlobHash,
                                        mediaMimeType = content.mediaMimeType,
                                        mediaChunkCount = content.mediaChunkCount,
                                        mediaDurationMs = content.mediaDurationMs,
                                        mediaMicroPreviewBase64 = content.mediaMicroPreviewBase64
                                    )
                                )

                                // Automatically acknowledge delivery receipt (status = 2) back to sender
                                db.onDirectMessageReceived?.invoke(payload.messageId, entry.authorId)

                                // Fire high-priority direct message notification
                                val senderProfile = db.profileDao().getProfile(entry.authorId)
                                val senderName = senderProfile?.displayName ?: "Someone Nearby"
                                val notifSnippet = when (content.mediaType) {
                                    "IMAGE" -> if (content.text.isNotBlank()) "📷 ${content.text}" else "📷 Photo"
                                    "AUDIO" -> "🎤 Voice message (${((content.mediaDurationMs ?: 0L) / 1000).coerceAtLeast(1)}s)"
                                    else -> content.text
                                }
                                db.appContext?.let { ctx ->
                                    com.mysocial.mesh.utils.PeerNotificationHelper.notifyDirectMessage(
                                        ctx, senderName, notifSnippet, entry.authorId
                                    )
                                }
                            }
                        } else if (entry.authorId == myAuthorId) {
                            // Outgoing message confirmation
                            db.directMessageDao().updateMessageStatusIfHigher(payload.messageId, 1)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MeshDatabase", "Failed to project direct message: ${e.message}")
                }
            }

            ContentType.DIRECT_MESSAGE_RECEIPT -> {
                try {
                    val payload = com.mysocial.mesh.model.DirectMessageReceiptPayload.fromJson(entry.contentJson)
                    val crypto = db.cryptoManager
                    val myAuthorId = crypto?.authorId
                    if (myAuthorId != null && payload.originalSenderId == myAuthorId) {
                        // Original sender receives delivery or read confirmation
                        db.directMessageDao().updateMessageStatusIfHigher(payload.messageId, payload.status)
                    }
                } catch (e: Exception) {
                    Log.w("MeshDatabase", "Failed to project direct message receipt: ${e.message}")
                }
            }

            ContentType.GROUP_MESSAGE -> {
                try {
                    val payload = GroupMessagePayload.fromJson(entry.contentJson)
                    val group = db.groupDao().getGroup(payload.groupId)
                    val myAuthorId = db.cryptoManager?.authorId
                    val isOutgoing = (entry.authorId == myAuthorId)

                    var decryptedSnippet: String? = null
                    if (group != null) {
                        try {
                            val keyBytes = android.util.Base64.decode(group.senderKeyBase64, android.util.Base64.NO_WRAP)
                            val ivBytes = android.util.Base64.decode(payload.ivBase64, android.util.Base64.NO_WRAP)
                            val cipherBytes = android.util.Base64.decode(payload.ciphertextBase64, android.util.Base64.NO_WRAP)
                            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                            val spec = javax.crypto.spec.GCMParameterSpec(128, ivBytes)
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(keyBytes, "AES"), spec)
                            val plainBytes = cipher.doFinal(cipherBytes)
                            val plainText = String(plainBytes, Charsets.UTF_8)
                            val content = com.mysocial.mesh.model.GroupPlaintextContent.fromJson(plainText)
                            decryptedSnippet = content.text
                        } catch (e: Exception) {
                            Log.w("MeshDatabase", "Failed to decrypt group message: ${e.message}")
                        }
                    }

                    db.groupDao().insertMessage(
                        GroupMessageEntity(
                            messageId = payload.messageId,
                            groupId = payload.groupId,
                            senderAuthorId = payload.senderAuthorId,
                            senderDisplayName = payload.senderDisplayName,
                            ciphertextBase64 = payload.ciphertextBase64,
                            plaintextSnippet = decryptedSnippet,
                            mediaBlobHash = payload.mediaBlobHash,
                            mediaType = payload.mediaType,
                            timestamp = payload.timestamp,
                            isOutgoing = isOutgoing,
                            status = if (isOutgoing) 1 else 0
                        )
                    )

                    if (!isOutgoing && decryptedSnippet != null && group != null) {
                        db.appContext?.let { ctx ->
                            com.mysocial.mesh.utils.PeerNotificationHelper.notifyGroupMessage(
                                ctx, group.groupName, payload.senderDisplayName, decryptedSnippet, group.groupId
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MeshDatabase", "Failed to project group message: ${e.message}")
                }
            }

            ContentType.GATEWAY_DELIVERY_RECEIPT -> {
                try {
                    val payload = com.mysocial.mesh.model.GatewayDeliveryReceiptPayload.fromJson(entry.contentJson)
                    db.postViewDao().markGatewayRelayed(payload.targetHash, payload.relayedAt)
                } catch (e: Exception) {
                    Log.w("MeshDatabase", "Failed to project gateway receipt: ${e.message}")
                }
            }

            ContentType.STORY -> {
                try {
                    val payload = com.mysocial.mesh.model.StoryPayload.fromJson(entry.contentJson)
                    db.storyDao().insert(
                        StoryEntity(
                            storyId = payload.storyId,
                            authorId = payload.authorId,
                            authorDisplayName = payload.authorDisplayName,
                            text = payload.text,
                            mediaBlobHash = payload.mediaBlobHash,
                            createdAt = payload.createdAt,
                            expiresAt = payload.expiresAt,
                            isViewed = false
                        )
                    )
                } catch (e: Exception) {
                    Log.w("MeshDatabase", "Failed to project story: ${e.message}")
                }
            }

            ContentType.PTT_VOICE_BURST -> {
                try {
                    val pttPayload = com.mysocial.mesh.model.PttVoiceBurstPayload.fromJson(entry.contentJson)
                    db.onPttVoiceBurstReceived?.invoke(pttPayload)
                } catch (e: Exception) {
                    Log.w("MeshDatabase", "Failed to project PTT voice burst: ${e.message}")
                }
            }
        }
    }
}

data class BatchResult(val acceptedCount: Int, val errors: List<String>)

// -----------------------------------------------------------------------------------------
// ROOM DATABASE DEFINITION
// -----------------------------------------------------------------------------------------

@Database(
    entities = [
        LogEntryEntity::class,
        ProfileViewEntity::class,
        PostViewEntity::class,
        ReactionEntity::class,
        FollowViewEntity::class,
        PeerSyncStateEntity::class,
        BlobManifestEntity::class,
        BlobChunkEntity::class,
        DirectMessageEntity::class,
        BlockedAuthorEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupMessageEntity::class,
        StoryEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MeshDatabase : RoomDatabase() {

    @Volatile
    var cryptoManager: com.mysocial.mesh.crypto.CryptoManager? = null

    @Volatile
    var appContext: Context? = null

    @Volatile
    var onDirectMessageReceived: ((messageId: String, senderId: String) -> Unit)? = null

    @Volatile
    var onBlobDiscovered: ((blobHash: String) -> Unit)? = null

    @Volatile
    var onPttVoiceBurstReceived: ((com.mysocial.mesh.model.PttVoiceBurstPayload) -> Unit)? = null

    abstract fun logEntryDao(): LogEntryDao
    abstract fun profileDao(): ProfileDao
    abstract fun postViewDao(): PostViewDao
    abstract fun reactionDao(): ReactionDao
    abstract fun followDao(): FollowDao
    abstract fun peerSyncStateDao(): PeerSyncStateDao
    abstract fun blobDao(): BlobDao
    abstract fun directMessageDao(): DirectMessageDao
    abstract fun blockedAuthorDao(): BlockedAuthorDao
    abstract fun groupDao(): GroupDao
    abstract fun storyDao(): StoryDao

    /**
     * Atomically validates a LogEntry and projects it into the materialized views.
     */
    suspend fun insertAndProject(
        entry: LogEntry,
        hopDistance: Int = 0,
        hopCount: Int = 0,
        transportSource: String = "LOCAL"
    ): Result<Unit> {
        return try {
            withTransaction {
                when (val validation = LogValidator.validate(entry, this)) {
                    is ValidationResult.AlreadyExists -> {
                        Result.success(Unit)
                    }
                    is ValidationResult.Invalid -> {
                        Result.failure(IllegalArgumentException("LogEntry validation failed: ${validation.reason}"))
                    }
                    is ValidationResult.Valid -> {
                        LogProjector.project(
                            entry = entry,
                            db = this,
                            hopDistance = hopDistance,
                            hopCount = hopCount,
                            transportSource = transportSource
                        )
                        Result.success(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically validates and projects a batch of LogEntries within a SINGLE transaction,
     * minimizing disk fsync operations, reducing Room table invalidation notifications,
     * and coalescing peer_sync_state vector clock updates.
     */
    suspend fun insertAndProjectBatch(
        entries: List<LogEntry>,
        hopDistance: Int = 1,
        hopCounts: List<Int>? = null,
        transportSource: String = "LOCAL"
    ): BatchResult {
        if (entries.isEmpty()) return BatchResult(0, emptyList())
        return withTransaction {
            var acceptedCount = 0
            val errors = mutableListOf<String>()
            val maxSequencePerAuthor = mutableMapOf<String, Pair<Long, String>>()

            for ((index, entry) in entries.withIndex()) {
                when (val validation = LogValidator.validate(entry, this@MeshDatabase)) {
                    is ValidationResult.AlreadyExists -> {
                        // Idempotent skip
                    }
                    is ValidationResult.Invalid -> {
                        errors.add("${entry.authorId}#${entry.sequence}: ${validation.reason}")
                    }
                    is ValidationResult.Valid -> {
                        val entryHopCount = hopCounts?.getOrNull(index) ?: hopDistance
                        LogProjector.projectSingleWithoutSyncState(
                            entry = entry,
                            db = this@MeshDatabase,
                            hopDistance = hopDistance,
                            hopCount = entryHopCount,
                            transportSource = transportSource
                        )
                        acceptedCount++

                        val currentMax = maxSequencePerAuthor[entry.authorId]?.first ?: 0L
                        if (entry.sequence > currentMax) {
                            maxSequencePerAuthor[entry.authorId] = Pair(entry.sequence, entry.entryHash)
                        }
                    }
                }
            }

            // Coalesced update of vector clocks once per author for the entire batch
            val now = System.currentTimeMillis()
            for ((authorId, pair) in maxSequencePerAuthor) {
                val (maxSeq, lastHash) = pair
                val existing = peerSyncStateDao().getState(authorId)
                if (existing == null || maxSeq > existing.highestSequence) {
                    peerSyncStateDao().upsert(
                        PeerSyncStateEntity(
                            authorId = authorId,
                            highestSequence = maxSeq,
                            lastHash = lastHash,
                            lastSyncedAt = now
                        )
                    )
                }
            }

            BatchResult(acceptedCount, errors)
        }
    }

    companion object {
        private const val DATABASE_NAME = "mysocial_mesh.db"

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_view_replyToHash_isSos_timestamp` ON `posts_view` (`replyToHash`, `isSos`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_view_authorId_replyToHash_timestamp` ON `posts_view` (`authorId`, `replyToHash`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_direct_messages_conversationPeerId_timestamp` ON `direct_messages` (`conversationPeerId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_direct_messages_isRead_isOutgoing` ON `direct_messages` (`isRead`, `isOutgoing`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reactions_view_authorId_pollOptionIndex` ON `reactions_view` (`authorId`, `pollOptionIndex`)")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `powNonce` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `blocked_authors` (
                        `authorId` TEXT NOT NULL,
                        `blockedAt` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `isSybilEvicted` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`authorId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_authors_authorId` ON `blocked_authors` (`authorId`)")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_authors_authorId` ON `blocked_authors` (`authorId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups_view` (
                        `groupId` TEXT NOT NULL,
                        `groupName` TEXT NOT NULL,
                        `creatorAuthorId` TEXT NOT NULL,
                        `senderKeyBase64` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `memberCount` INTEGER NOT NULL,
                        `avatarBlobHash` TEXT,
                        `description` TEXT NOT NULL,
                        PRIMARY KEY(`groupId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_view_creatorAuthorId` ON `groups_view` (`creatorAuthorId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_view_createdAt` ON `groups_view` (`createdAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `group_members` (
                        `groupId` TEXT NOT NULL,
                        `authorId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `joinedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`groupId`, `authorId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_authorId` ON `group_members` (`authorId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `group_messages` (
                        `messageId` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `senderAuthorId` TEXT NOT NULL,
                        `senderDisplayName` TEXT NOT NULL,
                        `ciphertextBase64` TEXT NOT NULL,
                        `plaintextSnippet` TEXT,
                        `mediaBlobHash` TEXT,
                        `mediaType` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        `isOutgoing` INTEGER NOT NULL,
                        `status` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId_timestamp` ON `group_messages` (`groupId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_senderAuthorId` ON `group_messages` (`senderAuthorId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stories_view` (
                        `storyId` TEXT NOT NULL,
                        `authorId` TEXT NOT NULL,
                        `authorDisplayName` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `mediaBlobHash` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `isViewed` INTEGER NOT NULL,
                        PRIMARY KEY(`storyId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_view_authorId` ON `stories_view` (`authorId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_view_expiresAt` ON `stories_view` (`expiresAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_view_createdAt` ON `stories_view` (`createdAt`)")

                db.execSQL("ALTER TABLE `posts_view` ADD COLUMN `gatewayRelayedAt` INTEGER")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_authors_authorId` ON `blocked_authors` (`authorId`)")
            }
        }

        @Volatile
        private var INSTANCE: MeshDatabase? = null

        fun getInstance(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MeshDatabase {
            return Room.databaseBuilder(
                context,
                MeshDatabase::class.java,
                DATABASE_NAME
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                .also { it.appContext = context.applicationContext }
        }

        fun buildInMemory(context: Context): MeshDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                MeshDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
