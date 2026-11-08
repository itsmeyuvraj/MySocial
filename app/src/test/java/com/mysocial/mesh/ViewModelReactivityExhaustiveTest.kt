package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.CanonicalJson
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.model.FollowViewEntity
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.TransportType
import com.mysocial.mesh.transport.DiscoveredPeer
import com.mysocial.mesh.ui.feed.FeedViewModel
import com.mysocial.mesh.ui.identity.IdentityViewModel
import com.mysocial.mesh.ui.messages.ChatViewModel
import com.mysocial.mesh.ui.messages.MessagesViewModel
import com.mysocial.mesh.ui.radar.RadarViewModel
import com.mysocial.mesh.utils.toBbid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelReactivityExhaustiveTest {

    private lateinit var db: MeshDatabase
    private lateinit var cryptoAlice: CryptoManager
    private lateinit var cryptoBob: CryptoManager
    private lateinit var cryptoCharlie: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine
    private lateinit var repository: MeshRepository
    private lateinit var appSettings: AppSettings
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        cryptoAlice = CryptoManager.createInMemory(seed = ByteArray(32) { 1 })
        cryptoBob = CryptoManager.createInMemory(seed = ByteArray(32) { 2 })
        cryptoCharlie = CryptoManager.createInMemory(seed = ByteArray(32) { 3 })
        appSettings = AppSettings.getInstance(context)

        syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoAlice,
            myDisplayName = "Alice",
            openMeshMode = true,
            appSettings = appSettings
        )
        repository = MeshRepository(
            db = db,
            cryptoManager = cryptoAlice,
            syncEngine = syncEngine,
            appSettings = appSettings
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testFeedViewModelOpenMeshVsFriendsFilter() = runBlocking {
        val feedVm = FeedViewModel(repository)
        val now = System.currentTimeMillis()

        // 1. Alice creates her own post
        repository.createPost(text = "Alice's status update")

        // 2. Bob creates a public post
        val bobPost = PostViewEntity(
            entryHash = "hash_bob_post",
            authorId = cryptoBob.authorId,
            text = "Bob's field dispatch",
            timestamp = now + 100,
            replyToHash = null,
            rootHash = null,
            isPublic = true
        )
        db.postViewDao().upsert(bobPost)

        // 3. Charlie (stranger) creates a public post
        val charliePost = PostViewEntity(
            entryHash = "hash_charlie_post",
            authorId = cryptoCharlie.authorId,
            text = "Charlie's public broadcast",
            timestamp = now + 200,
            replyToHash = null,
            rootHash = null,
            isPublic = true
        )
        db.postViewDao().upsert(charliePost)

        // Alice follows Bob
        repository.toggleFollow(cryptoBob.authorId, following = true)

        // Verify Open Mesh mode: all 3 posts are visible
        feedVm.setFilter(openMesh = true)
        val openMeshPosts = feedVm.feedPosts.first { it.size == 3 }
        assertEquals(3, openMeshPosts.size)
        val openMeshHashes = openMeshPosts.map { it.post.entryHash }.toSet()
        assertTrue(openMeshHashes.contains("hash_bob_post"))
        assertTrue(openMeshHashes.contains("hash_charlie_post"))

        // Switch to Friends Only mode
        feedVm.setFilter(openMesh = false)
        val friendsPosts = feedVm.feedPosts.first { it.size == 1 }
        assertEquals(1, friendsPosts.size)
        assertEquals("hash_bob_post", friendsPosts[0].post.entryHash)
        assertEquals(cryptoBob.authorId, friendsPosts[0].post.authorId)

        // Alice unfollows Bob -> Friends feed becomes empty
        repository.toggleFollow(cryptoBob.authorId, following = false)
        val emptyFriendsPosts = feedVm.feedPosts.first { it.isEmpty() }
        assertEquals(0, emptyFriendsPosts.size)
    }

    @Test
    fun testFeedViewModelMultiFieldSearch() = runBlocking {
        val feedVm = FeedViewModel(repository)
        val now = System.currentTimeMillis()

        // Author 1: Dave
        val daveId = "@dave.ed25519"
        db.profileDao().upsert(
            ProfileViewEntity(
                authorId = daveId,
                displayName = "Commander Dave",
                bio = "Field Ops Incident Lead",
                avatarSeed = "dave_seed",
                updatedAtSeq = 1L
            )
        )
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "hash_dave_post",
                authorId = daveId,
                text = "Emergency water rations distributed at Station 4",
                tagsJson = CanonicalJson.encodeToString(listOf("relief", "water")),
                timestamp = now + 10,
                replyToHash = null,
                rootHash = null,
                isPublic = true
            )
        )

        // Author 2: Emma
        val emmaId = "@emma.ed25519"
        db.profileDao().upsert(
            ProfileViewEntity(
                authorId = emmaId,
                displayName = "Emma Watson",
                bio = "Avid Bookworm & Botanist",
                avatarSeed = "emma_seed",
                updatedAtSeq = 1L
            )
        )
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "hash_emma_post",
                authorId = emmaId,
                text = "Sunny afternoon relaxing in the public gardens",
                tagsJson = CanonicalJson.encodeToString(listOf("park", "weekend")),
                timestamp = now + 20,
                replyToHash = null,
                rootHash = null,
                isPublic = true
            )
        )

        // Initial check: both posts visible
        val initialPosts = feedVm.feedPosts.first { it.size == 2 }
        assertEquals(2, initialPosts.size)

        // 1. Text search
        feedVm.openSearch()
        assertTrue(feedVm.isSearchActive.value)
        feedVm.setSearchQuery("water")
        val textResults = feedVm.feedPosts.first { it.size == 1 }
        assertEquals("hash_dave_post", textResults[0].post.entryHash)

        // 2. Tag search with '#' prefix
        feedVm.setSearchQuery("#weekend")
        val tagResults = feedVm.feedPosts.first { it.size == 1 }
        assertEquals("hash_emma_post", tagResults[0].post.entryHash)

        // 3. Author Display Name search
        feedVm.setSearchQuery("Commander")
        val authorNameResults = feedVm.feedPosts.first { it.size == 1 }
        assertEquals("hash_dave_post", authorNameResults[0].post.entryHash)

        // 4. Author Bio search
        feedVm.setSearchQuery("Botanist")
        val bioResults = feedVm.feedPosts.first { it.size == 1 }
        assertEquals("hash_emma_post", bioResults[0].post.entryHash)

        // 5. Author BBID search
        val daveBbid = daveId.toBbid()
        feedVm.setSearchQuery(daveBbid)
        val bbidResults = feedVm.feedPosts.first { it.size == 1 }
        assertEquals("hash_dave_post", bbidResults[0].post.entryHash)

        // 6. Close search restores full list and clears query
        feedVm.closeSearch()
        assertFalse(feedVm.isSearchActive.value)
        assertEquals("", feedVm.searchQuery.value)
        val restoredPosts = feedVm.feedPosts.first { it.size == 2 }
        assertEquals(2, restoredPosts.size)
    }

    @Test
    fun testFeedViewModelHashtagPinningAndMuting() = runBlocking {
        val feedVm = FeedViewModel(repository)
        val now = System.currentTimeMillis()

        // Tag pinning
        feedVm.pinHashtag("mesh-updates")
        assertTrue(feedVm.pinnedHashtags.first { it.contains("mesh-updates") }.contains("mesh-updates"))
        feedVm.unpinHashtag("mesh-updates")
        assertFalse(feedVm.pinnedHashtags.first { !it.contains("mesh-updates") }.contains("mesh-updates"))

        // Stranger post with only "spam" tag
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "hash_spam_only",
                authorId = cryptoBob.authorId,
                text = "Buy cheap goods now!",
                tagsJson = CanonicalJson.encodeToString(listOf("spam")),
                timestamp = now + 10,
                replyToHash = null,
                rootHash = null,
                isPublic = true
            )
        )

        // Stranger post with "spam" and "tech"
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "hash_mixed_tags",
                authorId = cryptoBob.authorId,
                text = "Tech news bulletin",
                tagsJson = CanonicalJson.encodeToString(listOf("spam", "tech")),
                timestamp = now + 20,
                replyToHash = null,
                rootHash = null,
                isPublic = true
            )
        )

        // Self post with "spam" tag
        repository.createPost(
            text = "My own test post",
            tags = listOf("spam")
        )

        // Verify all 3 appear before muting
        assertEquals(3, feedVm.feedPosts.first { it.size == 3 }.size)

        // Mute "spam"
        feedVm.muteHashtag("spam")
        assertTrue(feedVm.mutedHashtags.first { it.contains("spam") }.contains("spam"))

        // Post with ONLY "spam" from stranger must be filtered out.
        // Mixed post ("spam" + "tech") and Alice's own post remain visible!
        val filteredPosts = feedVm.feedPosts.first { it.size == 2 }
        assertEquals(2, filteredPosts.size)
        val filteredHashes = filteredPosts.map { it.post.entryHash }
        assertFalse("Spam-only stranger post must be filtered", filteredHashes.contains("hash_spam_only"))
        assertTrue("Mixed-tag post must remain", filteredHashes.contains("hash_mixed_tags"))

        // Unmute "spam" restores stranger post
        feedVm.unmuteHashtag("spam")
        assertEquals(3, feedVm.feedPosts.first { it.size == 3 }.size)
    }

    @Test
    fun testFeedViewModelPostActionsReactivity() = runBlocking {
        val feedVm = FeedViewModel(repository)
        val createResult = repository.createPost(
            text = "Original status update",
            pollOptions = listOf("Option 1", "Option 2")
        )
        assertTrue(createResult.isSuccess)
        val postHash = createResult.getOrThrow().entryHash

        // Verify post is in feed
        val initialPosts = feedVm.feedPosts.first { it.isNotEmpty() }
        assertEquals("Original status update", initialPosts[0].post.text)

        // 1. Like post -> reactive update to userLikedPostHashes
        feedVm.likePost(postHash)
        val liked = feedVm.userLikedPostHashes.first { it.contains(postHash) }
        assertTrue(liked.contains(postHash))

        // 2. Vote in poll
        feedVm.votePoll(postHash, 0)

        // 3. Reply to post
        feedVm.replyToPost(text = "First reply comment", rootHash = postHash, parentHash = postHash)
        val replies = feedVm.observeReplies(postHash).first { it.isNotEmpty() }
        assertEquals(1, replies.size)
        assertEquals("First reply comment", replies[0].post.text)

        // 4. Edit post
        feedVm.editPost(postHash, "Edited status update text")
        val editedPosts = feedVm.feedPosts.first { list -> list.any { it.post.text == "Edited status update text" } }
        assertTrue(editedPosts.any { it.post.text == "Edited status update text" })

        // 5. Delete post
        feedVm.deletePost(postHash)
        val afterDelete = feedVm.feedPosts.first { list -> list.none { it.post.entryHash == postHash } }
        assertTrue(afterDelete.none { it.post.entryHash == postHash })
    }

    @Test
    fun testRadarViewModelTtlPruningAndPeerNameResolution() = runBlocking {
        val radarVm = RadarViewModel(repository, getService = { null })
        val now = System.currentTimeMillis()

        // 1. Known profile for peer_resolved
        val peerResolvedId = "@resolved.ed25519"
        db.profileDao().upsert(
            ProfileViewEntity(
                authorId = peerResolvedId,
                displayName = "Dr. Stone",
                bio = "Scientist",
                avatarSeed = "stone_seed",
                updatedAtSeq = 1L
            )
        )

        // 2. Setup discovered peers
        val freshPeer = DiscoveredPeer(
            peerId = "@fresh.ed25519",
            peerName = "Fresh Peer",
            transportType = TransportType.MDNS_WIFI,
            lastSeenTimestamp = now - 5000 // 5 seconds ago
        )
        val stalePeer = DiscoveredPeer(
            peerId = "@stale.ed25519",
            peerName = "Stale Peer",
            transportType = TransportType.MDNS_WIFI,
            lastSeenTimestamp = now - 60_000 // 60 seconds ago (> 45s TTL)
        )
        val selfPeer = DiscoveredPeer(
            peerId = cryptoAlice.authorId,
            peerName = "Alice",
            transportType = TransportType.BLE,
            lastSeenTimestamp = now
        )
        val genericPeerWithProfile = DiscoveredPeer(
            peerId = peerResolvedId,
            peerName = "Nearby Device", // Generic placeholder
            transportType = TransportType.BLE,
            lastSeenTimestamp = now - 2000
        )
        val genericPeerWithoutProfile = DiscoveredPeer(
            peerId = "@unknown.ed25519",
            peerName = "Nearby Device", // Generic placeholder without profile
            transportType = TransportType.MDNS_WIFI,
            lastSeenTimestamp = now - 3000
        )

        repository.updateDiscoveredPeers(
            listOf(freshPeer, stalePeer, selfPeer, genericPeerWithProfile, genericPeerWithoutProfile)
        )

        val activePeers = radarVm.discoveredPeers.first { it.isNotEmpty() }

        // Self must be excluded
        assertFalse(activePeers.any { it.peerId == cryptoAlice.authorId })

        // Stale peer (> 45s) must be pruned
        assertFalse(activePeers.any { it.peerId == "@stale.ed25519" })

        // Fresh peer retained
        assertTrue(activePeers.any { it.peerId == "@fresh.ed25519" })

        // Generic peer with db profile resolved to "Dr. Stone"
        val resolved = activePeers.find { it.peerId == peerResolvedId }
        assertNotNull(resolved)
        assertEquals("Dr. Stone", resolved!!.peerName)

        // Generic peer without db profile falls back to "Nearby Friend"
        val fallback = activePeers.find { it.peerId == "@unknown.ed25519" }
        assertNotNull(fallback)
        assertEquals("Nearby Friend", fallback!!.peerName)
    }

    @Test
    fun testIdentityViewModelReactivity() = runBlocking {
        val identityVm = IdentityViewModel(repository)

        // 1. My Profile updates
        repository.updateProfile(
            displayName = "Alice In Wonderland",
            bio = "Decentralized mesh researcher",
            avatarSeed = "seed_rabbit_hole"
        )
        val profile = identityVm.myProfile.first { it != null }
        assertEquals("Alice In Wonderland", profile?.displayName)
        assertEquals("Decentralized mesh researcher", profile?.bio)
        assertEquals("seed_rabbit_hole", profile?.avatarSeed)

        // 2. Post creation reflects in dbStats and myPosts
        val initialEntries = identityVm.dbStats.value.totalEntries
        repository.createPost(text = "Timeline post 1")
        repository.createPost(text = "Timeline post 2")

        // Friend creates a post directly in postViewDao
        db.postViewDao().upsert(
            PostViewEntity(
                entryHash = "hash_bob_timeline",
                authorId = cryptoBob.authorId,
                text = "Bob's separate timeline post",
                timestamp = System.currentTimeMillis(),
                replyToHash = null,
                rootHash = null,
                isPublic = true
            )
        )

        // Verify myPosts only contains Alice's posts
        val myPosts = identityVm.myPosts.first { it.size == 2 }
        assertEquals(2, myPosts.size)
        assertTrue(myPosts.all { it.post.authorId == cryptoAlice.authorId })

        // Verify dbStats totalEntries count updated from local log insertions
        val stats = identityVm.dbStats.first { it.totalEntries >= initialEntries + 2 }
        assertTrue(stats.totalEntries >= initialEntries + 2)
        assertEquals(3, repository.getTotalPostsCount())
    }

    @Test
    fun testMessagesViewModelSearchAndUnreadCount() = runBlocking {
        val messagesVm = MessagesViewModel(repository)
        val now = System.currentTimeMillis()

        // Set up profile for Bob
        db.profileDao().upsert(
            ProfileViewEntity(
                authorId = cryptoBob.authorId,
                displayName = "Bob Dylan",
                bio = "Singer / Songwriter",
                avatarSeed = "bob_seed",
                updatedAtSeq = 1L
            )
        )

        // Insert unread incoming message from Bob
        val msgBob = DirectMessageEntity(
            messageId = "msg_bob_1",
            conversationPeerId = cryptoBob.authorId,
            senderId = cryptoBob.authorId,
            recipientId = cryptoAlice.authorId,
            text = "Are you on the mesh network?",
            timestamp = now - 1000,
            isOutgoing = false,
            isRead = false,
            status = 2
        )
        db.directMessageDao().insertMessage(msgBob)

        // Insert read incoming message from Charlie
        val msgCharlie = DirectMessageEntity(
            messageId = "msg_charlie_1",
            conversationPeerId = cryptoCharlie.authorId,
            senderId = cryptoCharlie.authorId,
            recipientId = cryptoAlice.authorId,
            text = "Meeting notes attached",
            timestamp = now - 500,
            isOutgoing = false,
            isRead = true,
            status = 2
        )
        db.directMessageDao().insertMessage(msgCharlie)

        // Verify total unread count is 1 (only Bob's message is unread)
        val totalUnread = messagesVm.totalUnreadCount.first { it == 1 }
        assertEquals(1, totalUnread)

        // Verify conversations list contains both
        val conversations = messagesVm.conversations.first { it.size == 2 }
        assertEquals(2, conversations.size)

        // Search by query "Bob"
        messagesVm.setSearchQuery("bob")
        val searchBob = messagesVm.conversations.first { it.size == 1 }
        assertEquals(cryptoBob.authorId, searchBob[0].peerId)
        assertEquals("Bob Dylan", searchBob[0].peerDisplayName)

        // Search by message content "meeting"
        messagesVm.setSearchQuery("meeting")
        val searchMeeting = messagesVm.conversations.first { it.size == 1 }
        assertEquals(cryptoCharlie.authorId, searchMeeting[0].peerId)

        // Clear search
        messagesVm.setSearchQuery("")
        assertEquals(2, messagesVm.conversations.first { it.size == 2 }.size)

        // ChatViewModel initializes for Bob and calls markAsRead()
        val chatVm = ChatViewModel(cryptoBob.authorId, repository)
        chatVm.markAsRead()

        // Verify unread count becomes 0
        val updatedUnread = messagesVm.totalUnreadCount.first { it == 0 }
        assertEquals(0, updatedUnread)
    }
}
