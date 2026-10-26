package com.mysocial.mesh.data

import android.content.Context
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
import com.mysocial.mesh.model.AuthorSequenceTuple
import com.mysocial.mesh.model.CanonicalJson
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.FollowViewEntity
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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LogEntryEntity)

    @Query("SELECT * FROM log_entries WHERE entryHash = :hash LIMIT 1")
    suspend fun getEntryByHash(hash: String): LogEntryEntity?

    @Query("DELETE FROM log_entries WHERE entryHash = :hash")
    suspend fun deleteEntry(hash: String)

    @Query("SELECT * FROM log_entries WHERE authorId = :authorId ORDER BY sequence DESC LIMIT 1")
    suspend fun getLatestEntryForAuthor(authorId: String): LogEntryEntity?

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
}

@Dao
interface PostViewDao {
    @Upsert
    suspend fun upsert(post: PostViewEntity)

    @Query("SELECT * FROM posts_view WHERE entryHash = :hash LIMIT 1")
    suspend fun getPost(hash: String): PostViewEntity?

    @Query("DELETE FROM posts_view WHERE entryHash = :hash OR replyToHash = :hash")
    suspend fun deletePost(hash: String)

    @Query("UPDATE posts_view SET text = :newText WHERE entryHash = :hash")
    suspend fun updatePostText(hash: String, newText: String)

    @Query("UPDATE posts_view SET likeCount = likeCount + 1 WHERE entryHash = :targetHash")
    suspend fun incrementLikeCount(targetHash: String)

    @Query("UPDATE posts_view SET replyCount = replyCount + 1 WHERE entryHash = :parentHash")
    suspend fun incrementReplyCount(parentHash: String)

    @Transaction
    @Query("SELECT * FROM posts_view WHERE replyToHash IS NULL ORDER BY timestamp DESC")
    fun observeAllFeed(): Flow<List<PostWithAuthor>>

    @Transaction
    @Query("""
        SELECT * FROM posts_view 
        WHERE replyToHash IS NULL 
        AND (
            authorId = :myAuthorId 
            OR authorId IN (SELECT followeeId FROM follows_view WHERE followerId = :myAuthorId AND isFollowing = 1)
            OR authorId IN (
                SELECT f2.followeeId FROM follows_view f1 
                JOIN follows_view f2 ON f1.followeeId = f2.followerId 
                WHERE f1.followerId = :myAuthorId AND f1.isFollowing = 1 AND f2.isFollowing = 1
            )
        )
        ORDER BY timestamp DESC
    """)
    fun observeFollowedAndTwoHopFeed(myAuthorId: String): Flow<List<PostWithAuthor>>

    @Transaction
    @Query("SELECT * FROM posts_view WHERE replyToHash = :parentHash OR rootHash = :parentHash ORDER BY timestamp ASC")
    fun observeReplies(parentHash: String): Flow<List<PostWithAuthor>>

    @Transaction
    @Query("SELECT * FROM posts_view WHERE authorId = :authorId AND replyToHash IS NULL ORDER BY timestamp DESC")
    fun observePostsByAuthor(authorId: String): Flow<List<PostWithAuthor>>

    @Query("SELECT COUNT(*) FROM posts_view")
    suspend fun getTotalPostsCount(): Int
}

@Dao
interface ReactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReaction(reaction: ReactionEntity): Long

    @Query("SELECT COUNT(*) FROM reactions_view WHERE targetHash = :targetHash AND authorId = :authorId AND emoji = :emoji")
    suspend fun hasUserReacted(targetHash: String, authorId: String, emoji: String): Int

    @Query("SELECT * FROM reactions_view WHERE targetHash = :targetHash")
    fun observeReactionsForPost(targetHash: String): Flow<List<ReactionEntity>>
}

@Dao
interface FollowDao {
    @Upsert
    suspend fun upsert(follow: FollowViewEntity)

    @Query("SELECT isFollowing FROM follows_view WHERE followerId = :followerId AND followeeId = :targetId LIMIT 1")
    suspend fun isFollowing(followerId: String, targetId: String): Boolean?

    @Query("SELECT followeeId FROM follows_view WHERE followerId = :followerId AND isFollowing = 1")
    suspend fun getFollowedAuthorIds(followerId: String): List<String>

    @Query("SELECT followeeId FROM follows_view WHERE followerId = :followerId AND isFollowing = 1")
    fun observeFollowedAuthorIds(followerId: String): Flow<List<String>>

    @Query("""
        SELECT DISTINCT f2.followeeId FROM follows_view f1
        JOIN follows_view f2 ON f1.followeeId = f2.followerId
        WHERE f1.followerId = :myAuthorId AND f1.isFollowing = 1 AND f2.isFollowing = 1
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
                    p.text.isNotBlank()
                }
                ContentType.REPLY -> {
                    val r = ReplyPayload.fromJson(json)
                    r.text.isNotBlank() && r.parentHash.isNotBlank() && r.rootHash.isNotBlank()
                }
                ContentType.REACTION -> {
                    val rx = ReactionPayload.fromJson(json)
                    rx.targetHash.isNotBlank() && rx.emoji.isNotBlank()
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
    suspend fun project(entry: LogEntry, db: MeshDatabase, hopDistance: Int) {
        // 1. Insert into raw ledger log_entries table
        db.logEntryDao().insert(entry.toEntity(hopDistance))

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
                            updatedAtSeq = entry.sequence
                        )
                    )
                }
            }

            ContentType.POST -> {
                val payload = PostPayload.fromJson(entry.contentJson)
                val tagsJson = CanonicalJson.encodeToString(payload.tags)
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
                        microPreviewBase64 = media?.microPreviewBase64
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
                        hopDistance = hopDistance
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
                val insertedRow = db.reactionDao().insertReaction(
                    ReactionEntity(
                        targetHash = payload.targetHash,
                        authorId = entry.authorId,
                        emoji = payload.emoji,
                        timestamp = entry.timestamp
                    )
                )
                // Only increment likeCount if this reaction was newly inserted (prevents duplicate gossip counts)
                if (insertedRow != -1L) {
                    db.postViewDao().incrementLikeCount(payload.targetHash)
                }
            }

            ContentType.FOLLOW -> {
                val payload = FollowPayload.fromJson(entry.contentJson)
                val current = db.followDao().isFollowing(entry.authorId, payload.targetAuthorId)
                db.followDao().upsert(
                    FollowViewEntity(
                        followerId = entry.authorId,
                        followeeId = payload.targetAuthorId,
                        isFollowing = payload.following,
                        updatedAtSeq = entry.sequence
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
        }

        // 3. Update peer_sync_state Vector Clock entry for this author
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
}

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
        BlobChunkEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MeshDatabase : RoomDatabase() {

    abstract fun logEntryDao(): LogEntryDao
    abstract fun profileDao(): ProfileDao
    abstract fun postViewDao(): PostViewDao
    abstract fun reactionDao(): ReactionDao
    abstract fun followDao(): FollowDao
    abstract fun peerSyncStateDao(): PeerSyncStateDao
    abstract fun blobDao(): BlobDao

    /**
     * Atomically validates a LogEntry and projects it into the materialized views.
     */
    suspend fun insertAndProject(entry: LogEntry, hopDistance: Int = 0): Result<Unit> {
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
                        LogProjector.project(entry, this, hopDistance)
                        Result.success(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val DATABASE_NAME = "mysocial_mesh.db"

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
                .fallbackToDestructiveMigration()
                .build()
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
