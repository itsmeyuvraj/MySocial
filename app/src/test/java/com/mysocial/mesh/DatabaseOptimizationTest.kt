package com.mysocial.mesh

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.model.ReactionEntity
import com.mysocial.mesh.model.ReactionPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseOptimizationTest {

    private lateinit var db: MeshDatabase
    private lateinit var cryptoAlice: CryptoManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        cryptoAlice = CryptoManager.createInMemory(seed = ByteArray(32) { 7 })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testObserveUserLikedPostHashesLifting() = runBlocking {
        val authorId = cryptoAlice.authorId

        // Initial state: no liked posts
        val initialLiked = db.reactionDao().observeUserLikedPostHashes(authorId).first()
        assertTrue("Initially user has no liked posts", initialLiked.isEmpty())

        // Insert post reaction (like)
        val reactionLike = ReactionEntity(
            targetHash = "hash_post_1",
            authorId = authorId,
            emoji = "❤️",
            timestamp = System.currentTimeMillis(),
            pollOptionIndex = null
        )
        db.reactionDao().insertReaction(reactionLike)

        // Insert poll reaction (vote, pollOptionIndex != null)
        val reactionPollVote = ReactionEntity(
            targetHash = "hash_poll_1",
            authorId = authorId,
            emoji = "🗳️",
            timestamp = System.currentTimeMillis(),
            pollOptionIndex = 2
        )
        db.reactionDao().insertReaction(reactionPollVote)

        // Observe liked hashes: must contain hash_post_1 and exclude hash_poll_1
        val updatedLiked = db.reactionDao().observeUserLikedPostHashes(authorId).first()
        assertEquals(1, updatedLiked.size)
        assertEquals("hash_post_1", updatedLiked[0])

        // Add a second like
        val reactionLike2 = ReactionEntity(
            targetHash = "hash_post_2",
            authorId = authorId,
            emoji = "❤️",
            timestamp = System.currentTimeMillis() + 10,
            pollOptionIndex = null
        )
        db.reactionDao().insertReaction(reactionLike2)

        val multipleLiked = db.reactionDao().observeUserLikedPostHashes(authorId).first().toSet()
        assertEquals(setOf("hash_post_1", "hash_post_2"), multipleLiked)
    }

    @Test
    fun testMigration6to7SqlExecution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDb = MeshDatabase.buildInMemory(context)
        val sqlDb = testDb.openHelper.writableDatabase

        // Execute MIGRATION_6_7 statements directly to verify syntax on SQLite engine
        MeshDatabase.MIGRATION_6_7.migrate(sqlDb)

        // Verify that indices exist in sqlite_master
        val cursor = sqlDb.query("SELECT name FROM sqlite_master WHERE type = 'index'")
        val indexNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            indexNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue(indexNames.contains("index_posts_view_replyToHash_isSos_timestamp"))
        assertTrue(indexNames.contains("index_posts_view_authorId_replyToHash_timestamp"))
        assertTrue(indexNames.contains("index_direct_messages_conversationPeerId_timestamp"))
        assertTrue(indexNames.contains("index_direct_messages_isRead_isOutgoing"))
        assertTrue(indexNames.contains("index_reactions_view_authorId_pollOptionIndex"))
    }
}
