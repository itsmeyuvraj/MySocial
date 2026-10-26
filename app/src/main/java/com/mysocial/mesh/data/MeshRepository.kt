package com.mysocial.mesh.data

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.PostEditPayload
import com.mysocial.mesh.model.PostDeletePayload
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.model.ProfilePayload
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.model.ReactionPayload
import com.mysocial.mesh.model.ReplyPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.SyncEvent
import com.mysocial.mesh.transport.DiscoveredPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

data class DatabaseStats(
    val totalEntries: Int,
    val totalAuthors: Int,
    val totalPosts: Int
)

/**
 * Single source of truth repository orchestrating Room database queries,
 * cryptographic identity operations, and peer gossip sync services.
 */
class MeshRepository(
    private val db: MeshDatabase,
    val cryptoManager: CryptoManager,
    private val syncEngine: GossipSyncEngine,
    val appSettings: AppSettings? = null
) {
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    var onContentPublished: (() -> Unit)? = null

    private val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())

    private val _activityLogs = MutableStateFlow<List<String>>(
        listOf("${timeFormat.format(java.util.Date())} • Mesh active • Scanning for nearby devices")
    )
    val activityLogs: StateFlow<List<String>> = _activityLogs.asStateFlow()

    fun logActivity(message: String) {
        val time = timeFormat.format(java.util.Date())
        val entry = "$time • $message"
        val current = _activityLogs.value
        if (current.firstOrNull()?.endsWith(message) == true) return
        _activityLogs.value = (listOf(entry) + current).take(50)
    }

    fun getPeerName(peerId: String): String {
        val found = _discoveredPeers.value.find { it.peerId == peerId }?.peerName
        return if (!found.isNullOrBlank() && found != "MeshPeer" && !found.startsWith("peer_")) {
            found
        } else if (peerId.startsWith("direct_")) {
            "Nearby Device (${peerId.removePrefix("direct_").replace('_', '.')})"
        } else {
            "Nearby Device"
        }
    }

    fun updateDiscoveredPeers(peers: List<DiscoveredPeer>) {
        val myAuthorId = cryptoManager.authorId
        val filtered = peers.filter { it.peerId != myAuthorId }
        val oldSize = _discoveredPeers.value.size
        val deduplicated = filtered.distinctBy { it.peerId }
        _discoveredPeers.value = deduplicated
        if (deduplicated.size > oldSize && deduplicated.isNotEmpty()) {
            val peerName = deduplicated.lastOrNull()?.peerName ?: "Peer"
            if (peerName != "Nearby Device" && !peerName.startsWith("Direct IP") && !peerName.startsWith("peer_") && !peerName.startsWith("Host ADB")) {
                logActivity("Discovered $peerName nearby")
            }
        }
    }

    fun updatePeerNameAndId(oldIdOrHost: String, newPeerId: String, newPeerName: String) {
        val myAuthorId = cryptoManager.authorId
        if (newPeerId == myAuthorId) return

        val current = _discoveredPeers.value.toMutableList()
        val index = current.indexOfFirst {
            it.peerId == oldIdOrHost || it.peerId == newPeerId || it.hostAddress == oldIdOrHost
        }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(
                peerId = newPeerId,
                peerName = newPeerName,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        } else {
            current.add(
                DiscoveredPeer(
                    peerId = newPeerId,
                    peerName = newPeerName,
                    transportType = com.mysocial.mesh.protocol.TransportType.MDNS_WIFI,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )
        }
        val deduplicated = current.filter { it.peerId != myAuthorId }.distinctBy { it.peerId }
        _discoveredPeers.value = deduplicated
        if (newPeerName.isNotBlank() && newPeerName != "MeshPeer" && newPeerName != "Nearby Device" && !newPeerName.startsWith("Host ADB")) {
            logActivity("Connected with $newPeerName")
        }
    }

    /**
     * Observes the main social feed with selectable filter:
     * - openMeshFilter = true: All mesh traffic (carried by mules, direct, all hops)
     * - openMeshFilter = false: Followed authors + 2-hop trust graph
     */
    fun observeFeed(openMeshFilter: Boolean): Flow<List<PostWithAuthor>> {
        return if (openMeshFilter) {
            db.postViewDao().observeAllFeed(cryptoManager.authorId)
        } else {
            db.postViewDao().observeFollowedAndTwoHopFeed(cryptoManager.authorId)
        }
    }

    /**
     * Observes replies for a specific parent/root post hash.
     */
    fun observeReplies(parentHash: String): Flow<List<PostWithAuthor>> {
        return db.postViewDao().observeReplies(parentHash)
    }

    /**
     * Observes posts authored by a specific user (e.g. self profile timeline).
     */
    fun observePostsByAuthor(authorId: String): Flow<List<PostWithAuthor>> {
        return db.postViewDao().observePostsByAuthor(authorId, cryptoManager.authorId)
    }

    /**
     * Creates and signs a new local POST LogEntry (with optional media attachment), committing it to Room.
     */
    suspend fun createPost(
        text: String,
        tags: List<String>,
        media: com.mysocial.mesh.model.MediaAttachment? = null,
        isPublic: Boolean = true
    ): Result<LogEntry> {
        val payload = PostPayload(text = text.trim(), tags = tags, media = media, isPublic = isPublic).toJson()
        return syncEngine.createAndPublishLocalEntry(ContentType.POST, payload).also {
            if (it.isSuccess) {
                val suffix = if (media != null) " with photo" else ""
                val aud = if (isPublic) "public" else "friends-only"
                logActivity("Published new $aud post$suffix: \"${text.take(30)}${if (text.length > 30) "..." else ""}\"")
                onContentPublished?.invoke()
            }
        }
    }

    /**
     * Observes blob manifest status for a media attachment.
     */
    fun observeBlobManifest(blobHash: String): Flow<BlobManifestEntity?> {
        return db.blobDao().observeManifest(blobHash)
    }

    /**
     * Observes received chunk count for a media attachment.
     */
    fun observeBlobReceivedChunks(blobHash: String): Flow<Int> {
        return db.blobDao().observeReceivedChunksCount(blobHash)
    }

    /**
     * Prioritizes downloading a specific blob across active connections.
     */
    suspend fun prioritizeBlobDownload(blobHash: String) {
        syncEngine.prioritizeBlob(blobHash)
    }

    /**
     * Registers a locally processed completed blob into Room blob_manifests.
     */
    suspend fun registerCompletedBlob(media: com.mysocial.mesh.data.ProcessedMedia) {
        db.blobDao().upsertManifest(
            BlobManifestEntity(
                blobHash = media.blobHash,
                totalBytes = media.totalBytes,
                chunkCount = media.chunkCount,
                mimeType = media.mimeType,
                isComplete = true,
                localFilePath = media.localFile.absolutePath
            )
        )
    }

    suspend fun deletePost(entryHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = PostDeletePayload(targetHash = entryHash).toJson()
            val result = syncEngine.createAndPublishLocalEntry(ContentType.POST_DELETE, payload)
            if (result.isSuccess) {
                logActivity("Deleted post")
                onContentPublished?.invoke()
                true
            } else {
                db.postViewDao().deletePost(entryHash)
                logActivity("Deleted post locally")
                onContentPublished?.invoke()
                true
            }
        } catch (e: Exception) {
            db.postViewDao().deletePost(entryHash)
            onContentPublished?.invoke()
            false
        }
    }

    suspend fun editPost(entryHash: String, newText: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = PostEditPayload(targetHash = entryHash, newText = newText.trim()).toJson()
            val result = syncEngine.createAndPublishLocalEntry(ContentType.POST_EDIT, payload)
            if (result.isSuccess) {
                logActivity("Updated post content")
                onContentPublished?.invoke()
                true
            } else {
                db.postViewDao().updatePostText(entryHash, newText.trim())
                logActivity("Updated post content locally")
                onContentPublished?.invoke()
                true
            }
        } catch (e: Exception) {
            db.postViewDao().updatePostText(entryHash, newText.trim())
            onContentPublished?.invoke()
            false
        }
    }

    /**
     * Creates and signs a new local REPLY LogEntry, committing it to Room.
     */
    suspend fun createReply(text: String, rootHash: String, parentHash: String): Result<LogEntry> {
        val payload = ReplyPayload(
            text = text.trim(),
            rootHash = rootHash,
            parentHash = parentHash
        ).toJson()
        return syncEngine.createAndPublishLocalEntry(ContentType.REPLY, payload).also {
            if (it.isSuccess) {
                logActivity("Added comment to post")
                onContentPublished?.invoke()
            }
        }
    }

    /**
     * Adds an emoji reaction to a post, incrementing likeCount.
     */
    suspend fun reactToPost(targetHash: String, emoji: String = "❤️"): Result<LogEntry> {
        val payload = ReactionPayload(
            targetHash = targetHash,
            emoji = emoji
        ).toJson()
        return syncEngine.createAndPublishLocalEntry(ContentType.REACTION, payload).also {
            if (it.isSuccess) {
                logActivity("Reacted $emoji to post")
                onContentPublished?.invoke()
            }
        }
    }

    /**
     * Updates local profile (displayName, bio, avatarSeed) and publishes PROFILE LogEntry.
     */
    suspend fun updateProfile(displayName: String, bio: String, avatarSeed: String): Result<LogEntry> {
        val payload = ProfilePayload(
            displayName = displayName.trim(),
            bio = bio.trim(),
            avatarSeed = avatarSeed
        ).toJson()
        return syncEngine.createAndPublishLocalEntry(ContentType.PROFILE, payload).also {
            if (it.isSuccess) {
                logActivity("Updated profile name to \"$displayName\"")
                onContentPublished?.invoke()
            }
        }
    }

    /**
     * Follows or unfollows a target peer authorId.
     */
    suspend fun toggleFollow(targetAuthorId: String, following: Boolean): Result<LogEntry> {
        val payload = FollowPayload(
            targetAuthorId = targetAuthorId,
            following = following
        ).toJson()
        return syncEngine.createAndPublishLocalEntry(ContentType.FOLLOW, payload).also {
            if (it.isSuccess) {
                logActivity(if (following) "Followed peer ${targetAuthorId.take(12)}" else "Unfollowed peer")
                onContentPublished?.invoke()
            }
        }
    }

    /**
     * Checks if local user is following a given author.
     */
    suspend fun isFollowing(targetAuthorId: String): Boolean {
        return db.followDao().isFollowing(cryptoManager.authorId, targetAuthorId) ?: false
    }

    /**
     * Observes a profile by authorId.
     */
    fun observeProfile(authorId: String): Flow<ProfileViewEntity?> {
        return db.profileDao().observeProfile(authorId)
    }

    /**
     * Observes all profiles currently stored in the database.
     */
    fun observeAllProfiles(): Flow<List<ProfileViewEntity>> {
        return db.profileDao().observeAllProfiles()
    }

    suspend fun getProfile(authorId: String): ProfileViewEntity? {
        return db.profileDao().getProfile(authorId)
    }

    /**
     * Observes local user's own profile.
     */
    fun observeMyProfile(): Flow<ProfileViewEntity?> {
        return db.profileDao().observeProfile(cryptoManager.authorId)
    }

    /**
     * Observes database metrics for Identity / Stats tab.
     */
    fun observeDatabaseStats(): Flow<DatabaseStats> {
        return combine(
            db.logEntryDao().observeTotalEntriesCount(),
            db.logEntryDao().observeTotalAuthorsCount()
        ) { entries, authors ->
            DatabaseStats(
                totalEntries = entries,
                totalAuthors = authors,
                totalPosts = 0 // dynamically supplemented
            )
        }
    }

    suspend fun getTotalPostsCount(): Int = withContext(Dispatchers.IO) {
        db.postViewDao().getTotalPostsCount()
    }
}
