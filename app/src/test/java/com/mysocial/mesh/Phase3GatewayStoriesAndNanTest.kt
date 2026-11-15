package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.ProofOfWorkEngine
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.gateway.InternetGatewayUplinkManager
import com.mysocial.mesh.ios.IosCompanionBridge
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.GatewayDeliveryReceiptPayload
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.model.StoryPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.story.StoryManager
import com.mysocial.mesh.transport.WifiAwareTransport
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase3GatewayStoriesAndNanTest {

    private lateinit var context: Context
    private lateinit var db: MeshDatabase
    private lateinit var cryptoA: CryptoManager
    private lateinit var engineA: GossipSyncEngine
    private lateinit var storyManager: StoryManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = MeshDatabase.buildInMemory(context)
        cryptoA = CryptoManager.createInMemory(seed = ByteArray(32) { 77 })
        db.cryptoManager = cryptoA

        engineA = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoA,
            myDisplayName = "Alice",
            context = context
        )
        storyManager = StoryManager(db, cryptoA, engineA)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testStoryPublishAndActiveObservation() = runBlocking {
        val result = storyManager.publishStory(
            text = "Beautiful sunset over the ridge! Mesh is strong 🌅",
            authorDisplayName = "Alice"
        )
        assertTrue(result.isSuccess)
        val story = result.getOrNull()
        assertNotNull(story)
        assertEquals("Alice", story?.authorDisplayName)
        assertTrue(story?.expiresAt ?: 0L > System.currentTimeMillis())

        val active = storyManager.observeActiveStories().first()
        assertEquals(1, active.size)
        assertEquals(story?.storyId, active[0].storyId)

        // Mark viewed
        storyManager.markStoryViewed(story!!.storyId)
        val viewedStories = storyManager.observeStoriesForAuthor(cryptoA.authorId).first()
        assertTrue(viewedStories[0].isViewed)
    }

    @Test
    fun testStoryExpirationPruning() = runBlocking {
        // Insert a story that expired in the past
        val expiredPayload = StoryPayload(
            storyId = "expired_story_1",
            authorId = cryptoA.authorId,
            authorDisplayName = "Alice",
            text = "Old story from yesterday",
            createdAt = System.currentTimeMillis() - 48 * 3600 * 1000L,
            expiresAt = System.currentTimeMillis() - 24 * 3600 * 1000L
        )
        db.storyDao().insert(
            com.mysocial.mesh.model.StoryEntity(
                storyId = expiredPayload.storyId,
                authorId = expiredPayload.authorId,
                authorDisplayName = expiredPayload.authorDisplayName,
                text = expiredPayload.text,
                createdAt = expiredPayload.createdAt,
                expiresAt = expiredPayload.expiresAt
            )
        )

        val beforePrune = db.storyDao().observeActiveStories().first()
        assertEquals(0, beforePrune.size) // observeActiveStories filters out expiresAt <= now

        val pruned = storyManager.pruneExpiredStories()
        assertEquals(1, pruned)
    }

    @Test
    fun testInternetGatewayUplinkManagerRelaysSos() = runBlocking {
        // 1. Insert an emergency SOS post without gateway relay
        val sosEntryHash = "sos_test_hash_001"
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = sosEntryHash,
                authorId = cryptoA.authorId,
                text = "Flash flood warning! Need evacuation at sector 4",
                timestamp = System.currentTimeMillis(),
                replyToHash = null,
                rootHash = null,
                isSos = true,
                gatewayRelayedAt = null
            )
        )

        val pendingBefore = db.postViewDao().getPendingGatewaySosPosts()
        assertEquals(1, pendingBefore.size)
        assertEquals(sosEntryHash, pendingBefore[0].entryHash)

        // 2. Uplink manager runs relay
        val uplinkManager = InternetGatewayUplinkManager(context, db, cryptoA, engineA)
        val relayed = uplinkManager.relayPendingSosAlerts()
        assertEquals(1, relayed)

        // 3. Post should now be marked as relayed
        val postAfter = db.postViewDao().getPost(sosEntryHash)
        assertNotNull(postAfter?.gatewayRelayedAt)

        val pendingAfter = db.postViewDao().getPendingGatewaySosPosts()
        assertEquals(0, pendingAfter.size)
    }

    @Test
    fun testIosL2capFramePackingAndCrc() {
        val payload = "Hello iOS CoreBluetooth Peer".toByteArray(Charsets.UTF_8)
        val encoded = IosCompanionBridge.encodeFrame(IosCompanionBridge.TYPE_LOG_BATCH, payload)

        assertTrue(encoded.size >= 12 + payload.size)

        // Decode valid frame
        val decoded = IosCompanionBridge.decodeFrame(encoded)
        assertNotNull(decoded)
        assertEquals(IosCompanionBridge.TYPE_LOG_BATCH, decoded?.frameType)
        assertEquals(String(payload, Charsets.UTF_8), String(decoded?.payload ?: ByteArray(0), Charsets.UTF_8))

        // Tamper with payload byte -> CRC must fail
        val tampered = encoded.copyOf()
        tampered[14] = (tampered[14] + 1).toByte()
        val rejected = IosCompanionBridge.decodeFrame(tampered)
        assertNull("Tampered L2CAP frame must be rejected", rejected)
    }

    @Test
    fun testWifiAwareTransportCapabilities() {
        val transport = WifiAwareTransport(context, cryptoA.authorId, "Alice")
        // Transport initializes without crashing and exposes reactive peer state flow
        assertNotNull(transport.discoveredPeers)
        assertEquals(0, transport.discoveredPeers.value.size)
        // Calling start/stop in test environment does not throw
        transport.start()
        transport.stop()
    }

    @Test
    fun testPeerStoryProjectionAndBlobManifestDiscovery() = runBlocking {
        val cryptoB = CryptoManager.createInMemory(seed = ByteArray(32) { 88 })
        val discoveredBlobs = mutableListOf<String>()
        db.onBlobDiscovered = { hash -> discoveredBlobs.add(hash) }

        // 1. Bob has a profile
        val tsProfile = 1_000_000L
        val profilePayload = com.mysocial.mesh.model.ProfilePayload(displayName = "Bob The Builder", bio = "Builder", avatarSeed = "bob123")
        val sigProfile = cryptoB.signEntry(1, null, tsProfile, "PROFILE", profilePayload.toJson())
        val hashProfile = CryptoManager.computeEntryHash(cryptoB.authorId, 1, null, tsProfile, "PROFILE", profilePayload.toJson(), sigProfile)
        val profileEntry = com.mysocial.mesh.model.LogEntry(
            authorId = cryptoB.authorId,
            sequence = 1,
            previousHash = null,
            timestamp = tsProfile,
            contentType = com.mysocial.mesh.model.ContentType.PROFILE,
            contentJson = profilePayload.toJson(),
            signature = sigProfile,
            entryHash = hashProfile,
            powNonce = ProofOfWorkEngine.mineNonce(cryptoB.authorId, 1, null, tsProfile, "PROFILE", profilePayload.toJson(), sigProfile)
        )
        db.insertAndProject(profileEntry)

        // 2. Bob publishes a photo story
        val tsStory = 1_001_000L
        val storyPayload = StoryPayload(
            storyId = "bob_story_100",
            authorId = cryptoB.authorId,
            authorDisplayName = "Me", // Even if Bob incorrectly had "Me" in his payload
            text = "Check out the bridge construction!",
            mediaBlobHash = "bob_bridge_blob_hash",
            mediaTotalBytes = 49152L,
            mediaChunkCount = 3,
            mediaMimeType = "image/webp"
        )
        val sigStory = cryptoB.signEntry(2, hashProfile, tsStory, "STORY", storyPayload.toJson())
        val hashStory = CryptoManager.computeEntryHash(cryptoB.authorId, 2, hashProfile, tsStory, "STORY", storyPayload.toJson(), sigStory)
        val storyEntry = com.mysocial.mesh.model.LogEntry(
            authorId = cryptoB.authorId,
            sequence = 2,
            previousHash = hashProfile,
            timestamp = tsStory,
            contentType = com.mysocial.mesh.model.ContentType.STORY,
            contentJson = storyPayload.toJson(),
            signature = sigStory,
            entryHash = hashStory,
            powNonce = ProofOfWorkEngine.mineNonce(cryptoB.authorId, 2, hashProfile, tsStory, "STORY", storyPayload.toJson(), sigStory)
        )

        // 3. Project Bob's story on Alice's device
        val result = db.insertAndProject(storyEntry)
        assertTrue("Bob's story must be accepted and projected", result.isSuccess)

        // 4. Verify story entity in Room stories_view
        val projectedStory = db.storyDao().getStoryById("bob_story_100")
        assertNotNull(projectedStory)
        assertEquals("Bob The Builder", projectedStory?.authorDisplayName) // Resolved from Bob's profile, NOT "Me"
        assertEquals(cryptoB.authorId, projectedStory?.authorId)
        assertFalse("Peer story must not be marked viewed initially", projectedStory?.isViewed ?: true)

        // 5. Verify Blob Manifest is auto-registered and download requested
        val manifest = db.blobDao().getManifest("bob_bridge_blob_hash")
        assertNotNull("Blob manifest must be created for photo story", manifest)
        assertEquals(3, manifest?.chunkCount)
        assertEquals(49152L, manifest?.totalBytes)
        assertEquals("image/webp", manifest?.mimeType)
        assertFalse(manifest?.isComplete ?: true)
        assertTrue("onBlobDiscovered must be triggered", discoveredBlobs.contains("bob_bridge_blob_hash"))
    }

    @Test
    fun testStoryProfileUpdateReflectsOnActiveStories() = runBlocking {
        val cryptoC = CryptoManager.createInMemory(seed = ByteArray(32) { 99 })

        // 1. Charlie posts a story before setting a profile
        val ts1 = 2_000_000L
        val storyPayload = StoryPayload(
            storyId = "charlie_story_1",
            authorId = cryptoC.authorId,
            authorDisplayName = "Charlie",
            text = "Hello mesh!"
        )
        val sigStory = cryptoC.signEntry(1, null, ts1, "STORY", storyPayload.toJson())
        val hashStory = CryptoManager.computeEntryHash(cryptoC.authorId, 1, null, ts1, "STORY", storyPayload.toJson(), sigStory)
        val storyEntry = com.mysocial.mesh.model.LogEntry(
            authorId = cryptoC.authorId,
            sequence = 1,
            previousHash = null,
            timestamp = ts1,
            contentType = com.mysocial.mesh.model.ContentType.STORY,
            contentJson = storyPayload.toJson(),
            signature = sigStory,
            entryHash = hashStory,
            powNonce = ProofOfWorkEngine.mineNonce(cryptoC.authorId, 1, null, ts1, "STORY", storyPayload.toJson(), sigStory)
        )
        val res1 = db.insertAndProject(storyEntry)
        assertTrue("Charlie's story must be inserted", res1.isSuccess)
        assertEquals("Charlie", db.storyDao().getStoryById("charlie_story_1")?.authorDisplayName)

        // 2. Charlie later updates profile to "Charles Darwin"
        val ts2 = 2_001_000L
        val profilePayload = com.mysocial.mesh.model.ProfilePayload(displayName = "Charles Darwin", bio = "Naturalist", avatarSeed = "charlie123")
        val sigProfile = cryptoC.signEntry(2, hashStory, ts2, "PROFILE", profilePayload.toJson())
        val hashProfile = CryptoManager.computeEntryHash(cryptoC.authorId, 2, hashStory, ts2, "PROFILE", profilePayload.toJson(), sigProfile)
        val profileEntry = com.mysocial.mesh.model.LogEntry(
            authorId = cryptoC.authorId,
            sequence = 2,
            previousHash = hashStory,
            timestamp = ts2,
            contentType = com.mysocial.mesh.model.ContentType.PROFILE,
            contentJson = profilePayload.toJson(),
            signature = sigProfile,
            entryHash = hashProfile,
            powNonce = ProofOfWorkEngine.mineNonce(cryptoC.authorId, 2, hashStory, ts2, "PROFILE", profilePayload.toJson(), sigProfile)
        )
        val res2 = db.insertAndProject(profileEntry)
        assertTrue("Charlie's profile update must be inserted", res2.isSuccess)

        // 3. Active story must reflect updated display name automatically
        assertEquals("Charles Darwin", db.storyDao().getStoryById("charlie_story_1")?.authorDisplayName)
    }

    @Test
    fun testStoryPayloadCanonicalJsonSerialization() {
        val payload = StoryPayload(
            storyId = "s_123",
            authorId = "@alice_key",
            authorDisplayName = "Alice",
            text = "Testing canonical json serialization",
            mediaBlobHash = null,
            mediaTotalBytes = null,
            mediaChunkCount = null
        )
        val json = payload.toJson()
        val decoded = StoryPayload.fromJson(json)
        assertEquals(payload.storyId, decoded.storyId)
        assertEquals(payload.authorId, decoded.authorId)
        assertEquals(payload.authorDisplayName, decoded.authorDisplayName)
        assertEquals(payload.text, decoded.text)
        assertNull(decoded.mediaBlobHash)
        assertNull(decoded.mediaTotalBytes)
        assertNull(decoded.mediaChunkCount)
    }
}
