package com.mysocial.mesh.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersionName: String,
    val latestVersionCode: Long = 0L,
    val releaseNotes: String = "",
    val downloadUrl: String = ""
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_REPO = "itsmeyuvraj/MySocial"
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    val currentVersionName: String
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.15"
        } catch (_: Exception) {
            "1.0.15"
        }

    val currentVersionCode: Long
        get() = try {
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
        } catch (_: Exception) {
            15L
        }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        var latestFoundVersionName = currentVersionName
        var latestFoundVersionCode = currentVersionCode
        var latestFoundReleaseNotes = ""

        try {
            val ghReq = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MySocial-Android")
                .build()
            val ghResp = client.newCall(ghReq).execute()
            if (ghResp.isSuccessful) {
                val jsonStr = ghResp.body?.string()
                ghResp.close()
                if (!jsonStr.isNullOrBlank()) {
                    val root = JSONObject(jsonStr)
                    val tagName = root.optString("tag_name", "")
                    val body = root.optString("body", "")

                    if (body.isNotBlank() && latestFoundReleaseNotes.isBlank()) {
                        latestFoundReleaseNotes = body
                        if (tagName.isNotBlank()) latestFoundVersionName = tagName
                    }

                    var apkDownloadUrl = ""
                    val assets = root.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name", "")
                            val download = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = download
                                break
                            }
                        }
                    }

                    if (apkDownloadUrl.isBlank() && tagName.isNotBlank()) {
                        apkDownloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tagName/MySocial.apk"
                    }

                    if (tagName.isNotBlank() && isNewerVersion(tagName, currentVersionName)) {
                        Log.d(TAG, "Update found via GitHub Releases: $tagName")
                        return@withContext AppUpdateInfo(
                            isUpdateAvailable = true,
                            latestVersionName = tagName,
                            latestVersionCode = currentVersionCode + 1,
                            releaseNotes = body.ifBlank { "Bug fixes and performance improvements" },
                            downloadUrl = apkDownloadUrl
                        )
                    }
                }
            } else {
                ghResp.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "GitHub update check skipped: ${e.localizedMessage}")
        }

        // Return current version status
        return@withContext AppUpdateInfo(
            isUpdateAvailable = false,
            latestVersionName = latestFoundVersionName.ifBlank { currentVersionName },
            latestVersionCode = if (latestFoundVersionCode > 0) latestFoundVersionCode else currentVersionCode,
            releaseNotes = latestFoundReleaseNotes.ifBlank { getEmbeddedReleaseNotes() },
            downloadUrl = ""
        )
    }

    fun getEmbeddedReleaseNotes(): String {
        return try {
            context.assets.open("release_notes.txt").bufferedReader().use { it.readText() }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "MySocial-Android")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return@withContext null
            }

            val body = resp.body ?: return@withContext null
            val contentLength = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val apkFile = File(updatesDir, "MySocial_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            resp.close()
            return@withContext apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update APK: ${e.localizedMessage}", e)
            return@withContext null
        }
    }

    fun promptInstall(apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ verify package install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger APK installer: ${e.localizedMessage}", e)
        }
    }

    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trimStart('v', 'V').trim()
        val cleanCurrent = currentVersion.trimStart('v', 'V').trim()

        val remoteParts = cleanRemote.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val currentParts = cleanCurrent.split(".").mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
