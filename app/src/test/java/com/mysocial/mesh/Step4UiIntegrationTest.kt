package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.composer.ComposerViewModel
import com.mysocial.mesh.ui.composer.ExpiryOption
import com.mysocial.mesh.ui.feed.FeedViewModel
import com.mysocial.mesh.ui.identity.IdentityViewModel
import com.mysocial.mesh.utils.TrustQrUtils
import com.mysocial.mesh.utils.toBbid
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
class Step4UiIntegrationTest {

    private lateinit var db: MeshDatabase
    private lateinit var crypto: CryptoManager
    private lateinit var friendCrypto: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine
    private lateinit var repository: MeshRepository
    private lateinit var appSettings: AppSettings
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        crypto = CryptoManager.createInMemory(seed = ByteArray(32) { 42 })
        friendCrypto = CryptoManager.createInMemory(seed = ByteArray(32) { 77 })
        appSettings = AppSettings.getInstance(context)

        syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = crypto,
            myDisplayName = "TestUser",
            openMeshMode = true,
            appSettings = appSettings
        )
        repository = MeshRepository(
            db = db,
            cryptoManager = crypto,
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
    fun testBbidFeedSearchFiltering() = runBlocking {
        val feedVm = FeedViewModel(repository)

        // Create post from friend
        val friendAuthorId = friendCrypto.authorId
        val friendBbid = friendAuthorId.toBbid()

        repository.createPost(
            text = "Hello from local mesh node"
        )

        // Verify post shows in feed
        val initialPosts = feedVm.feedPosts.first { it.isNotEmpty() }
        assertEquals(1, initialPosts.size)

        // Search by author's BBID
        feedVm.openSearch()
        val bbidQuery = crypto.authorId.toBbid()
        feedVm.setSearchQuery(bbidQuery)

        val searchResult = feedVm.feedPosts.first { it.isNotEmpty() }
        assertEquals(1, searchResult.size)

        // Search by a non-matching BBID
        feedVm.setSearchQuery("ZZZZ-ZZZZ")
        val noMatch = feedVm.feedPosts.first { it.isEmpty() }
        assertEquals(0, noMatch.size)
    }

    @Test
    fun testHashtagPinningAndMuting() = runBlocking {
        val feedVm = FeedViewModel(repository)

        feedVm.pinHashtag("urgent")
        assertTrue(feedVm.pinnedHashtags.first().contains("urgent"))

        feedVm.unpinHashtag("urgent")
        assertFalse(feedVm.pinnedHashtags.first().contains("urgent"))

        feedVm.muteHashtag("spam")
        assertTrue(feedVm.mutedHashtags.first().contains("spam"))

        feedVm.unmuteHashtag("spam")
        assertFalse(feedVm.mutedHashtags.first().contains("spam"))
    }

    @Test
    fun testComposerViewModelPollAndSosState() = runBlocking {
        val composerVm = ComposerViewModel(repository)

        // Ephemeral TTL options
        composerVm.setExpiryOption(ExpiryOption.SIX_HOURS)
        assertEquals(ExpiryOption.SIX_HOURS, composerVm.expiryOption.value)
        assertEquals(6 * 3600_000L, composerVm.expiryOption.value.durationMs)

        // SOS beacon
        composerVm.setSos(true)
        assertTrue(composerVm.isSos.value)

        // Poll building
        composerVm.setPollEnabled(true)
        assertTrue(composerVm.isPollEnabled.value)
        assertEquals(2, composerVm.pollOptions.value.size)

        composerVm.updatePollOption(0, "Yes")
        composerVm.updatePollOption(1, "No")
        composerVm.addPollOption()
        assertEquals(3, composerVm.pollOptions.value.size)
        composerVm.updatePollOption(2, "Maybe")

        composerVm.removePollOption(2)
        assertEquals(2, composerVm.pollOptions.value.size)
        assertEquals("Yes", composerVm.pollOptions.value[0])
        assertEquals("No", composerVm.pollOptions.value[1])
    }

    @Test
    fun testIdentityViewModelTrustQrVerificationAndMuleStorage() = runBlocking {
        val identityVm = IdentityViewModel(repository)

        // Mule storage limit selector
        identityVm.setMaxStrangerMediaCacheMb(250)
        assertEquals(250, identityVm.maxStrangerMediaCacheMb.first())

        // In-Person Trust QR Verification
        val friendTrustPayload = TrustQrUtils.createTrustPayload(
            authorId = friendCrypto.authorId,
            displayName = "AliceFriend",
            crypto = friendCrypto
        )

        var verifySuccess = false
        var statusMsg = ""
        val job = identityVm.processScannedQr(friendTrustPayload) { success, msg ->
            verifySuccess = success
            statusMsg = msg
        }
        job.join()

        assertTrue(verifySuccess)
        assertTrue(statusMsg.contains("Verified AliceFriend in person!"))

        // Check database verified flag
        val isVerified = repository.isVerifiedInPerson(friendCrypto.authorId)
        assertTrue(isVerified)
    }

    @Test
    fun testSettingsViewModelMuleStorage() = runBlocking {
        val settingsVm = com.mysocial.mesh.ui.settings.SettingsViewModel(repository)
        settingsVm.setMaxStrangerMediaCacheMb(100)
        assertEquals(100, settingsVm.maxStrangerMediaCacheMb.first())
        settingsVm.setMaxStrangerMediaCacheMb(50)
        assertEquals(50, settingsVm.maxStrangerMediaCacheMb.first())
    }
}
