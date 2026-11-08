package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
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
}
