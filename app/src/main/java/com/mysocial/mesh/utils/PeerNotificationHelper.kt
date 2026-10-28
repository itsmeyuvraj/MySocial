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
import com.mysocial.mesh.ui.MainActivity

object PeerNotificationHelper {
    private const val TAG = "PeerNotificationHelper"
    private const val CHANNEL_ID = "peer_nearby_channel"
    private const val NOTIFICATION_ID = 2002

    @Volatile
    private var lastNotifiedTimestamp = 0L

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
                "Nearby Peer Discovery",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a peer is discovered nearby"
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

        val message = if (!peerName.isNullOrBlank() && peerName != "Nearby Device") {
            "$peerName is nearby"
        } else {
            "A peer is nearby"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Peer Nearby")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
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
}
