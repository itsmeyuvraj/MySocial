package com.mysocial.mesh

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.utils.PeerNotificationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeerNotificationHelperTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager
    private lateinit var appSettings: AppSettings

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = context as android.app.Application
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(notificationManager)
        notificationManager.cancelAll()
        PeerNotificationHelper.resetCooldownsForTesting()

        val prefs = context.getSharedPreferences("mysocial_user_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        AppSettings.resetInstanceForTesting()
        appSettings = AppSettings.getInstance(context)
        appSettings.setNearbyPeerNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun testAppSettingsNearbyNotificationsToggle() {
        assertTrue(appSettings.isNearbyPeerNotificationsEnabled.value)

        appSettings.setNearbyPeerNotificationsEnabled(false)
        assertFalse(appSettings.isNearbyPeerNotificationsEnabled.value)

        // Notification must be suppressed when disabled
        PeerNotificationHelper.notifyPeerNearby(context, "@testAuthorId12345.ed25519", "Alice")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        appSettings.setNearbyPeerNotificationsEnabled(true)
        assertTrue(appSettings.isNearbyPeerNotificationsEnabled.value)
    }

    @Test
    fun testRejectsInvalidAndSyntheticPeerIds() {
        // Direct IP bridge
        PeerNotificationHelper.notifyPeerNearby(context, "direct_10_0_2_2", "Host ADB Bridge")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // Localhost
        PeerNotificationHelper.notifyPeerNearby(context, "direct_127_0_0_1", "Nearby Peer")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // LoRa companion seed
        PeerNotificationHelper.notifyPeerNearby(context, "lora_node_1", "LoRa Seed (Heltec)")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // WiFi Aware synthetic
        PeerNotificationHelper.notifyPeerNearby(context, "WIFI_AWARE_123456", "Nearby NAN Peer")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // Raw Bluetooth MAC address
        PeerNotificationHelper.notifyPeerNearby(context, "AA:BB:CC:DD:EE:FF", "Nearby Friend")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // Bridge names even with dummy @
        PeerNotificationHelper.notifyPeerNearby(context, "@bridge_node.ed25519", "Phone ADB Bridge")
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // Non-@ ID
        PeerNotificationHelper.notifyPeerNearby(context, "some_random_id", "Bob")
        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun testValidPeerNotificationAndPerPeerCooldown() {
        val authorId = "@testGenuinePeerKeyForTesting123456789.ed25519"

        // First notification for this genuine author must post
        PeerNotificationHelper.notifyPeerNearby(context, authorId, "Charlie")
        val active = shadowNotificationManager.allNotifications
        assertEquals(1, active.size)
        val notif = active[0]
        assertEquals("Friend Nearby", notif.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("Charlie is nearby", notif.extras.getString(android.app.Notification.EXTRA_TEXT))

        // Second notification for the exact same authorId within 30 min cooldown must be ignored
        notificationManager.cancelAll()
        PeerNotificationHelper.notifyPeerNearby(context, authorId, "Charlie")
        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun testPlaceholderNameFormatting() {
        val authorId = "@testGenuinePeerWithoutName987654321.ed25519"

        // Peer with generic placeholder name
        PeerNotificationHelper.notifyPeerNearby(context, authorId, "Nearby Friend")
        val active = shadowNotificationManager.allNotifications
        assertEquals(1, active.size)
        val notif = active[0]
        assertEquals("Friend Nearby", notif.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("A friend joined your local mesh", notif.extras.getString(android.app.Notification.EXTRA_TEXT))
    }
}
