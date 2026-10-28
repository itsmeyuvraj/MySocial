package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
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
@Config(sdk = [34])
class PostVisibilityTest {

    private lateinit var db: MeshDatabase
    private lateinit var cryptoAlice: CryptoManager
    private lateinit var cryptoBob: CryptoManager
    private lateinit var cryptoCharlie: CryptoManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)

        cryptoAlice = CryptoManager.createInMemory(seed = ByteArray(32) { 1 })
        cryptoBob = CryptoManager.createInMemory(seed = ByteArray(32) { 2 })
        cryptoCharlie = CryptoManager.createInMemory(seed = ByteArray(32) { 3 })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPostPayloadSerializationAndCompatibility() {
        // Test public post serialization
        val publicPost = PostPayload(text = "Hello world", isPublic = true)
        val publicJson = publicPost.toJson()
        val parsedPublic = PostPayload.fromJson(publicJson)
        assertTrue(parsedPublic.isPublic)
        assertEquals("Hello world", parsedPublic.text)

        // Test friends-only post serialization
        val friendsPost = PostPayload(text = "Secret for friends", isPublic = false)
        val friendsJson = friendsPost.toJson()
        val parsedFriends = PostPayload.fromJson(friendsJson)
        assertFalse(parsedFriends.isPublic)
        assertEquals("Secret for friends", parsedFriends.text)

        // Test backward compatibility when isPublic is absent in legacy JSON
        val legacyJson = """{"text":"Legacy post","tags":[],"media":null}"""
        val parsedLegacy = PostPayload.fromJson(legacyJson)
        assertTrue("Legacy posts without isPublic must default to public", parsedLegacy.isPublic)
        assertEquals("Legacy post", parsedLegacy.text)
    }

    @Test
    fun testPublicAndFriendsOnlyPostVisibilityInFeeds() = runBlocking {
        val aliceAuthorId = cryptoAlice.authorId
        val bobAuthorId = cryptoBob.authorId
        val charlieAuthorId = cryptoCharlie.authorId

        // 1. Alice publishes a public post
        val publicPayload = PostPayload(text = "Alice's public announcement", isPublic = true).toJson()
        val publicSig = cryptoAlice.signEntry(1L, null, 1000L, ContentType.POST.name, publicPayload)
        val publicEntry = LogEntry(
            authorId = aliceAuthorId,
            sequence = 1L,
            previousHash = null,
            timestamp = 1000L,
            contentType = ContentType.POST,
            contentJson = publicPayload,
            signature = publicSig,
            entryHash = CryptoManager.computeEntryHash(aliceAuthorId, 1L, null, 1000L, ContentType.POST.name, publicPayload, publicSig)
        )
        db.insertAndProject(publicEntry)

        // 2. Alice publishes a friends-only post
        val friendsPayload = PostPayload(text = "Alice's friends-only secret", isPublic = false).toJson()
        val friendsSig = cryptoAlice.signEntry(2L, publicEntry.entryHash, 2000L, ContentType.POST.name, friendsPayload)
        val friendsEntry = LogEntry(
            authorId = aliceAuthorId,
            sequence = 2L,
            previousHash = publicEntry.entryHash,
            timestamp = 2000L,
            contentType = ContentType.POST,
            contentJson = friendsPayload,
            signature = friendsSig,
            entryHash = CryptoManager.computeEntryHash(aliceAuthorId, 2L, publicEntry.entryHash, 2000L, ContentType.POST.name, friendsPayload, friendsSig)
        )
        db.insertAndProject(friendsEntry)

        // 3. Alice adds Bob via QR code (Alice follows Bob)
        val followBobPayload = FollowPayload(targetAuthorId = bobAuthorId, following = true).toJson()
        val followSig = cryptoAlice.signEntry(3L, friendsEntry.entryHash, 3000L, ContentType.FOLLOW.name, followBobPayload)
        val followEntry = LogEntry(
            authorId = aliceAuthorId,
            sequence = 3L,
            previousHash = friendsEntry.entryHash,
            timestamp = 3000L,
            contentType = ContentType.FOLLOW,
            contentJson = followBobPayload,
            signature = followSig,
            entryHash = CryptoManager.computeEntryHash(aliceAuthorId, 3L, friendsEntry.entryHash, 3000L, ContentType.FOLLOW.name, followBobPayload, followSig)
        )
        db.insertAndProject(followEntry)

        // --- VERIFY ALICE'S VIEW ---
        // Alice should see both her public and friends-only posts
        val aliceFeed = db.postViewDao().observeAllFeed(aliceAuthorId).first()
        assertEquals(2, aliceFeed.size)
        assertTrue(aliceFeed.any { it.post.text == "Alice's public announcement" })
        assertTrue(aliceFeed.any { it.post.text == "Alice's friends-only secret" })

        val aliceOwnProfile = db.postViewDao().observePostsByAuthor(aliceAuthorId, aliceAuthorId).first()
        assertEquals(2, aliceOwnProfile.size)

        // --- VERIFY BOB'S VIEW (Added via QR code by Alice) ---
        // Bob should see both Alice's public post and friends-only post
        val bobFeed = db.postViewDao().observeAllFeed(bobAuthorId).first()
        assertEquals(2, bobFeed.size)
        assertTrue(bobFeed.any { it.post.text == "Alice's public announcement" })
        assertTrue(bobFeed.any { it.post.text == "Alice's friends-only secret" })

        val bobProfileViewOfAlice = db.postViewDao().observePostsByAuthor(aliceAuthorId, bobAuthorId).first()
        assertEquals(2, bobProfileViewOfAlice.size)

        // Verify mutual friend list:
        val aliceFriends = db.followDao().observeFollowedAuthorIds(aliceAuthorId).first()
        assertTrue("Alice should see Bob in friends list", aliceFriends.contains(bobAuthorId))
        val bobFriends = db.followDao().observeFollowedAuthorIds(bobAuthorId).first()
        assertTrue("Bob should see Alice in friends list (mutual friend status with blue tick)", bobFriends.contains(aliceAuthorId))

        // --- VERIFY CHARLIE'S VIEW (Stranger, not added by Alice) ---
        // Charlie should ONLY see Alice's public post, NOT Alice's friends-only post
        val charlieFeed = db.postViewDao().observeAllFeed(charlieAuthorId).first()
        assertEquals(1, charlieFeed.size)
        assertEquals("Alice's public announcement", charlieFeed[0].post.text)
        assertFalse(charlieFeed.any { it.post.text == "Alice's friends-only secret" })

        // Charlie viewing Alice's profile should also only see the public post
        val charlieProfileViewOfAlice = db.postViewDao().observePostsByAuthor(aliceAuthorId, charlieAuthorId).first()
        assertEquals(1, charlieProfileViewOfAlice.size)
        assertEquals("Alice's public announcement", charlieProfileViewOfAlice[0].post.text)
    }

    @Test
    fun testPeerOlderPostsVisibleAccordingToAudienceWhenPeerIsAround() = runBlocking {
        val aliceId = cryptoAlice.authorId
        val bobId = cryptoBob.authorId

        // Bob has created older posts before encountering Alice:
        // 1. Older Public Post
        val olderPublicPayload = PostPayload(text = "Bob's older public announcement", isPublic = true).toJson()
        val sig1 = cryptoBob.signEntry(1L, null, 1000L, ContentType.POST.name, olderPublicPayload)
        val entry1 = LogEntry(
            authorId = bobId, sequence = 1L, previousHash = null, timestamp = 1000L,
            contentType = ContentType.POST, contentJson = olderPublicPayload, signature = sig1,
            entryHash = CryptoManager.computeEntryHash(bobId, 1L, null, 1000L, ContentType.POST.name, olderPublicPayload, sig1)
        )

        // 2. Older Friends-Only Post
        val olderFriendsPayload = PostPayload(text = "Bob's older friends-only secret", isPublic = false).toJson()
        val sig2 = cryptoBob.signEntry(2L, entry1.entryHash, 2000L, ContentType.POST.name, olderFriendsPayload)
        val entry2 = LogEntry(
            authorId = bobId, sequence = 2L, previousHash = entry1.entryHash, timestamp = 2000L,
            contentType = ContentType.POST, contentJson = olderFriendsPayload, signature = sig2,
            entryHash = CryptoManager.computeEntryHash(bobId, 2L, entry1.entryHash, 2000L, ContentType.POST.name, olderFriendsPayload, sig2)
        )

        // 3. Newer Public Post
        val newerPublicPayload = PostPayload(text = "Bob's newer post just made", isPublic = true).toJson()
        val sig3 = cryptoBob.signEntry(3L, entry2.entryHash, 3000L, ContentType.POST.name, newerPublicPayload)
        val entry3 = LogEntry(
            authorId = bobId, sequence = 3L, previousHash = entry2.entryHash, timestamp = 3000L,
            contentType = ContentType.POST, contentJson = newerPublicPayload, signature = sig3,
            entryHash = CryptoManager.computeEntryHash(bobId, 3L, entry2.entryHash, 3000L, ContentType.POST.name, newerPublicPayload, sig3)
        )

        // Bob comes into range of Alice -> Sync engine synchronizes all entries into Alice's database
        db.insertAndProjectBatch(listOf(entry1, entry2, entry3), hopDistance = 1)

        // Scenario 1: Alice and Bob are strangers (not friends yet)
        // Alice should see BOTH Bob's older public post and newer public post, but NOT Bob's older friends-only post
        val strangerFeed = db.postViewDao().observeAllFeed(aliceId).first()
        assertEquals(2, strangerFeed.size)
        assertTrue(strangerFeed.any { it.post.text == "Bob's older public announcement" })
        assertTrue(strangerFeed.any { it.post.text == "Bob's newer post just made" })
        assertFalse(strangerFeed.any { it.post.text == "Bob's older friends-only secret" })

        // Bob's profile viewed by Alice as a stranger:
        val strangerProfileView = db.postViewDao().observePostsByAuthor(bobId, aliceId).first()
        assertEquals(2, strangerProfileView.size)
        assertFalse(strangerProfileView.any { it.post.text == "Bob's older friends-only secret" })

        // Scenario 2: Alice adds Bob as a friend (Follow)
        val followPayload = FollowPayload(targetAuthorId = bobId, following = true).toJson()
        val followSig = cryptoAlice.signEntry(1L, null, 4000L, ContentType.FOLLOW.name, followPayload)
        val followEntry = LogEntry(
            authorId = aliceId, sequence = 1L, previousHash = null, timestamp = 4000L,
            contentType = ContentType.FOLLOW, contentJson = followPayload, signature = followSig,
            entryHash = CryptoManager.computeEntryHash(aliceId, 1L, null, 4000L, ContentType.FOLLOW.name, followPayload, followSig)
        )
        db.insertAndProject(followEntry)

        // Now Alice and Bob are friends:
        // Alice's feed MUST include Bob's older friends-only post as well!
        val friendsFeed = db.postViewDao().observeAllFeed(aliceId).first()
        assertEquals(3, friendsFeed.size)
        assertTrue(friendsFeed.any { it.post.text == "Bob's older public announcement" })
        assertTrue(friendsFeed.any { it.post.text == "Bob's older friends-only secret" })
        assertTrue(friendsFeed.any { it.post.text == "Bob's newer post just made" })

        // Profile view by friend:
        val friendProfileView = db.postViewDao().observePostsByAuthor(bobId, aliceId).first()
        assertEquals(3, friendProfileView.size)
        assertTrue(friendProfileView.any { it.post.text == "Bob's older friends-only secret" })
    }

    @Test
    fun testFriendsOnlyFeedExcludesSelfPosts() = runBlocking {
        val aliceId = cryptoAlice.authorId
        val bobId = cryptoBob.authorId

        // Alice creates a post
        val alicePayload = PostPayload(text = "Alice's post", isPublic = true).toJson()
        val aliceSig = cryptoAlice.signEntry(1L, null, 1000L, ContentType.POST.name, alicePayload)
        val aliceEntry = LogEntry(
            authorId = aliceId, sequence = 1L, previousHash = null, timestamp = 1000L,
            contentType = ContentType.POST, contentJson = alicePayload, signature = aliceSig,
            entryHash = CryptoManager.computeEntryHash(aliceId, 1L, null, 1000L, ContentType.POST.name, alicePayload, aliceSig)
        )
        db.insertAndProject(aliceEntry)

        // Bob creates a post
        val bobPayload = PostPayload(text = "Bob's post", isPublic = true).toJson()
        val bobSig = cryptoBob.signEntry(1L, null, 1500L, ContentType.POST.name, bobPayload)
        val bobEntry = LogEntry(
            authorId = bobId, sequence = 1L, previousHash = null, timestamp = 1500L,
            contentType = ContentType.POST, contentJson = bobPayload, signature = bobSig,
            entryHash = CryptoManager.computeEntryHash(bobId, 1L, null, 1500L, ContentType.POST.name, bobPayload, bobSig)
        )
        db.insertAndProject(bobEntry)

        // Alice adds Bob as a friend
        val followPayload = FollowPayload(targetAuthorId = bobId, following = true).toJson()
        val followSig = cryptoAlice.signEntry(2L, aliceEntry.entryHash, 2000L, ContentType.FOLLOW.name, followPayload)
        val followEntry = LogEntry(
            authorId = aliceId, sequence = 2L, previousHash = aliceEntry.entryHash, timestamp = 2000L,
            contentType = ContentType.FOLLOW, contentJson = followPayload, signature = followSig,
            entryHash = CryptoManager.computeEntryHash(aliceId, 2L, aliceEntry.entryHash, 2000L, ContentType.FOLLOW.name, followPayload, followSig)
        )
        db.insertAndProject(followEntry)

        // All Feed (Nearby): Alice sees both her own post and Bob's post
        val allFeed = db.postViewDao().observeAllFeed(aliceId).first()
        assertEquals(2, allFeed.size)
        assertTrue(allFeed.any { it.post.authorId == aliceId })
        assertTrue(allFeed.any { it.post.authorId == bobId })

        // Friends Only Feed: Alice sees ONLY Bob's post, NOT her own post
        val friendsOnlyFeed = db.postViewDao().observeFollowedAndTwoHopFeed(aliceId).first()
        assertEquals(1, friendsOnlyFeed.size)
        assertEquals("Bob's post", friendsOnlyFeed[0].post.text)
        assertFalse(friendsOnlyFeed.any { it.post.authorId == aliceId })
    }
}

