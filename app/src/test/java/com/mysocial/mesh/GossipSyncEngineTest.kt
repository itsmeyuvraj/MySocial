package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.ProfilePayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.MemoryPeerConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GossipSyncEngineTest {

    private lateinit var dbA: MeshDatabase
    private lateinit var dbB: MeshDatabase

    private lateinit var cryptoA: CryptoManager
    private lateinit var cryptoB: CryptoManager

    private lateinit var engineA: GossipSyncEngine
    private lateinit var engineB: GossipSyncEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbA = MeshDatabase.buildInMemory(context)
        dbB = MeshDatabase.buildInMemory(context)

        cryptoA = CryptoManager.createInMemory(seed = ByteArray(32) { 1 })
        cryptoB = CryptoManager.createInMemory(seed = ByteArray(32) { 2 })

        engineA = GossipSyncEngine(
            db = dbA,
            cryptoManager = cryptoA,
            myDisplayName = "Alice",
            openMeshMode = true
        )

        engineB = GossipSyncEngine(
            db = dbB,
            cryptoManager = cryptoB,
            myDisplayName = "Bob",
            openMeshMode = true
        )
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    @Test
    fun testTwoNodesGossipSync() = runBlocking {
        // 1. Node A publishes a Profile and 2 Posts
        val profilePayload = ProfilePayload(
            displayName = "Alice",
            bio = "Decentralized mesh explorer",
            avatarSeed = "seed_alice"
        ).toJson()
        val resProfile = engineA.createAndPublishLocalEntry(ContentType.PROFILE, profilePayload)
        assertTrue(resProfile.isSuccess)

        val post1 = PostPayload(text = "Hello P2P Mesh!", tags = listOf("mesh", "offline")).toJson()
        val resPost1 = engineA.createAndPublishLocalEntry(ContentType.POST, post1)
        assertTrue(resPost1.isSuccess)

        val post2 = PostPayload(text = "Second post from Alice", tags = listOf("android")).toJson()
        val resPost2 = engineA.createAndPublishLocalEntry(ContentType.POST, post2)
        assertTrue(resPost2.isSuccess)

        // Verify Node A has 3 entries
        assertEquals(3, dbA.logEntryDao().getTotalEntriesCount())
        assertEquals(0, dbB.logEntryDao().getTotalEntriesCount())

        // 2. Setup Duplex in-memory peer connection between Node A and Node B
        val (connToB, connToA) = MemoryPeerConnection.createPaired(
            peerAId = cryptoA.authorId,
            peerAName = "Alice",
            peerBId = cryptoB.authorId,
            peerBName = "Bob"
        )

        // 3. Concurrently run the 3-step gossip handshake on both ends
        val syncAJob = async { engineA.syncWithPeer(connToB) }
        val syncBJob = async { engineB.syncWithPeer(connToA) }

        val resultA = syncAJob.await()
        val resultB = syncBJob.await()

        assertTrue(resultA.isSuccess)
        assertTrue(resultB.isSuccess)

        // Node B should have received 3 entries from Node A
        assertEquals(3, resultB.getOrNull())

        // 4. Verify Node B database now contains Alice's projected profile and posts
        val profileInB = dbB.profileDao().getProfile(cryptoA.authorId)
        assertNotNull(profileInB)
        assertEquals("Alice", profileInB?.displayName)
        assertEquals("Decentralized mesh explorer", profileInB?.bio)

        val postsInB = dbB.postViewDao().getTotalPostsCount()
        assertEquals(2, postsInB)

        val syncStateInB = dbB.peerSyncStateDao().getState(cryptoA.authorId)
        assertNotNull(syncStateInB)
        assertEquals(3L, syncStateInB?.highestSequence)

        // 5. Run a second sync without any new posts -> should exchange 0 entries
        val (connToB2, connToA2) = MemoryPeerConnection.createPaired(
            peerAId = cryptoA.authorId,
            peerAName = "Alice",
            peerBId = cryptoB.authorId,
            peerBName = "Bob"
        )
        val syncA2 = async { engineA.syncWithPeer(connToB2) }
        val syncB2 = async { engineB.syncWithPeer(connToA2) }

        assertEquals(0, syncA2.await().getOrNull())
        assertEquals(0, syncB2.await().getOrNull())
    }
}
