package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.call.ActiveCallSession
import com.mysocial.mesh.call.BleVoiceCallManager
import com.mysocial.mesh.call.CallState
import com.mysocial.mesh.call.VoiceCodec
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.E2eeManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.FollowViewEntity
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.model.VoiceCallSignalPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleVoiceCallTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var aliceDb: MeshDatabase
    private lateinit var aliceCrypto: CryptoManager
    private lateinit var bobCrypto: CryptoManager
    private lateinit var malloryCrypto: CryptoManager
    private lateinit var aliceSyncEngine: GossipSyncEngine
    private lateinit var aliceCallManager: BleVoiceCallManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        aliceDb = MeshDatabase.buildInMemory(context)
        aliceCrypto = CryptoManager.createInMemory(seed = ByteArray(32) { 1 })
        bobCrypto = CryptoManager.createInMemory(seed = ByteArray(32) { 2 })
        malloryCrypto = CryptoManager.createInMemory(seed = ByteArray(32) { 9 })

        aliceSyncEngine = GossipSyncEngine(
            db = aliceDb,
            cryptoManager = aliceCrypto,
            myDisplayName = "Alice",
            openMeshMode = true
        )

        aliceCallManager = BleVoiceCallManager(
            context = context,
            cryptoManager = aliceCrypto,
            syncEngine = aliceSyncEngine,
            db = aliceDb,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + testDispatcher)
        )
    }

    @After
    fun tearDown() {
        aliceCallManager.release()
        aliceDb.close()
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------------------------
    // 1. VoiceCodec IMA-ADPCM Codec Fidelity & Compression Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testAdpcmFrameCompressionRatio() {
        assertEquals(320, VoiceCodec.SAMPLES_PER_FRAME)
        assertEquals(640, VoiceCodec.RAW_FRAME_BYTES)
        assertEquals(164, VoiceCodec.ENCODED_FRAME_BYTES)

        val rawPcm = ByteArray(VoiceCodec.RAW_FRAME_BYTES) { (it % 128).toByte() }
        val encoded = VoiceCodec.encodeFrame(rawPcm)
        assertEquals("Encoded frame must be 164 bytes (4 bytes state + 160 bytes nibbles)", 164, encoded.size)

        val decoded = VoiceCodec.decodeFrame(encoded)
        assertEquals("Decoded frame must restore exactly 640 PCM bytes", 640, decoded.size)
    }

    @Test
    fun testSilenceEncodingAndDecoding() {
        val silence = ByteArray(VoiceCodec.RAW_FRAME_BYTES) { 0 }
        val encoded = VoiceCodec.encodeFrame(silence)
        val decoded = VoiceCodec.decodeFrame(encoded)

        val bb = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until VoiceCodec.SAMPLES_PER_FRAME) {
            val sample = bb.short.toInt()
            assertTrue("Decoded silence samples must remain near zero (got $sample)", kotlin.math.abs(sample) <= 8)
        }
    }

    @Test
    fun testSineWaveSignalFidelity() {
        // Generate a 440 Hz sine wave @ 16 kHz sampling rate (20ms = 320 samples)
        val rawPcm = ByteArray(VoiceCodec.RAW_FRAME_BYTES)
        val sampleBuffer = ShortArray(VoiceCodec.SAMPLES_PER_FRAME)
        val bb = ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN)

        val sampleRate = 16000.0
        val frequency = 440.0
        val amplitude = 12000.0

        for (i in 0 until VoiceCodec.SAMPLES_PER_FRAME) {
            val value = (amplitude * sin(2.0 * Math.PI * frequency * (i / sampleRate))).toInt().toShort()
            sampleBuffer[i] = value
            bb.putShort(value)
        }

        // Encode and decode
        val encoded = VoiceCodec.encodeFrame(rawPcm)
        val decoded = VoiceCodec.decodeFrame(encoded)

        val decodedBb = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
        var sumSquaredError = 0.0
        var sumSignalPower = 0.0

        for (i in 0 until VoiceCodec.SAMPLES_PER_FRAME) {
            val original = sampleBuffer[i].toDouble()
            val reconstructed = decodedBb.short.toDouble()
            val error = original - reconstructed
            sumSquaredError += error * error
            sumSignalPower += original * original
        }

        val rmse = sqrt(sumSquaredError / VoiceCodec.SAMPLES_PER_FRAME)
        val snrDb = 10.0 * kotlin.math.log10(sumSignalPower / (sumSquaredError + 1e-9))

        // Standard 4-bit IMA-ADPCM on tone provides SNR > 15 dB
        assertTrue("RMSE must be within acceptable perceptual bounds for speech (got $rmse)", rmse < 2500)
        assertTrue("SNR must exceed 15 dB for a clean 440Hz test tone (got $snrDb dB)", snrDb > 15.0)
    }

    @Test
    fun testPartialBufferGracefulHandling() {
        val tinyBuffer = ByteArray(100)
        val encoded = VoiceCodec.encodeFrame(tinyBuffer)
        assertEquals(VoiceCodec.ENCODED_FRAME_BYTES, encoded.size)

        val tinyEncoded = ByteArray(50)
        val decoded = VoiceCodec.decodeFrame(tinyEncoded)
        assertEquals(VoiceCodec.RAW_FRAME_BYTES, decoded.size)
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Cryptographic E2EE Voice Frame Encryption & Sequence Security
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testX25519SessionKeyDerivationMatch() {
        val callId = "test-call-session-999"

        val bobSyncEngine = GossipSyncEngine(
            db = aliceDb,
            cryptoManager = bobCrypto,
            myDisplayName = "Bob",
            openMeshMode = true
        )
        val bobCallManager = BleVoiceCallManager(
            context = context,
            cryptoManager = bobCrypto,
            syncEngine = bobSyncEngine,
            db = aliceDb
        )

        try {
            aliceCallManager.deriveSessionKey(callId, bobCrypto.authorId)
            bobCallManager.deriveSessionKey(callId, aliceCrypto.authorId)

            val aliceKey = aliceCallManager.getSessionAesKey()
            val bobKey = bobCallManager.getSessionAesKey()

            assertNotNull("Alice must derive session key", aliceKey)
            assertNotNull("Bob must derive session key", bobKey)
            assertArrayEquals("Both peers must derive identical AES-256 session keys via ECDH", aliceKey!!.encoded, bobKey!!.encoded)
        } finally {
            bobCallManager.release()
        }
    }

    @Test
    fun testAudioFrameEncryptionDecryptionRoundtrip() {
        val callId = "e2ee-audio-call"
        aliceCallManager.deriveSessionKey(callId, bobCrypto.authorId)

        val sampleAdpcm = ByteArray(VoiceCodec.ENCODED_FRAME_BYTES) { (it * 7 % 256).toByte() }
        val sequence = 42L

        val encrypted = aliceCallManager.encryptFrame(sampleAdpcm, sequence)
        assertTrue("Encrypted frame must contain 12-byte IV + ciphertext + 16-byte tag", encrypted.size > sampleAdpcm.size + 12)

        val decrypted = aliceCallManager.decryptFrame(encrypted)
        assertNotNull("Decryption must succeed with matching session key", decrypted)
        assertArrayEquals("Decrypted frame must perfectly match original ADPCM payload", sampleAdpcm, decrypted)
    }

    @Test
    fun testSequenceReplayResistance() {
        val callId = "replay-test-call"
        aliceCallManager.deriveSessionKey(callId, bobCrypto.authorId)

        val sampleAdpcm = ByteArray(VoiceCodec.ENCODED_FRAME_BYTES) { 42 }

        val frame1 = aliceCallManager.encryptFrame(sampleAdpcm, 1L)
        val frame2 = aliceCallManager.encryptFrame(sampleAdpcm, 2L)

        // Frame IVs and ciphertexts must differ because sequence numbers increment the 12-byte IV
        assertFalse("Different sequences must produce distinct encrypted frames", frame1.contentEquals(frame2))
    }

    @Test
    fun testTamperedAudioFrameRejection() {
        val callId = "tamper-test-call"
        aliceCallManager.deriveSessionKey(callId, bobCrypto.authorId)

        val sampleAdpcm = ByteArray(VoiceCodec.ENCODED_FRAME_BYTES) { 77 }
        val encrypted = aliceCallManager.encryptFrame(sampleAdpcm, 10L)

        // Flip one byte in the ciphertext portion
        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        val decrypted = aliceCallManager.decryptFrame(tampered)
        assertNull("Tampered frame must fail AES-GCM MAC validation and return null", decrypted)
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Access Control (Verified Friend Gate) Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testRejectIncomingCallFromStranger() = runBlocking {
        // Mallory is a stranger (not followed, not QR verified)
        val isVerified = aliceCallManager.isVerifiedFriend(malloryCrypto.authorId)
        assertFalse("Stranger must not be considered a verified friend", isVerified)

        val inviteSignal = VoiceCallSignalPayload(
            callId = "call-from-stranger",
            signalType = "INVITE",
            callerAuthorId = malloryCrypto.authorId,
            callerDisplayName = "Mallory",
            recipientAuthorId = aliceCrypto.authorId,
            l2capPsm = 128
        )

        aliceCallManager.handleCallSignal(inviteSignal).join()

        // Assert stranger call was rejected and Alice is NOT ringing
        assertNull("Incoming session must remain null when called by a stranger", aliceCallManager.incomingSession.value)
    }

    @Test
    fun testAllowIncomingCallFromFollowedFriend() = runBlocking {
        // Alice follows Bob
        aliceDb.followDao().upsert(
            FollowViewEntity(
                followerId = aliceCrypto.authorId,
                followeeId = bobCrypto.authorId,
                isFollowing = true,
                updatedAtSeq = 1L,
                isVerifiedInPerson = false
            )
        )

        val isVerified = aliceCallManager.isVerifiedFriend(bobCrypto.authorId)
        assertTrue("Followed peer must be considered a verified friend", isVerified)

        val inviteSignal = VoiceCallSignalPayload(
            callId = "call-from-bob",
            signalType = "INVITE",
            callerAuthorId = bobCrypto.authorId,
            callerDisplayName = "Bob",
            recipientAuthorId = aliceCrypto.authorId,
            l2capPsm = 129
        )

        aliceCallManager.handleCallSignal(inviteSignal).join()

        // Assert incoming ringing session is active
        val incoming = aliceCallManager.incomingSession.value
        assertNotNull("Incoming session must be populated for verified friend", incoming)
        assertEquals(CallState.RINGING, incoming?.state)
        assertEquals(bobCrypto.authorId, incoming?.peerAuthorId)
        assertEquals("Bob", incoming?.peerDisplayName)
        assertEquals("call-from-bob", incoming?.callId)
    }

    @Test
    fun testAllowIncomingCallFromQrVerifiedFriend() = runBlocking {
        // Peer verified in person via QR code
        aliceDb.followDao().upsert(
            FollowViewEntity(
                followerId = aliceCrypto.authorId,
                followeeId = bobCrypto.authorId,
                isFollowing = false,
                updatedAtSeq = 2L,
                isVerifiedInPerson = true
            )
        )

        val isVerified = aliceCallManager.isVerifiedFriend(bobCrypto.authorId)
        assertTrue("In-person QR verified peer must be allowed to voice call", isVerified)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Call State Machine Transitions Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testStartCallAndEndCallFlow() = runBlocking {
        // Add Bob as friend
        aliceDb.followDao().upsert(
            FollowViewEntity(
                followerId = aliceCrypto.authorId,
                followeeId = bobCrypto.authorId,
                isFollowing = true,
                updatedAtSeq = 1L
            )
        )

        aliceCallManager.startCall(bobCrypto.authorId, "Bob").join()

        val active = aliceCallManager.activeSession.value
        assertNotNull("Active session must exist after startCall", active)
        assertEquals(CallState.DIALING, active?.state)
        assertTrue(active?.isCaller == true)
        assertEquals(bobCrypto.authorId, active?.peerAuthorId)

        // End call
        aliceCallManager.endCall()?.join()
        assertNull("Active session must be cleared after endCall", aliceCallManager.activeSession.value)
        assertEquals(0, aliceCallManager.callDurationSec.value)
    }

    @Test
    fun testAcceptCallTransition() = runBlocking {
        // Setup friend
        aliceDb.followDao().upsert(
            FollowViewEntity(
                followerId = aliceCrypto.authorId,
                followeeId = bobCrypto.authorId,
                isFollowing = true,
                updatedAtSeq = 1L
            )
        )

        val inviteSignal = VoiceCallSignalPayload(
            callId = "call-accept-test",
            signalType = "INVITE",
            callerAuthorId = bobCrypto.authorId,
            callerDisplayName = "Bob",
            recipientAuthorId = aliceCrypto.authorId,
            l2capPsm = 130
        )
        aliceCallManager.handleCallSignal(inviteSignal).join()
        assertEquals(CallState.RINGING, aliceCallManager.incomingSession.value?.state)

        // Accept call
        aliceCallManager.acceptCall()?.join()

        assertNull("Incoming session must be cleared upon accept", aliceCallManager.incomingSession.value)
        val active = aliceCallManager.activeSession.value
        assertNotNull("Active session must be set upon accept", active)
        assertEquals(CallState.CONNECTING, active?.state)
    }

    @Test
    fun testToggleMuteAndSpeakerphone() {
        assertFalse(aliceCallManager.isMuted.value)
        aliceCallManager.toggleMute()
        assertTrue(aliceCallManager.isMuted.value)
        aliceCallManager.toggleMute()
        assertFalse(aliceCallManager.isMuted.value)

        assertFalse(aliceCallManager.isSpeakerphone.value)
        aliceCallManager.toggleSpeakerphone()
        aliceCallManager.toggleSpeakerphone()
    }
}
