package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.ReactionPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.MemoryPeerConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
class Step2SyncEngineTest {

    private lateinit var dbA: MeshDatabase
    private lateinit var dbB: MeshDatabase

    private lateinit var cryptoA: CryptoManager
    private lateinit var cryptoB: CryptoManager

    private lateinit var engineA: GossipSyncEngine
    private lateinit var engineB: GossipSyncEngine

    private lateinit var appSettingsA: AppSettings

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbA = MeshDatabase.buildInMemory(context)
        dbB = MeshDatabase.buildInMemory(context)

        cryptoA = CryptoManager.createInMemory(seed = ByteArray(32) { 10 })
        cryptoB = CryptoManager.createInMemory(seed = ByteArray(32) { 20 })

        appSettingsA = AppSettings.getInstance(context)

        engineA = GossipSyncEngine(
            db = dbA,
            cryptoManager = cryptoA,
            myDisplayName = "Alice",
            openMeshMode = true,
            appSettings = appSettingsA
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
    fun testHopCountIncrementOnSync() = runBlocking {
        // Node A creates a local post (hopCount = 0)
        val postPayload = PostPayload(text = "Original post from Alice", tags = listOf("mesh")).toJson()
        val res = engineA.createAndPublishLocalEntry(ContentType.POST, postPayload)
        assertTrue(res.isSuccess)

        val localPostA = dbA.postViewDao().getPost(res.getOrThrow().entryHash)
        assertNotNull(localPostA)
        assertEquals(0, localPostA!!.hopCount)
        assertEquals("LOCAL", localPostA.transportSource)

        // Pair Node A and Node B
        val (connToB, connToA) = MemoryPeerConnection.createPaired(
            peerAId = cryptoA.authorId,
            peerAName = "Alice",
            peerBId = cryptoB.authorId,
            peerBName = "Bob"
        )

        val syncA = async { engineA.syncWithPeer(connToB) }
        val syncB = async { engineB.syncWithPeer(connToA) }

        syncA.await()
        syncB.await()

        // Verify Node B received post with hopCount = 1 (incremented by 1)
        val postOnB = dbB.postViewDao().getPost(res.getOrThrow().entryHash)
        assertNotNull(postOnB)
        assertEquals(1, postOnB!!.hopCount)
    }

    @Test
    fun testPollVotingAndDeduplication() = runBlocking {
        // Node A publishes a poll post
        val pollPayload = PostPayload(
            text = "What is the best mesh transport?",
            pollOptions = listOf("BLE 5.0", "Wi-Fi Direct / mDNS", "LoRa")
        ).toJson()
        val postRes = engineA.createAndPublishLocalEntry(ContentType.POST, pollPayload)
        assertTrue(postRes.isSuccess)
        val postHash = postRes.getOrThrow().entryHash

        // User A votes for option 0 (BLE 5.0)
        val vote1 = ReactionPayload(targetHash = postHash, emoji = "poll:0", pollOptionIndex = 0).toJson()
        val vote1Res = engineA.createAndPublishLocalEntry(ContentType.REACTION, vote1)
        assertTrue(vote1Res.isSuccess)

        val votesAfterFirst = dbA.reactionDao().observePollVotesForPost(postHash).first()
        assertEquals(1, votesAfterFirst.size)
        assertEquals(0, votesAfterFirst[0].pollOptionIndex)

        // User A changes vote to option 1 (Wi-Fi Direct)
        val vote2 = ReactionPayload(targetHash = postHash, emoji = "poll:1", pollOptionIndex = 1).toJson()
        val vote2Res = engineA.createAndPublishLocalEntry(ContentType.REACTION, vote2)
        assertTrue(vote2Res.isSuccess)

        val votesAfterSecond = dbA.reactionDao().observePollVotesForPost(postHash).first()
        assertEquals(1, votesAfterSecond.size) // No double voting!
        assertEquals(1, votesAfterSecond[0].pollOptionIndex)
    }

    @Test
    fun testTtlPostPruning() = runBlocking {
        val now = System.currentTimeMillis()
        // Create an expired post (expiresAt 1000ms in the past)
        val expiredPost = PostPayload(
            text = "Ephemeral notice from 1h ago",
            expiresAt = now - 1000L
        ).toJson()
        val expRes = engineA.createAndPublishLocalEntry(ContentType.POST, expiredPost)
        assertTrue(expRes.isSuccess)

        // Create a non-expired post (expiresAt 1h in the future)
        val activePost = PostPayload(
            text = "Active notice",
            expiresAt = now + 3600_000L
        ).toJson()
        val actRes = engineA.createAndPublishLocalEntry(ContentType.POST, activePost)
        assertTrue(actRes.isSuccess)

        // Run pruning worker
        engineA.pruneExpiredPostsAndBlobs()

        // Expired post should be deleted from posts_view
        val expiredInDb = dbA.postViewDao().getPost(expRes.getOrThrow().entryHash)
        assertEquals(null, expiredInDb)

        // Active post should still exist
        val activeInDb = dbA.postViewDao().getPost(actRes.getOrThrow().entryHash)
        assertNotNull(activeInDb)

        // Ledger log_entries MUST retain the expired entry to preserve sequence continuity!
        val ledgerEntry = dbA.logEntryDao().getEntryByHash(expRes.getOrThrow().entryHash)
        assertNotNull(ledgerEntry)
    }
}

