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

    fun notifyPeerNearby(context: Context, peerName: String? = null) {
        val now = System.currentTimeMillis()
        // Rate-limit notifications to at most once per 60 seconds to prevent alert floods
        if (now - lastNotifiedTimestamp < 60_000L) {
            return
        }
        lastNotifiedTimestamp = now

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nearby Friends & People",
                NotificationManager.IMPORTANCE_HIGH
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
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (!peerName.isNullOrBlank() && peerName != "Nearby Device" && peerName != "Nearby Friend") {
            "$peerName is nearby"
        } else {
            "Someone is nearby"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Someone Nearby")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_nearby_peer)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "Notified user: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
        }
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
}
