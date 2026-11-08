package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.emergency.CompassDirectionFinder
import com.mysocial.mesh.emergency.EmergencyChannels
import com.mysocial.mesh.group.GroupManager
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.GroupEntity
import com.mysocial.mesh.model.PttVoiceBurstPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase2GroupAndEmergencyTest {

    private lateinit var context: Context
    private lateinit var db: MeshDatabase
    private lateinit var cryptoA: CryptoManager
    private lateinit var engineA: GossipSyncEngine
    private lateinit var groupManager: GroupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = MeshDatabase.buildInMemory(context)
        cryptoA = CryptoManager.createInMemory(seed = ByteArray(32) { 77 })
        db.cryptoManager = cryptoA

        engineA = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoA,
            myDisplayName = "Alice",
            context = context
        )
        groupManager = GroupManager(db, cryptoA, engineA)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGroupCreationAndInviteUriParsing() = runBlocking {
        val group = groupManager.createGroup(name = "Disaster Relief Alpha", description = "Medical triage team")
        assertNotNull(group.groupId)
        assertEquals("Disaster Relief Alpha", group.groupName)
        assertEquals(cryptoA.authorId, group.creatorAuthorId)
        assertTrue(group.senderKeyBase64.isNotBlank())

        val members = groupManager.observeGroupMembers(group.groupId).first()
        assertEquals(1, members.size)
        assertEquals("ADMIN", members[0].role)

        // Generate invite URI
        val inviteUri = groupManager.generateInviteUri(group)
        assertTrue(inviteUri.startsWith("mysocial://group_invite?"))
        assertTrue(inviteUri.contains("gid=${group.groupId}"))

        // Remote joins group from invite URI
        val joined = groupManager.joinGroupFromInvite(inviteUri, myDisplayName = "Bob Responder")
        assertNotNull(joined)
        assertEquals(group.groupId, joined?.groupId)
        assertEquals(group.groupName, joined?.groupName)
        assertEquals(group.senderKeyBase64, joined?.senderKeyBase64)
    }

    @Test
    fun testGroupMessageEncryptionAndTransmission() = runBlocking {
        val group = groupManager.createGroup("Security Ops")

        val result = groupManager.sendGroupMessage(
            groupId = group.groupId,
            text = "Team, rendezvous at North Gate",
            myDisplayName = "Alice"
        )
        assertTrue(result.isSuccess)
        val msgEntity = result.getOrNull()
        assertNotNull(msgEntity)
        assertEquals("Team, rendezvous at North Gate", msgEntity?.plaintextSnippet)
        assertTrue(msgEntity?.ciphertextBase64?.isNotBlank() == true)

        val stored = groupManager.observeGroupMessages(group.groupId).first()
        assertEquals(1, stored.size)
        assertEquals(msgEntity?.messageId, stored[0].messageId)
        assertEquals("Team, rendezvous at North Gate", stored[0].plaintextSnippet)
    }

    @Test
    fun testEmergencyChannelsDefinitions() {
        val channels = EmergencyChannels.CHANNELS
        assertTrue(channels.isNotEmpty())
        assertTrue(channels.any { it.tag == "#emergency-sos" })
        assertTrue(channels.any { it.tag == "#medical-aid" })
        assertTrue(channels.any { it.tag == "#shelter-updates" })
        assertTrue(channels.any { it.tag == "#food-and-water" })
        assertTrue(channels.any { it.tag == "#lost-and-found" })
        assertTrue(channels.any { it.tag == "#power-and-fuel" })
    }

    @Test
    fun testCompassBearingAndRelativeAngleCalculations() {
        // Test bearing: London (51.5074, -0.1278) to Paris (48.8566, 2.3522) ~ 148 degrees
        val bearing = CompassDirectionFinder.calculateBearing(51.5074, -0.1278, 48.8566, 2.3522)
        assertTrue("Bearing should be approx 148 deg, was $bearing", bearing in 140f..155f)

        // Relative angle: device facing 100 deg, target is 150 deg -> bear right 50 deg
        val relRight = CompassDirectionFinder.calculateRelativeBearing(100f, 150f)
        assertEquals(50f, relRight, 0.01f)

        // Relative angle: device facing 180 deg, target is 90 deg -> bear left -90 deg
        val relLeft = CompassDirectionFinder.calculateRelativeBearing(180f, 90f)
        assertEquals(-90f, relLeft, 0.01f)

        // Relative angle wrap around: device facing 10 deg, target is 350 deg -> -20 deg
        val relWrap = CompassDirectionFinder.calculateRelativeBearing(10f, 350f)
        assertEquals(-20f, relWrap, 0.01f)

        // RSSI distance estimation: -59 dBm ~ 1 meter
        val dist1m = CompassDirectionFinder.estimateDistanceMeters(-59)
        assertEquals(1.0, dist1m, 0.05)

        // Weaker RSSI ~ greater distance
        val distFar = CompassDirectionFinder.estimateDistanceMeters(-85)
        assertTrue(distFar > 5.0)
    }

    @Test
    fun testPttVoiceBurstSerialization() {
        val payload = PttVoiceBurstPayload(
            burstId = "burst_123",
            senderAuthorId = "author_456",
            senderDisplayName = "Medic 1",
            channel = "#medical-aid",
            durationMs = 1500L,
            audioBase64 = "AQIDBAU="
        )
        val json = payload.toJson()
        val parsed = PttVoiceBurstPayload.fromJson(json)
        assertEquals("burst_123", parsed.burstId)
        assertEquals("Medic 1", parsed.senderDisplayName)
        assertEquals("#medical-aid", parsed.channel)
        assertEquals(1500L, parsed.durationMs)
        assertEquals("AQIDBAU=", parsed.audioBase64)
    }

    @Test
    fun testPttWalkieTalkieManagerBroadcastAndReceive() = runBlocking {
        var onPublishedCalled = false
        val pttManager = com.mysocial.mesh.emergency.PttWalkieTalkieManager(
            context = context,
            cryptoManager = cryptoA,
            syncEngine = engineA
        ) {
            onPublishedCalled = true
        }

        var receivedBurst: PttVoiceBurstPayload? = null
        db.onPttVoiceBurstReceived = { burst ->
            receivedBurst = burst
        }

        pttManager.setChannel(EmergencyChannels.DEFAULT_CHANNEL)
        assertEquals(EmergencyChannels.DEFAULT_CHANNEL, pttManager.selectedChannel.value)

        // Start recording
        pttManager.startRecording()
        assertTrue(pttManager.isRecording.value)
        kotlinx.coroutines.delay(250)

        // Stop and broadcast
        val broadcastResult = pttManager.stopAndBroadcast(displayName = "Alice")
        assertTrue("Broadcast should succeed", broadcastResult.isSuccess)
        val payload = broadcastResult.getOrThrow()
        assertEquals("Alice", payload.senderDisplayName)
        assertEquals(cryptoA.authorId, payload.senderAuthorId)
        assertEquals(EmergencyChannels.DEFAULT_CHANNEL, payload.channel)
        assertTrue(onPublishedCalled)

        // Verify the database has the log entry
        val entries = db.logEntryDao().getEntriesForAuthorAfter(cryptoA.authorId, 0L)
        val pttEntry = entries.find { it.contentType == ContentType.PTT_VOICE_BURST }
        assertNotNull("Database must contain PTT_VOICE_BURST entry", pttEntry)

        // Verify playback state
        pttManager.playVoiceBurst(payload)
        assertEquals(payload.burstId, pttManager.lastReceivedBurst.value?.burstId)
    }

    @Test
    fun testPttAudioAmplificationAndChirp() {
        // Create quiet 16-bit PCM (sample value = 1000)
        val quietPcm = ByteArray(200)
        for (i in 0 until 100) {
            val sample = 1000.toShort()
            quietPcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            quietPcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val amplified = com.mysocial.mesh.emergency.PttWalkieTalkieManager.amplifyPcm(quietPcm)
        val firstSample = ((amplified[1].toInt() shl 8) or (amplified[0].toInt() and 0xFF)).toShort()

        assertTrue("Amplified sample should be significantly louder than 1000, was $firstSample", firstSample > 2500)

        // Chirp generation
        val chirp = com.mysocial.mesh.emergency.PttWalkieTalkieManager.generateRadioChirp()
        assertTrue("Chirp must have audio bytes", chirp.isNotEmpty())
    }
}
