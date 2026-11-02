package com.mysocial.mesh.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mysocial.mesh.R
import com.mysocial.mesh.utils.OfflineApkServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Metadata for an APK update received from a peer over the mesh network.
 */
data class PeerUpdateInfo(
    val peerId: String,
    val peerName: String,
    val remoteVersionCode: Long,
    val remoteVersionName: String,
    val apkFile: File,
    val fileSizeBytes: Long = 0L,
    val receivedAt: Long = System.currentTimeMillis()
)

/**
 * Manages zero-internet P2P APK auto-updates across the mesh network:
 * 1. Automatically hosts the installed base APK using OfflineApkServer so peers can update from us.
 * 2. When a connected peer advertises a higher buildNumber in their handshake, automatically downloads the APK over Wi-Fi/TCP.
 * 3. Cryptographically verifies package name, version, and developer signing certificate.
 * 4. Alerts the user via system notification and in-app banner for a seamless 1-tap installation.
 */
class PeerApkUpdateManager(
    private val context: Context
) {
    private val tag = "PeerApkUpdateManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val apkServer = OfflineApkServer(context)
    private val isDownloadingFlag = AtomicBoolean(false)

    private val _availableUpdate = MutableStateFlow<PeerUpdateInfo?>(null)
    val availableUpdate: StateFlow<PeerUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    val currentVersionCode: Long by lazy {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to read versionCode: ${e.message}")
            1L
        }
    }

    val currentVersionName: String by lazy {
        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    val serverPort: Int
        get() = apkServer.port

    fun startServer(preferredPort: Int = 42531) {
        scope.launch {
            try {
                apkServer.start(preferredPort)
                Log.i(tag, "Local APK update server active on port ${apkServer.port} (serving build #$currentVersionCode)")
            } catch (e: Exception) {
                Log.w(tag, "Failed to start local APK update server: ${e.message}")
            }
        }
    }

    fun stopServer() {
        try {
            apkServer.stop()
        } catch (_: Exception) {}
    }

    /**
     * Called when a peer handshake reports their app version.
     * If the peer's build number is higher than ours, kicks off background download.
     */
    fun onPeerVersionDiscovered(
        peerId: String,
        peerName: String,
        remoteHost: String,
        remotePort: Int,
        remoteVersionCode: Long,
        remoteVersionName: String
    ) {
        if (remoteVersionCode <= currentVersionCode) {
            return
        }

        // Already have an equal or higher update ready
        val currentReady = _availableUpdate.value
        if (currentReady != null && currentReady.remoteVersionCode >= remoteVersionCode) {
            return
        }

        // Check if we already have this exact version downloaded and verified in cache
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetApk = File(updatesDir, "mysocial_update_${remoteVersionCode}.apk")
        if (targetApk.exists() && targetApk.length() > 100_000L) {
            if (verifyApk(targetApk, remoteVersionCode)) {
                Log.i(tag, "Found previously verified update APK for build #$remoteVersionCode")
                val info = PeerUpdateInfo(
                    peerId = peerId,
                    peerName = peerName,
                    remoteVersionCode = remoteVersionCode,
                    remoteVersionName = remoteVersionName.ifBlank { "Build $remoteVersionCode" },
                    apkFile = targetApk,
                    fileSizeBytes = targetApk.length()
                )
                _availableUpdate.value = info
                notifyUpdateReady(info)
                return
            } else {
                targetApk.delete()
            }
        }

        if (!isDownloadingFlag.compareAndSet(false, true)) {
            Log.d(tag, "Already downloading an update, skipping duplicate request")
            return
        }

        scope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f

            try {
                val effectiveHost = if (remoteHost == "10.0.2.2" && !Build.FINGERPRINT.startsWith("generic")) "127.0.0.1" else remoteHost
                val downloadUrlStr = "http://$effectiveHost:$remotePort/mysocial.apk"
                Log.i(tag, "Starting P2P download of build #$remoteVersionCode from $peerName at $downloadUrlStr")

                val tempFile = File(updatesDir, "download_${remoteVersionCode}.tmp")
                if (tempFile.exists()) tempFile.delete()

                val url = URL(downloadUrlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MySocial-P2P-Update")
                }

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Server returned HTTP $responseCode")
                }

                val totalLength = conn.contentLength.toLong().takeIf { it > 0 } ?: 10_000_000L
                var bytesReadTotal = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32768)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesReadTotal += read
                            _downloadProgress.value = (bytesReadTotal.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                Log.i(tag, "Downloaded $bytesReadTotal bytes for build #$remoteVersionCode. Verifying signature...")

                if (verifyApk(tempFile, remoteVersionCode)) {
                    if (targetApk.exists()) targetApk.delete()
                    tempFile.renameTo(targetApk)

                    val info = PeerUpdateInfo(
                        peerId = peerId,
                        peerName = peerName,
                        remoteVersionCode = remoteVersionCode,
                        remoteVersionName = remoteVersionName.ifBlank { "Build $remoteVersionCode" },
                        apkFile = targetApk,
                        fileSizeBytes = targetApk.length()
                    )
                    _availableUpdate.value = info
                    notifyUpdateReady(info)
                    Log.i(tag, "✅ Successfully verified and staged update build #$remoteVersionCode from $peerName")
                } else {
                    Log.e(tag, "❌ Verification failed for downloaded APK from $peerName. Discarding.")
                    tempFile.delete()
                }

            } catch (e: Exception) {
                Log.e(tag, "Failed to download update from $peerName: ${e.message}", e)
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
                isDownloadingFlag.set(false)
            }
        }
    }

    /**
     * Cryptographically verifies package name, version, and developer signing certificate.
     */
    private fun verifyApk(apkFile: File, expectedVersionCode: Long): Boolean {
        return try {
            val pm = context.packageManager

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            if (archiveInfo == null) {
                Log.w(tag, "getPackageArchiveInfo returned null for ${apkFile.name}")
                return false
            }

            if (archiveInfo.packageName != context.packageName) {
                Log.w(tag, "Package name mismatch: expected ${context.packageName}, got ${archiveInfo.packageName}")
                return false
            }

            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }

            if (archiveVersionCode < expectedVersionCode) {
                Log.w(tag, "Version code too low: expected $expectedVersionCode, got $archiveVersionCode")
                return false
            }

            // Verify developer signing signatures match the currently installed app
            val currentSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                pInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray().contentHashCode() }
            } else {
                @Suppress("DEPRECATION")
                val pInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                pInfo.signatures?.map { it.toByteArray().contentHashCode() }
            } ?: return false

            val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray().contentHashCode() }
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.signatures?.map { it.toByteArray().contentHashCode() }
            } ?: return false

            val matches = currentSignatures.any { it in archiveSignatures }
            if (!matches) {
                Log.w(tag, "Signatures do not match installed app! Rejecting untrusted APK.")
            }
            matches
        } catch (e: Exception) {
            Log.e(tag, "Error verifying APK: ${e.message}", e)
            false
        }
    }

    /**
     * Fires a high-priority system notification allowing the user to install the update in 1 tap.
     */
    private fun notifyUpdateReady(updateInfo: PeerUpdateInfo) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channelId = "peer_apk_update_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a newer version of MySocial is received from a peer"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val installIntent = createInstallIntent(updateInfo.apkFile)
        val pendingIntent = PendingIntent.getActivity(
            context,
            updateInfo.remoteVersionCode.toInt(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("🚀 App Update Ready (${updateInfo.remoteVersionName})")
            .setContentText("Received from ${updateInfo.peerName} without internet. Tap to install.")
            .setSmallIcon(R.drawable.ic_stat_nearby_peer)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            manager.notify(4001, notification)
        } catch (e: Exception) {
            Log.e(tag, "Failed to post update notification", e)
        }
    }

    fun createInstallIntent(apkFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    fun installUpdate(activityContext: Context, updateInfo: PeerUpdateInfo) {
        try {
            val intent = createInstallIntent(updateInfo.apkFile)
            activityContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch installer intent: ${e.message}", e)
        }
    }

    fun dismissUpdate() {
        _availableUpdate.value = null
    }

    companion object {
        @Volatile
        private var instance: PeerApkUpdateManager? = null

        fun getInstance(context: Context): PeerApkUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: PeerApkUpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

