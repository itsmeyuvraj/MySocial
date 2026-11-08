package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.feed.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedSearchTest {

    private lateinit var db: MeshDatabase
    private lateinit var crypto: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine
    private lateinit var repository: MeshRepository
    private lateinit var viewModel: FeedViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        crypto = CryptoManager.createInMemory(seed = ByteArray(32) { 42 })
        syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = crypto,
            myDisplayName = "Tester",
            openMeshMode = true
        )
        repository = MeshRepository(
            db = db,
            cryptoManager = crypto,
            syncEngine = syncEngine
        )
        viewModel = FeedViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testSearchStateTransitions() {
        assertFalse(viewModel.isSearchActive.value)
        assertEquals("", viewModel.searchQuery.value)

        viewModel.openSearch()
        assertTrue(viewModel.isSearchActive.value)

        viewModel.setSearchQuery("bluetooth")
        assertEquals("bluetooth", viewModel.searchQuery.value)

        viewModel.closeSearch()
        assertFalse(viewModel.isSearchActive.value)
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun testFeedFilteringByContentAndTags() = runBlocking {
        // Publish sample posts
        repository.createPost("Welcome to offline peer mesh network!", tags = emptyList(), isPublic = true)
        repository.createPost("Emergency medical supplies needed", tags = listOf("sos", "aid"), isPublic = true)
        repository.createPost("Photos from today's mountain hike", tags = listOf("nature"), isPublic = true)

        // Initial feed should contain all 3 posts
        val initialPosts = viewModel.feedPosts.first { it.size == 3 }
        assertEquals(3, initialPosts.size)

        // Search by keyword "emergency"
        viewModel.setSearchQuery("emergency")
        val emergencyResults = viewModel.feedPosts.first { it.size == 1 }
        assertEquals(1, emergencyResults.size)
        assertTrue(emergencyResults[0].post.text.contains("Emergency medical supplies"))

        // Search by hashtag "#nature"
        viewModel.setSearchQuery("nature")
        val natureResults = viewModel.feedPosts.first { it.size == 1 }
        assertEquals(1, natureResults.size)
        assertTrue(natureResults[0].post.text.contains("mountain hike"))

        // Search with non-matching query
        viewModel.setSearchQuery("nonexistent_keyword_123")
        val emptyResults = viewModel.feedPosts.first { it.isEmpty() }
        assertEquals(0, emptyResults.size)

        // Clearing query restores all posts
        viewModel.setSearchQuery("")
        val restoredPosts = viewModel.feedPosts.first { it.size == 3 }
        assertEquals(3, restoredPosts.size)
    }
}
