package com.mysocial.mesh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.ValidationResult
import com.mysocial.mesh.model.BlockedAuthorEntity
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AdaptiveCongestionAndBlocklistTest {

    private lateinit var context: Context
    private lateinit var db: MeshDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = MeshDatabase.buildInMemory(context)

        cryptoManager = CryptoManager.createInMemory(seed = ByteArray(32) { 77 })
        syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoManager,
            myDisplayName = "TestNode",
            context = context
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAdaptiveScopingHopLimits() {
        // Low density: <= 3 peers
        syncEngine.updatePeerDensity(2)
        assertEquals(7, syncEngine.getDynamicMaxHops(isSos = false))
        assertEquals(50, syncEngine.getDynamicBatchSize())

        // Medium density: 4..12 peers
        syncEngine.updatePeerDensity(8)
        assertEquals(4, syncEngine.getDynamicMaxHops(isSos = false))
        assertEquals(30, syncEngine.getDynamicBatchSize())

        // High-density crowd mode: > 12 peers
        syncEngine.updatePeerDensity(25)
        assertEquals(2, syncEngine.getDynamicMaxHops(isSos = false))
        assertEquals(15, syncEngine.getDynamicBatchSize())

        // Emergency SOS alerts must ALWAYS bypass scoping limits
        assertEquals(15, syncEngine.getDynamicMaxHops(isSos = true))
    }

    @Test
    fun testCongestionDampeningExecution() = runBlocking {
        // SOS alert has zero delay
        val startSos = System.currentTimeMillis()
        syncEngine.applyCongestionDampening(isSos = true)
        val elapsedSos = System.currentTimeMillis() - startSos
        assertTrue(elapsedSos < 50)

        // Low density has zero delay
        syncEngine.updatePeerDensity(2)
        val startLow = System.currentTimeMillis()
        syncEngine.applyCongestionDampening(isSos = false)
        val elapsedLow = System.currentTimeMillis() - startLow
        assertTrue(elapsedLow < 50)
    }

    @Test
    fun testBlockAuthorAndDaoOperations() = runBlocking {
        val spammerId = "spammer_ed25519_key_999"
        assertEquals(0, db.blockedAuthorDao().isAuthorBlocked(spammerId))

        // Block author
        db.blockedAuthorDao().insert(
            BlockedAuthorEntity(
                authorId = spammerId,
                reason = "Flood spam",
                isSybilEvicted = false
            )
        )
        assertEquals(1, db.blockedAuthorDao().isAuthorBlocked(spammerId))

        val list = db.blockedAuthorDao().observeBlockedAuthors().first()
        assertEquals(1, list.size)
        assertEquals(spammerId, list[0].authorId)
        assertEquals("Flood spam", list[0].reason)

        // Unblock author
        db.blockedAuthorDao().unblock(spammerId)
        assertEquals(0, db.blockedAuthorDao().isAuthorBlocked(spammerId))
    }

    @Test
    fun testLogValidatorRejectsBlockedAuthorPackets() = runBlocking {
        val spammerCrypto = CryptoManager.createInMemory(seed = ByteArray(32) { 99 })
        val spammerId = spammerCrypto.authorId

        // Block the author
        db.blockedAuthorDao().insert(
            BlockedAuthorEntity(
                authorId = spammerId,
                reason = "Sybil evicted",
                isSybilEvicted = true
            )
        )

        // Create log entry from blocked author
        val payload = PostPayload("Spamming open mesh airwaves").toJson()
        val sig = spammerCrypto.signEntry(1L, null, 1000L, ContentType.POST.name, payload)
        val entryHash = CryptoManager.computeEntryHash(spammerId, 1L, null, 1000L, ContentType.POST.name, payload, sig)

        val entry = LogEntry(
            authorId = spammerId,
            sequence = 1L,
            previousHash = null,
            timestamp = 1000L,
            contentType = ContentType.POST,
            contentJson = payload,
            signature = sig,
            entryHash = entryHash
        )

        val result = db.insertAndProject(entry)
        assertTrue("Blocked author entry must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("blocked or Sybil-evicted") == true)
    }

    @Test
    fun testDeltaRequestExcludesBlockedAuthors() = runBlocking {
        val goodPeer = "author_good_111"
        val badPeer = "author_bad_222"

        db.blockedAuthorDao().insert(
            BlockedAuthorEntity(authorId = badPeer, reason = "Blocked by 3 friends", isSybilEvicted = true)
        )

        val localClock = mapOf(goodPeer to 2L, badPeer to 1L)
        val remoteClock = mapOf(goodPeer to 5L, badPeer to 10L)

        val deltas = syncEngine.computeNeededDeltas(localClock, remoteClock)

        assertTrue(deltas.containsKey(goodPeer))
        assertEquals(3L, deltas[goodPeer])
        assertFalse("Blocked author must not be requested in delta vector", deltas.containsKey(badPeer))
    }

    @Test
    fun testMigration8to9SqlExecution() {
        val sqlDb = db.openHelper.writableDatabase
        MeshDatabase.MIGRATION_8_9.migrate(sqlDb)

        val cursor = sqlDb.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'blocked_authors'")
        assertTrue("blocked_authors table must exist after migration", cursor.moveToFirst())
        cursor.close()

        val indexCursor = sqlDb.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_blocked_authors_authorId'")
        assertTrue("index on blocked_authors must exist after migration", indexCursor.moveToFirst())
        indexCursor.close()
    }
}
