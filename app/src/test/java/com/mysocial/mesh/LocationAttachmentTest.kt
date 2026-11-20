package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.LogProjector
import com.mysocial.mesh.data.LogValidator
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.data.ValidationResult
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.LocationAttachment
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.PostViewEntity
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.ui.composer.ComposerMode
import com.mysocial.mesh.ui.composer.ComposerViewModel
import com.mysocial.mesh.utils.LocationHelper
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationAttachmentTest {

    private lateinit var db: MeshDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        cryptoManager = CryptoManager.createInMemory(seed = ByteArray(32) { 42 })
        db.cryptoManager = cryptoManager
        syncEngine = GossipSyncEngine(db, cryptoManager, "TestNode", context = context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testLocationHelperFormatCoordinates() {
        val formattedNorthWest = LocationHelper.formatCoordinates(37.7749, -122.4194)
        assertEquals("37.7749° N, 122.4194° W", formattedNorthWest)

        val formattedSouthEast = LocationHelper.formatCoordinates(-33.8688, 151.2093)
        assertEquals("33.8688° S, 151.2093° E", formattedSouthEast)

        val formattedZero = LocationHelper.formatCoordinates(0.0, 0.0)
        assertEquals("0.0000° N, 0.0000° E", formattedZero)
    }

    @Test
    fun testPostPayloadSerializationRoundTripWithLocation() {
        val loc = LocationAttachment(
            name = "Central Park, NY",
            latitude = 40.785091,
            longitude = -73.968285
        )
        val payload = PostPayload(
            text = "Enjoying the fresh air in the park!",
            tags = listOf("park", "nyc"),
            location = loc
        )

        val json = payload.toJson()
        val decoded = PostPayload.fromJson(json)

        assertEquals(payload.text, decoded.text)
        assertNotNull(decoded.location)
        assertEquals("Central Park, NY", decoded.location?.name)
        assertEquals(40.785091, decoded.location?.latitude ?: 0.0, 0.00001)
        assertEquals(-73.968285, decoded.location?.longitude ?: 0.0, 0.00001)
    }

    @Test
    fun testPostPayloadSerializationWithNameOnly() {
        val loc = LocationAttachment(name = "Downtown Coffee Lab")
        val payload = PostPayload(
            text = "Coding offline over peer-to-peer mesh",
            location = loc
        )

        val json = payload.toJson()
        val decoded = PostPayload.fromJson(json)

        assertNotNull(decoded.location)
        assertEquals("Downtown Coffee Lab", decoded.location?.name)
        assertNull(decoded.location?.latitude)
        assertNull(decoded.location?.longitude)
    }

    @Test
    fun testPostPayloadBackwardsCompatibility() {
        // Raw JSON without location field from earlier protocol builds
        val legacyJson = """{"text":"Old post without location field","tags":[],"media":null,"isPublic":true,"expiresAt":null,"isSos":false,"pollOptions":null}"""
        val decoded = PostPayload.fromJson(legacyJson)

        assertEquals("Old post without location field", decoded.text)
        assertNull(decoded.location)
    }

    @Test
    fun testDatabaseSavesAndQueriesPostWithLocation() = runBlocking {
        val post = PostViewEntity(
            entryHash = "hash_loc_post_1",
            authorId = cryptoManager.authorId,
            text = "At the summit!",
            timestamp = System.currentTimeMillis(),
            replyToHash = null,
            rootHash = null,
            locationName = "Mount Rainier Base Camp",
            latitude = 46.8523,
            longitude = -121.7603
        )

        db.postViewDao().upsert(post)

        val retrieved = db.postViewDao().getPost("hash_loc_post_1")
        assertNotNull(retrieved)
        assertEquals("Mount Rainier Base Camp", retrieved?.locationName)
        assertEquals(46.8523, retrieved?.latitude ?: 0.0, 0.0001)
        assertEquals(-121.7603, retrieved?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun testGossipSyncEnginePublishesAndProjectsPostWithLocation() = runBlocking {
        val loc = LocationAttachment(
            name = "Emergency Shelter #4",
            latitude = 34.0522,
            longitude = -118.2437
        )
        val payload = PostPayload(
            text = "Emergency food and water distributed here",
            isSos = true,
            location = loc
        ).toJson()

        val result = syncEngine.createAndPublishLocalEntry(ContentType.POST, payload)
        assertTrue("Entry publishing should succeed", result.isSuccess)

        val published = result.getOrNull()
        assertNotNull(published)

        val post = db.postViewDao().getPost(published!!.entryHash)
        assertNotNull("Projected post must exist in posts_view", post)
        assertEquals("Emergency Shelter #4", post?.locationName)
        assertEquals(34.0522, post?.latitude ?: 0.0, 0.0001)
        assertEquals(-118.2437, post?.longitude ?: 0.0, 0.0001)
        assertTrue(post?.isSos == true)
    }

    @Test
    fun testPostValidationWithLocationOnly() = runBlocking {
        val locationOnlyPayload = PostPayload(
            text = "",
            location = LocationAttachment(name = "Central Square")
        ).toJson()

        val result = syncEngine.createAndPublishLocalEntry(ContentType.POST, locationOnlyPayload)
        assertTrue("Publishing post with location-only content should be valid", result.isSuccess)

        val published = result.getOrNull()
        assertNotNull(published)
        val post = db.postViewDao().getPost(published!!.entryHash)
        assertNotNull(post)
        assertEquals("Central Square", post?.locationName)
    }

    @Test
    fun testComposerViewModelLocationState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appSettings = AppSettings.getInstance(context)
        val repository = MeshRepository(db, cryptoManager, syncEngine, appSettings)
        val vm = ComposerViewModel(repository)

        // Initially null
        assertNull(vm.locationAttachment.value)
        assertFalse(vm.isAcquiringLocation.value)

        // setLocation updates state
        vm.setLocation("Golden Gate Bridge", 37.8199, -122.4783)
        assertNotNull(vm.locationAttachment.value)
        assertEquals("Golden Gate Bridge", vm.locationAttachment.value?.name)
        assertEquals(37.8199, vm.locationAttachment.value?.latitude ?: 0.0, 0.0001)
        assertEquals(-122.4783, vm.locationAttachment.value?.longitude ?: 0.0, 0.0001)

        // clearLocation removes location
        vm.clearLocation()
        assertNull(vm.locationAttachment.value)
        assertFalse(vm.isAcquiringLocation.value)

        // resetForMode resets location
        vm.setLocation("Muir Woods", 37.8970, -122.5811)
        assertEquals("Muir Woods", vm.locationAttachment.value?.name)
        vm.resetForMode(ComposerMode.POST)
        assertNull(vm.locationAttachment.value)
        assertFalse(vm.isAcquiringLocation.value)
    }

    @Test
    fun testLocationHelperPermissionChecks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hasPermission = LocationHelper.hasLocationPermission(context)
        val hasFine = LocationHelper.hasFineLocationPermission(context)
        assertEquals(hasPermission, hasFine)
    }

    @Test
    fun testIncomingSosPostTriggersNotification() = runBlocking {
        val senderCrypto = CryptoManager.createInMemory(seed = ByteArray(32) { 99 })
        val loc = LocationAttachment("Camp 4", 37.742, -119.603)
        val payload = PostPayload(
            text = "Need medical assistance near the rocks",
            isSos = true,
            location = loc
        ).toJson()

        val now = System.currentTimeMillis()
        val canonical = CryptoManager.buildCanonicalPayload(
            authorId = senderCrypto.authorId,
            sequence = 1L,
            previousHash = null,
            timestamp = now,
            contentType = ContentType.POST.name,
            contentJson = payload
        )
        val sig = senderCrypto.sign(canonical)
        val hash = CryptoManager.computeEntryHash(
            authorId = senderCrypto.authorId,
            sequence = 1L,
            previousHash = null,
            timestamp = now,
            contentType = ContentType.POST.name,
            contentJson = payload,
            signature = sig
        )

        val entry = LogEntry(
            authorId = senderCrypto.authorId,
            sequence = 1L,
            previousHash = null,
            timestamp = now,
            contentType = ContentType.POST,
            contentJson = payload,
            signature = sig,
            entryHash = hash
        )

        val result = db.insertAndProjectBatch(listOf(entry))
        assertEquals(1, result.acceptedCount)

        val projected = db.postViewDao().getPost(hash)
        assertNotNull(projected)
        assertTrue(projected?.isSos == true)
        assertEquals("Camp 4", projected?.locationName)
        assertEquals(37.742, projected?.latitude ?: 0.0, 0.001)
        assertEquals(-119.603, projected?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun testCompassDirectionFinderDistanceCalculationAndFormatting() {
        // Distance from Mt. Everest base to summit (~5.5 km)
        val lat1 = 28.0026
        val lon1 = 86.8528
        val lat2 = 27.9881
        val lon2 = 86.9250
        val distMeters = com.mysocial.mesh.emergency.CompassDirectionFinder.calculateDistanceMeters(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be greater than 7000m", distMeters > 7000.0)
        assertTrue("Distance should be less than 8500m", distMeters < 8500.0)

        // Test formatting
        val formattedMeters = com.mysocial.mesh.emergency.CompassDirectionFinder.formatDistance(450.0)
        assertEquals("450 m", formattedMeters)

        val formattedKm = com.mysocial.mesh.emergency.CompassDirectionFinder.formatDistance(7350.0)
        assertEquals("7.4 km", formattedKm)
    }

    @Test
    fun testFormatCoordinatesWithAltitude() {
        val withAlt = LocationHelper.formatCoordinates(47.6062, -122.3321, 142.6)
        assertEquals("47.6062° N, 122.3321° W (Alt: 142m)", withAlt)

        val withoutAlt = LocationHelper.formatCoordinates(47.6062, -122.3321, null)
        assertEquals("47.6062° N, 122.3321° W", withoutAlt)
    }

    @Test
    fun testLocationServicesAndClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Verify isLocationServicesEnabled runs safely without exceptions
        val enabled = LocationHelper.isLocationServicesEnabled(context)
        // Verify copyCoordinates copies to system clipboard
        LocationHelper.copyCoordinates(context, 37.7749, -122.4194)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        assertNotNull(clipboard?.primaryClip)
        assertEquals("37.7749, -122.4194", clipboard?.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
