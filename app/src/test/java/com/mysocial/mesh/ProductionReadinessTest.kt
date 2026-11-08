package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.feed.FeedViewModel
import com.mysocial.mesh.ui.settings.SettingsViewModel
import com.mysocial.mesh.utils.BatteryOptimizationHelper
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProductionReadinessTest {

    private lateinit var context: Context
    private lateinit var appSettings: AppSettings
    private lateinit var repository: MeshRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("mysocial_user_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        AppSettings.resetInstanceForTesting()
        appSettings = AppSettings.getInstance(context)

        val db = MeshDatabase.buildInMemory(context)
        val crypto = CryptoManager.createInMemory(seed = ByteArray(32) { 101 })
        val syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = crypto,
            myDisplayName = "ProductionTester",
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
        AppSettings.resetInstanceForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun testOnboardingAndTermsStatePersistence() {
        // Initially, onboarding and terms are not completed
        assertFalse(appSettings.hasCompletedOnboarding.value)
        assertFalse(appSettings.hasAcceptedTerms.value)

        // Accept terms
        appSettings.setAcceptedTerms(true)
        assertTrue(appSettings.hasAcceptedTerms.value)

        // Complete onboarding
        appSettings.setCompletedOnboarding(true)
        assertTrue(appSettings.hasCompletedOnboarding.value)

        // Reset singleton and verify state is loaded from disk prefs
        AppSettings.resetInstanceForTesting()
        val reloadedSettings = AppSettings.getInstance(context)
        assertTrue(reloadedSettings.hasAcceptedTerms.value)
        assertTrue(reloadedSettings.hasCompletedOnboarding.value)
    }

    @Test
    fun testReportPostPersistenceAndStatus() {
        // Initially no reported posts
        assertTrue(appSettings.reportedPosts.value.isEmpty())
        assertFalse(appSettings.isPostReported("post_hash_alpha"))

        // Report post alpha
        appSettings.reportPost("post_hash_alpha")
        assertTrue(appSettings.isPostReported("post_hash_alpha"))
        assertEquals(setOf("post_hash_alpha"), appSettings.reportedPosts.value)

        // Report post beta
        appSettings.reportPost("post_hash_beta")
        assertTrue(appSettings.isPostReported("post_hash_beta"))
        assertEquals(2, appSettings.reportedPosts.value.size)

        // Reload from prefs
        AppSettings.resetInstanceForTesting()
        val reloadedSettings = AppSettings.getInstance(context)
        assertTrue(reloadedSettings.isPostReported("post_hash_alpha"))
        assertTrue(reloadedSettings.isPostReported("post_hash_beta"))
    }

    @Test
    fun testFeedViewModelHidesReportedPostsImmediately() = runBlocking {
        val feedViewModel = FeedViewModel(repository)

        // Publish two posts
        val post1Res = repository.createPost("Clean post for neighbors")
        val post2Res = repository.createPost("Objectionable spam message")
        assertTrue(post1Res.isSuccess)
        assertTrue(post2Res.isSuccess)

        val hash1 = post1Res.getOrThrow().entryHash
        val hash2 = post2Res.getOrThrow().entryHash

        // Collect initial feed posts
        val initialPosts = feedViewModel.feedPosts.first { it.size == 2 }
        assertEquals(2, initialPosts.size)

        // Report post 2
        feedViewModel.reportPost(entryHash = hash2, reason = "Spam", alsoBlockAuthor = false)

        // Filtered feed should immediately exclude post 2
        val updatedPosts = feedViewModel.feedPosts.first { it.size == 1 }
        assertEquals(1, updatedPosts.size)
        assertEquals(hash1, updatedPosts[0].post.entryHash)
        assertFalse(updatedPosts.any { it.post.entryHash == hash2 })
    }

    @Test
    fun testReportPostWithAlsoBlockAuthor() = runBlocking {
        val feedViewModel = FeedViewModel(repository)
        val remoteAuthor = "remote_author_bad_actor_9999"

        // Author is not blocked initially
        assertFalse(repository.isAuthorBlocked(remoteAuthor))

        // Report post and request author block
        feedViewModel.reportPost(
            entryHash = "bad_post_hash_777",
            reason = "Harassment",
            alsoBlockAuthor = true,
            authorId = remoteAuthor
        )

        // Post is reported
        assertTrue(appSettings.isPostReported("bad_post_hash_777"))

        // Author is now blocked (observe Flow to await Room IO write)
        val blockedList = repository.observeBlockedAuthors().first { list ->
            list.any { it.authorId == remoteAuthor }
        }
        assertTrue(blockedList.any { it.authorId == remoteAuthor })
        assertTrue(repository.isAuthorBlocked(remoteAuthor))
    }

    @Test
    fun testBatteryOptimizationHelperMethods() {
        // Verify helper methods run safely and return meaningful descriptions
        val title = BatteryOptimizationHelper.getStatusTitle(context)
        val desc = BatteryOptimizationHelper.getStatusDescription(context)
        assertNotNull(title)
        assertNotNull(desc)
        assertTrue(title.isNotBlank())
        assertTrue(desc.isNotBlank())
    }

    @Test
    fun testMySocialApplicationCrashDumpPersistence() {
        MySocialApplication.clearCrashDump(context)
        assertNull(MySocialApplication.getLatestCrashDump(context))

        val crashFile = File(context.filesDir, MySocialApplication.CRASH_DUMP_FILENAME)
        crashFile.writeText("MYSOCIAL FATAL CRASH REPORT\nException: NullPointerException\nMessage: Sample test crash")

        val dump = MySocialApplication.getLatestCrashDump(context)
        assertNotNull(dump)
        assertTrue(dump!!.contains("MYSOCIAL FATAL CRASH REPORT"))
        assertTrue(dump.contains("NullPointerException"))

        MySocialApplication.clearCrashDump(context)
        assertNull(MySocialApplication.getLatestCrashDump(context))
    }

    @Test
    fun testDiagnosticReportGenerationContainsMandatorySections() = runBlocking {
        val settingsViewModel = SettingsViewModel(repository)
        repository.logActivity("Unit test initialized diagnostic telemetry")

        val report = settingsViewModel.generateDiagnosticReport(context)
        assertNotNull(report)
        assertTrue(report.isNotBlank())

        // Verify mandatory sections
        assertTrue("Report must have header", report.contains("MYSOCIAL OFFLINE DIAGNOSTIC TELEMETRY"))
        assertTrue("Report must have app info", report.contains("1. APPLICATION & OS INFO:"))
        assertTrue("Report must have security info", report.contains("2. CRYPTOGRAPHIC IDENTITY & SECURITY:"))
        assertTrue("Report must have radio info", report.contains("3. HARDWARE RADIO CAPABILITIES:"))
        assertTrue("Report must have database metrics", report.contains("4. DATABASE & STORAGE METRICS:"))
        assertTrue("Report must have activity logs", report.contains("5. RECENT ACTIVITY LOGS"))
        assertTrue("Report must have crash status", report.contains("6. CRASH STATUS:"))
        assertTrue("Report must contain logged activity", report.contains("Unit test initialized diagnostic telemetry"))
    }
}

