package com.mysocial.mesh.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mysocial.mesh.R
import com.mysocial.mesh.ui.MainActivity

object PeerNotificationHelper {
    private const val TAG = "PeerNotificationHelper"
    private const val CHANNEL_ID = "peer_nearby_channel"
    private const val NOTIFICATION_ID = 2002
    private const val DM_CHANNEL_ID = "direct_messages_channel"
    private const val DM_NOTIFICATION_ID_BASE = 3000
    private const val PTT_CHANNEL_ID = "ptt_transmissions_channel"
    private const val PTT_NOTIFICATION_ID = 2005

    @Volatile
    private var lastNotifiedTimestamp = 0L

    private val notifiedPeersCooldown = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val PEER_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes cooldown per peer
    private const val MIN_GLOBAL_INTERVAL_MS = 15_000L // 15 seconds global minimum between notifications

    fun resetCooldownsForTesting() {
        lastNotifiedTimestamp = 0L
        notifiedPeersCooldown.clear()
    }

    fun notifyPttVoiceBurst(context: Context, senderName: String, durationSec: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PTT_CHANNEL_ID,
                "Walkie-Talkie Transmissions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live Walkie-Talkie Push-To-Talk voice bursts from nearby peers"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 2)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2005,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PTT_CHANNEL_ID)
            .setContentTitle("📻 Walkie-Talkie: $senderName")
            .setContentText("Incoming voice burst ($durationSec)")
            .setSmallIcon(R.drawable.ic_stat_nearby_peer)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            manager.notify(PTT_NOTIFICATION_ID, notification)
            Log.i(TAG, "Notified user of incoming PTT voice burst from $senderName ($durationSec)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post PTT notification", e)
        }
    }

    fun notifyDirectMessage(context: Context, senderName: String, messageSnippet: String, peerId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DM_CHANNEL_ID,
                "Private Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for private end-to-end encrypted direct messages"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat_peer_id", peerId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            peerId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DM_CHANNEL_ID)
            .setContentTitle(senderName)
            .setContentText(messageSnippet)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            val notifId = DM_NOTIFICATION_ID_BASE + (peerId.hashCode() and 0x7FFF)
            manager.notify(notifId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post direct message notification", e)
        }
    }

    fun notifyGroupMessage(context: Context, groupName: String, senderName: String, messageSnippet: String, groupId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DM_CHANNEL_ID,
                "Private Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for private end-to-end encrypted direct and group messages"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_group_id", groupId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            groupId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DM_CHANNEL_ID)
            .setContentTitle("[$groupName] $senderName")
            .setContentText(messageSnippet)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            val notifId = DM_NOTIFICATION_ID_BASE + (groupId.hashCode() and 0x7FFF)
            manager.notify(notifId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post group message notification", e)
        }
    }

    fun notifyPeerNearby(context: Context, peerId: String, peerName: String? = null) {
        if (peerId.isBlank()) return

        // Check user preferences
        val settings = com.mysocial.mesh.data.AppSettings.getInstance(context)
        if (!settings.isNearbyPeerNotificationsEnabled.value) {
            return
        }

        // Filter out non-human, bridge, synthetic, or unverified IDs
        if (peerId.startsWith("direct_") ||
            peerId.startsWith("lora_") ||
            peerId.startsWith("WIFI_AWARE_") ||
            peerId.startsWith("bridge_") ||
            isMacAddress(peerId)
        ) {
            return
        }

        if (peerName?.contains("Bridge", ignoreCase = true) == true ||
            peerName?.contains("ADB", ignoreCase = true) == true
        ) {
            return
        }

        // Must be a genuine cryptographic author ID
        if (!peerId.startsWith("@")) {
            return
        }

        val now = System.currentTimeMillis()

        // Per-peer debounce: at most once every 30 minutes for this specific peer
        val lastPeerNotified = notifiedPeersCooldown[peerId] ?: 0L
        if (now - lastPeerNotified < PEER_COOLDOWN_MS) {
            return
        }

        // Global debounce: prevent rapid-fire alert floods
        if (now - lastNotifiedTimestamp < MIN_GLOBAL_INTERVAL_MS) {
            return
        }

        lastNotifiedTimestamp = now
        notifiedPeersCooldown[peerId] = now

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nearby Friends & People",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when someone is nearby"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot post notification: POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 1) // Switch to People Nearby / Radar tab
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            peerId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanName = peerName?.trim()
        val message = if (!cleanName.isNullOrBlank() &&
            cleanName != "Nearby Device" &&
            cleanName != "Nearby Peer" &&
            cleanName != "Nearby Friend" &&
            cleanName != "MeshPeer" &&
            cleanName != "User"
        ) {
            "$cleanName is nearby"
        } else {
            "A friend joined your local mesh"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Friend Nearby")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_nearby_peer)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            val notifId = NOTIFICATION_ID + (peerId.hashCode() and 0x7FFF)
            manager.notify(notifId, notification)
            Log.i(TAG, "Notified user: $message ($peerId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
        }
    }

    /**
     * Legacy backward-compatible signature. Suppressed to prevent ghost notifications without a verified peer ID.
     */
    fun notifyPeerNearby(context: Context, peerName: String? = null) {
        Log.d(TAG, "Legacy notifyPeerNearby called without peerId; suppressing ghost alert")
    }

    private fun isMacAddress(id: String): Boolean {
        return id.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
    }

    fun notifySosEmergency(context: Context, authorName: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val sosChannelId = "mysocial_sos_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                sosChannelId,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority emergency broadcast alerts from nearby peers"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 0)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, sosChannelId)
            .setContentTitle("🚨 URGENT SOS ALERT: $authorName")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_nearby_peer)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            manager.notify(9999, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post SOS notification", e)
        }
    }

    private const val CALL_CHANNEL_ID = "voice_calls_channel"
    private const val CALL_NOTIFICATION_ID = 4000

    fun notifyIncomingVoiceCall(context: Context, callerName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Voice Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming Direct BLE Voice Calls"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4000,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setContentTitle("📞 Incoming Direct Voice Call")
            .setContentText("$callerName is calling via BLE mesh...")
            .setSmallIcon(R.drawable.ic_stat_nearby_peer)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

        try {
            manager.notify(CALL_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post voice call notification", e)
        }
    }

    fun cancelIncomingVoiceCall(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            manager.cancel(CALL_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
