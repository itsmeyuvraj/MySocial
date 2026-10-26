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
}
