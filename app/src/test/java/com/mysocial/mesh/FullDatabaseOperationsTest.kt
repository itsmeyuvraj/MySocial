package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.model.FollowViewEntity
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.model.ReactionEntity
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
@Config(sdk = [34])
class FullDatabaseOperationsTest {

    private lateinit var db: MeshDatabase
    private lateinit var cryptoAlice: CryptoManager
    private lateinit var cryptoBob: CryptoManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        cryptoAlice = CryptoManager.createInMemory(seed = ByteArray(32) { 10 })
        cryptoBob = CryptoManager.createInMemory(seed = ByteArray(32) { 20 })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSosPostsAlwaysPrecedeStandardPostsRegardlessOfTimestamp() = runBlocking {
        val now = System.currentTimeMillis()

        // Newer standard post
        val standardPost = PostViewEntity(
            entryHash = "hash_standard",
            authorId = cryptoAlice.authorId,
            text = "Regular update",
            timestamp = now + 5000,
            replyToHash = null,
            rootHash = null,
            isSos = false
        )
        // Older SOS emergency broadcast
        val sosPost = PostViewEntity(
            entryHash = "hash_sos",
            authorId = cryptoBob.authorId,
            text = "Emergency SOS flood warning",
            timestamp = now,
            replyToHash = null,
            rootHash = null,
            isSos = true
        )

        db.postViewDao().upsert(standardPost)
        db.postViewDao().upsert(sosPost)

        val feed = db.postViewDao().observeAllFeed(cryptoAlice.authorId, now).first()
        assertEquals(2, feed.size)
        // SOS post MUST be first despite having an older timestamp
        assertEquals("hash_sos", feed[0].post.entryHash)
        assertTrue(feed[0].post.isSos)
        assertEquals("hash_standard", feed[1].post.entryHash)
        assertFalse(feed[1].post.isSos)
    }

    @Test
    fun testExpiredPostsAutoPruning() = runBlocking {
        val now = System.currentTimeMillis()

        val expiredPost = PostViewEntity(
            entryHash = "hash_expired",
            authorId = cryptoAlice.authorId,
            text = "Ephemeral 24h post",
            timestamp = now - 5000,
            replyToHash = null,
            rootHash = null,
            expiresAt = now - 1000 // Expired 1 second ago
        )
        val validPost = PostViewEntity(
            entryHash = "hash_valid",
            authorId = cryptoAlice.authorId,
            text = "Persistent post",
            timestamp = now,
            replyToHash = null,
            rootHash = null,
            expiresAt = now + 60000 // Valid for 1 more minute
        )

        db.postViewDao().upsert(expiredPost)
        db.postViewDao().upsert(validPost)

        // Feed query should only return valid post
        val feed = db.postViewDao().observeAllFeed(cryptoAlice.authorId, now).first()
        assertEquals(1, feed.size)
        assertEquals("hash_valid", feed[0].post.entryHash)

        // Delete expired posts in database
        val deletedCount = db.postViewDao().deleteExpiredPosts(now)
        assertEquals(1, deletedCount)

        assertNull(db.postViewDao().getPost("hash_expired"))
        assertNotNull(db.postViewDao().getPost("hash_valid"))
    }

    @Test
    fun testAtomicLikeAndReplyCounters() = runBlocking {
        val post = PostViewEntity(
            entryHash = "hash_counter_test",
            authorId = cryptoAlice.authorId,
            text = "Counting likes",
            timestamp = System.currentTimeMillis(),
            replyToHash = null,
            rootHash = null,
            likeCount = 0,
            replyCount = 0
        )
        db.postViewDao().upsert(post)

        db.postViewDao().incrementLikeCount("hash_counter_test")
        db.postViewDao().incrementLikeCount("hash_counter_test")
        db.postViewDao().incrementReplyCount("hash_counter_test")

        val updated = db.postViewDao().getPost("hash_counter_test")
        assertNotNull(updated)
        assertEquals(2, updated!!.likeCount)
        assertEquals(1, updated.replyCount)
    }

    @Test
    fun testPostTextUpdateAndCascadeDelete() = runBlocking {
        val post = PostViewEntity(
            entryHash = "hash_root",
            authorId = cryptoAlice.authorId,
            text = "Original draft",
            timestamp = System.currentTimeMillis(),
            replyToHash = null,
            rootHash = null
        )
        val reply = PostViewEntity(
            entryHash = "hash_reply_1",
            authorId = cryptoBob.authorId,
            text = "Comment on draft",
            timestamp = System.currentTimeMillis() + 10,
            replyToHash = "hash_root",
            rootHash = "hash_root"
        )
        db.postViewDao().upsert(post)
        db.postViewDao().upsert(reply)

        // Update text
        db.postViewDao().updatePostText("hash_root", "Edited finalized text")
        assertEquals("Edited finalized text", db.postViewDao().getPost("hash_root")?.text)

        // Delete post cascades to its direct reply
        db.postViewDao().deletePost("hash_root")
        assertNull(db.postViewDao().getPost("hash_root"))
        assertNull(db.postViewDao().getPost("hash_reply_1"))
    }

