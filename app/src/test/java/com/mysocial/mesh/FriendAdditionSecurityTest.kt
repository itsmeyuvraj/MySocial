package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.settings.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
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
class FriendAdditionSecurityTest {

    private lateinit var db: MeshDatabase
    private lateinit var crypto: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine
    private lateinit var repository: MeshRepository
    private lateinit var settingsViewModel: SettingsViewModel
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
            myDisplayName = "SecurityTester",
            openMeshMode = true
        )
        repository = MeshRepository(
            db = db,
            cryptoManager = crypto,
            syncEngine = syncEngine
        )
        settingsViewModel = SettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testSettingsScan_withUrlPayload() = runBlocking {
        val deferred = CompletableDeferred<Pair<Boolean, String>>()

        settingsViewModel.followAuthorFromScan("mysocial://follow?authorId=@alice1234&name=Alice") { success, id ->
            deferred.complete(Pair(success, id))
        }

        val (callbackSuccess, callbackId) = deferred.await()
        assertTrue(callbackSuccess)
        assertEquals("@alice1234", callbackId)

        val followed = repository.observeFollowedAuthorIds().filter { it.contains("@alice1234") }.first()
        assertTrue(followed.contains("@alice1234"))
    }

    @Test
    fun testSettingsScan_withManualFriendCode() = runBlocking {
        val deferred = CompletableDeferred<Pair<Boolean, String>>()

        settingsViewModel.followAuthorFromScan("bob5678") { success, id ->
            deferred.complete(Pair(success, id))
        }

        val (callbackSuccess, callbackId) = deferred.await()
        assertTrue(callbackSuccess)
        assertEquals("@bob5678", callbackId)

        val followed = repository.observeFollowedAuthorIds().filter { it.contains("@bob5678") }.first()
        assertTrue(followed.contains("@bob5678"))
    }

    @Test
    fun testSettingsScan_invalidBlankCodeFails() = runBlocking {
        val deferred = CompletableDeferred<Pair<Boolean, String>>()

        settingsViewModel.followAuthorFromScan("   ") { success, msg ->
            deferred.complete(Pair(success, msg))
        }

        val (callbackSuccess, errorMessage) = deferred.await()
        assertFalse(callbackSuccess)
        assertEquals("Invalid QR code payload", errorMessage)
    }
}
