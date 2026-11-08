package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.identity.IdentityViewModel
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
    private lateinit var identityViewModel: IdentityViewModel
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
        identityViewModel = IdentityViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testQrScan_withUrlPayload() = runBlocking {
        val deferred = CompletableDeferred<Pair<Boolean, String>>()

        val job = identityViewModel.processScannedQr("mysocial://follow?authorId=@alice1234&name=Alice") { success, msg ->
            deferred.complete(Pair(success, msg))
        }
        job.join()

        val (callbackSuccess, callbackMsg) = deferred.await()
        assertTrue(callbackSuccess)
        assertTrue(callbackMsg.contains("Alice") || callbackMsg.contains("@alice1234"))

        val followed = repository.observeFollowedAuthorIds().filter { it.contains("@alice1234") }.first()
        assertTrue(followed.contains("@alice1234"))
    }

    @Test
    fun testQrScan_withManualFriendCode() = runBlocking {
        val deferred = CompletableDeferred<Pair<Boolean, String>>()

        val job = identityViewModel.processScannedQr("bob5678") { success, msg ->
            deferred.complete(Pair(success, msg))
        }
        job.join()

        val (callbackSuccess, callbackMsg) = deferred.await()
        assertTrue(callbackSuccess)
        assertEquals("Added @bob5678 as friend", callbackMsg)

        val followed = repository.observeFollowedAuthorIds().filter { it.contains("@bob5678") }.first()
        assertTrue(followed.contains("@bob5678"))
    }

    @Test
    fun testQrScan_invalidBlankCodeFails() = runBlocking {
        val deferred = CompletableDeferred<Pair<Boolean, String>>()

        val job = identityViewModel.processScannedQr("   ") { success, msg ->
            deferred.complete(Pair(success, msg))
        }
        job.join()

        val (callbackSuccess, errorMessage) = deferred.await()
        assertFalse(callbackSuccess)
        assertEquals("Unrecognized QR code", errorMessage)
    }
}