    @Test
    fun testReactionDeduplicationAndPollOptionVoting() = runBlocking {
        val targetHash = "poll_post_1"
        val authorId = cryptoAlice.authorId

        // Option 0 vote
        val voteOpt0 = ReactionEntity(
            targetHash = targetHash,
            authorId = authorId,
            emoji = "🗳️",
            timestamp = System.currentTimeMillis(),
            pollOptionIndex = 0
        )
        db.reactionDao().insertReaction(voteOpt0)

        val votesInitially = db.reactionDao().observePollVotesForPost(targetHash).first()
        assertEquals(1, votesInitially.size)
        assertEquals(0, votesInitially[0].pollOptionIndex)

        // User changes vote to Option 2: delete old vote and insert new
        db.reactionDao().deleteExistingPollVote(targetHash, authorId)
        val voteOpt2 = ReactionEntity(
            targetHash = targetHash,
            authorId = authorId,
            emoji = "🗳️",
            timestamp = System.currentTimeMillis() + 50,
            pollOptionIndex = 2
        )
        db.reactionDao().insertReaction(voteOpt2)

        val votesAfterChange = db.reactionDao().observePollVotesForPost(targetHash).first()
        assertEquals(1, votesAfterChange.size)
        assertEquals(2, votesAfterChange[0].pollOptionIndex)
    }

    @Test
    fun testDirectMessageMonotonicStatusProgression() = runBlocking {
        val messageId = "dm_msg_100"
        val peerId = cryptoBob.authorId
        val msg = DirectMessageEntity(
            messageId = messageId,
            conversationPeerId = peerId,
            senderId = cryptoAlice.authorId,
            recipientId = peerId,
            text = "Hello Bob",
            timestamp = System.currentTimeMillis(),
            isOutgoing = true,
            status = 1 // 1 = SENT
        )
        db.directMessageDao().insertMessage(msg)

        // Progress to 2 = DELIVERED
        db.directMessageDao().updateMessageStatusIfHigher(messageId, 2)
        val msgDelivered = db.directMessageDao().observeMessagesForPeer(peerId).first()[0]
        assertEquals(2, msgDelivered.status)

        // Progress to 3 = READ
        db.directMessageDao().updateMessageStatusIfHigher(messageId, 3)
        val msgRead = db.directMessageDao().observeMessagesForPeer(peerId).first()[0]
        assertEquals(3, msgRead.status)

        // Attempt to regress status back to 2 (DELIVERED) should be ignored
        db.directMessageDao().updateMessageStatusIfHigher(messageId, 2)
        val msgStillRead = db.directMessageDao().observeMessagesForPeer(peerId).first()[0]
        assertEquals("Status must not regress from READ to DELIVERED", 3, msgStillRead.status)
    }

    @Test
    fun testDirectMessageUnreadCountsAndRecentGrouping() = runBlocking {
        val now = System.currentTimeMillis()
        val peerBob = cryptoBob.authorId
        val peerCharlie = "@charlie_test_key.ed25519"

        // Outgoing message (should NOT increment unread badge)
        val msgOut = DirectMessageEntity(
            messageId = "msg_out",
            conversationPeerId = peerBob,
            senderId = cryptoAlice.authorId,
            recipientId = peerBob,
            text = "Hey Bob",
            timestamp = now,
            isOutgoing = true,
            isRead = false
        )
        // Incoming message from Bob (unread)
        val msgInBob = DirectMessageEntity(
            messageId = "msg_in_bob",
            conversationPeerId = peerBob,
            senderId = peerBob,
            recipientId = cryptoAlice.authorId,
            text = "Hey Alice!",
            timestamp = now + 10,
            isOutgoing = false,
            isRead = false
        )
        // Incoming message from Charlie (unread)
        val msgInCharlie = DirectMessageEntity(
            messageId = "msg_in_charlie",
            conversationPeerId = peerCharlie,
            senderId = peerCharlie,
            recipientId = cryptoAlice.authorId,
            text = "Hello from Charlie",
            timestamp = now + 20,
            isOutgoing = false,
            isRead = false
        )

        db.directMessageDao().insertMessage(msgOut)
        db.directMessageDao().insertMessage(msgInBob)
        db.directMessageDao().insertMessage(msgInCharlie)

        // Total unread should be 2 (incoming only)
        val totalUnread = db.directMessageDao().observeTotalUnreadCount().first()
        assertEquals(2, totalUnread)

        val bobUnread = db.directMessageDao().observeUnreadCountForPeer(peerBob).first()
        assertEquals(1, bobUnread)

        // Test Recent Conversations grouping: should have 2 conversations, Charlie first because newer timestamp
        val recent = db.directMessageDao().observeRecentConversations().first()
        assertEquals(2, recent.size)
        assertEquals(peerCharlie, recent[0].conversationPeerId)
        assertEquals(peerBob, recent[1].conversationPeerId)

        // Mark Bob conversation as read
        db.directMessageDao().markConversationAsRead(peerBob)
        assertEquals(0, db.directMessageDao().observeUnreadCountForPeer(peerBob).first())
        assertEquals(1, db.directMessageDao().observeTotalUnreadCount().first())
    }

    @Test
    fun testBidirectionalFollowAndVerification() = runBlocking {
        val alice = cryptoAlice.authorId
        val bob = cryptoBob.authorId

        val follow = FollowViewEntity(
            followerId = alice,
            followeeId = bob,
            isFollowing = true,
            updatedAtSeq = 1,
            isVerifiedInPerson = false
        )
        db.followDao().upsert(follow)

        // Bidirectional query: both (alice, bob) and (bob, alice) should recognize the following relationship
        assertEquals(true, db.followDao().isFollowing(alice, bob))
        assertEquals(true, db.followDao().isFollowing(bob, alice))
        assertEquals(false, db.followDao().isVerifiedInPerson(alice, bob))

        // Set in-person verification
        db.followDao().setVerifiedInPerson(bob, true)
        assertEquals(true, db.followDao().isVerifiedInPerson(alice, bob))
        assertEquals(true, db.followDao().isVerifiedInPerson(bob, alice))
    }
}

