package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.BlobManifestEntity
import com.mysocial.mesh.model.FollowViewEntity
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshDataMulePruningTest {

    private lateinit var context: Context
    private lateinit var db: MeshDatabase
    private lateinit var cryptoAlice: CryptoManager
    private lateinit var cryptoBob: CryptoManager
    private lateinit var cryptoStranger: CryptoManager
    private lateinit var appSettings: AppSettings
    private lateinit var syncEngine: GossipSyncEngine
    private lateinit var blobsDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = MeshDatabase.buildInMemory(context)
        cryptoAlice = CryptoManager.createInMemory(seed = ByteArray(32) { 1 })
        cryptoBob = CryptoManager.createInMemory(seed = ByteArray(32) { 2 })
        cryptoStranger = CryptoManager.createInMemory(seed = ByteArray(32) { 3 })
        appSettings = AppSettings.getInstance(context)

        blobsDir = MediaProcessor.getBlobsDirectory(context)
        blobsDir.mkdirs()

        syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoAlice,
            myDisplayName = "Alice",
            openMeshMode = true,
            appSettings = appSettings,
            context = context
        )
    }

    @After
    fun tearDown() {
        db.close()
        blobsDir.deleteRecursively()
    }

    @Test
    fun testStrangerMediaPrunedWhenExceedingCapacity() = runBlocking {
        // Alice follows Bob (friend). Stranger is NOT followed.
        db.followDao().upsert(
            FollowViewEntity(
                followerId = cryptoAlice.authorId,
                followeeId = cryptoBob.authorId,
                isFollowing = true,
                updatedAtSeq = 1
            )
        )

        val friendBlobHash = "friend_blob_hash_1"
        val strangerBlobHash = "stranger_blob_hash_2"

        // Create friend blob file (1MB)
        val friendFile = File(blobsDir, "$friendBlobHash.blob")
        friendFile.writeBytes(ByteArray(1024 * 1024) { 1 })

        // Create stranger blob file (1MB)
        val strangerFile = File(blobsDir, "$strangerBlobHash.blob")
        strangerFile.writeBytes(ByteArray(1024 * 1024) { 2 })

        // Associate friend post
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "post_friend",
                authorId = cryptoBob.authorId,
                text = "Friend's photo",
                timestamp = System.currentTimeMillis(),
                replyToHash = null,
                rootHash = null,
                mediaBlobHash = friendBlobHash
            )
        )
        db.blobDao().upsertManifest(
            BlobManifestEntity(
                blobHash = friendBlobHash,
                mimeType = "image/webp",
                totalBytes = friendFile.length(),
                chunkCount = 1,
                isComplete = true
            )
        )

        // Associate stranger post
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "post_stranger",
                authorId = cryptoStranger.authorId,
                text = "Stranger's photo",
                timestamp = System.currentTimeMillis() - 1000,
                replyToHash = null,
                rootHash = null,
                mediaBlobHash = strangerBlobHash
            )
        )
        db.blobDao().upsertManifest(
            BlobManifestEntity(
                blobHash = strangerBlobHash,
                mimeType = "image/webp",
                totalBytes = strangerFile.length(),
                chunkCount = 1,
                isComplete = true
            )
        )

        // Set max cache to 1 MB (so total 2MB will trigger pruning)
        appSettings.setMaxStrangerMediaCacheMb(1)

        syncEngine.pruneStrangerMediaIfOverCapacity()

        // Friend's blob MUST be preserved
        assertTrue("Friend's blob file must be protected and preserved", friendFile.exists())
        val friendManifest = db.blobDao().getManifest(friendBlobHash)
        assertTrue(friendManifest?.isComplete == true)

        // Stranger's blob MUST be pruned
        assertFalse("Stranger's blob file must be deleted to free cache", strangerFile.exists())
        val strangerManifest = db.blobDao().getManifest(strangerBlobHash)
        assertFalse("Stranger's manifest must be marked incomplete", strangerManifest?.isComplete == true)
    }

    @Test
    fun testExpiredPostsAndBlobsPurged() = runBlocking {
        val now = System.currentTimeMillis()
        val expiredBlobHash = "expired_blob_hash"

        val expiredFile = File(blobsDir, "$expiredBlobHash.blob")
        expiredFile.writeBytes(ByteArray(5000) { 9 })

        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "post_expired_with_blob",
                authorId = cryptoAlice.authorId,
                text = "Disappearing photo",
                timestamp = now - 5000,
                replyToHash = null,
                rootHash = null,
                mediaBlobHash = expiredBlobHash,
                expiresAt = now - 100 // Expired
            )
        )
        db.blobDao().upsertManifest(
            BlobManifestEntity(
                blobHash = expiredBlobHash,
                mimeType = "image/webp",
                totalBytes = 5000,
                chunkCount = 1,
                isComplete = true
            )
        )

        syncEngine.pruneExpiredPostsAndBlobs()

        // File must be deleted
        assertFalse("Expired media file must be deleted", expiredFile.exists())
        // Manifest must be deleted
        assertNull("Expired manifest must be deleted", db.blobDao().getManifest(expiredBlobHash))
        // Post must be deleted
        assertNull("Expired post must be deleted from posts_view", db.postViewDao().getPost("post_expired_with_blob"))
    }
}
