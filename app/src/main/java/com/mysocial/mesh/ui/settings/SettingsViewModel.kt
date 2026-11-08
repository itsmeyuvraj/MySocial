package com.mysocial.mesh.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.data.DatabaseStats
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.MySocialApplication
import com.mysocial.mesh.utils.BatteryOptimizationHelper
import com.mysocial.mesh.utils.toFormattedBbid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val dbStats: StateFlow<DatabaseStats> = repository.observeDatabaseStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DatabaseStats(0, 0, 0))

    val syncIntervalSeconds: StateFlow<Int> =
        repository.appSettings?.syncIntervalSeconds
            ?: MutableStateFlow(10).asStateFlow()

    fun setSyncInterval(seconds: Int) {
        repository.appSettings?.setSyncIntervalSeconds(seconds)
        repository.logActivity("Update check set to $seconds seconds")
    }

    val isDarkMode: StateFlow<Boolean> =
        repository.appSettings?.isDarkMode
            ?: MutableStateFlow(false).asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        repository.appSettings?.setDarkMode(enabled)
    }

    val themeMode: StateFlow<String> =
        repository.appSettings?.themeMode
            ?: MutableStateFlow(AppSettings.THEME_DARK).asStateFlow()

    fun setThemeMode(mode: String) {
        repository.appSettings?.setThemeMode(mode)
    }

    val accentColor: StateFlow<String> =
        repository.appSettings?.accentColor
            ?: MutableStateFlow(AppSettings.ACCENT_BLUE).asStateFlow()

    fun setAccentColor(accent: String) {
        repository.appSettings?.setAccentColor(accent)
    }

    val compactFeedMode: StateFlow<Boolean> =
        repository.appSettings?.compactFeedMode
            ?: MutableStateFlow(false).asStateFlow()

    fun setCompactFeedMode(compact: Boolean) {
        repository.appSettings?.setCompactFeedMode(compact)
    }

    val showHopBadges: StateFlow<Boolean> =
        repository.appSettings?.showHopBadges
            ?: MutableStateFlow(true).asStateFlow()

    fun setShowHopBadges(show: Boolean) {
        repository.appSettings?.setShowHopBadges(show)
    }

    val maxStrangerMediaCacheMb: StateFlow<Int> = (repository.appSettings?.maxStrangerMediaCacheMb ?: flowOf(AppSettings.DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings.DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB)

    private val _storageUsageBytes = MutableStateFlow(0L)
    val storageUsageBytes: StateFlow<Long> = _storageUsageBytes.asStateFlow()

    fun refreshStorageUsage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _storageUsageBytes.value = repository.getBlobsDirectorySizeBytes(context)
        }
    }

    fun setMaxStrangerMediaCacheMb(mb: Int) {
        viewModelScope.launch {
            repository.appSettings?.setMaxStrangerMediaCacheMb(mb)
        }
    }

    val blockedAuthors: StateFlow<List<com.mysocial.mesh.model.BlockedAuthorEntity>> =
        repository.observeBlockedAuthors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reportedPosts: StateFlow<Set<String>> =
        repository.appSettings?.reportedPosts
            ?: MutableStateFlow(emptySet<String>()).asStateFlow()

    fun unblockAuthor(authorId: String) {
        viewModelScope.launch {
            repository.unblockAuthor(authorId)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
    }

    suspend fun generateDiagnosticReport(context: Context): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val packageInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 0L
        }

        val authorId = repository.cryptoManager.authorId
        val bbid = authorId.toFormattedBbid()
        val stats = dbStats.value
        val peers = repository.discoveredPeers.value
        val blockedCount = blockedAuthors.value.size
        val reportedCount = reportedPosts.value.size
        val isDozeExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        val storageUsage = repository.getBlobsDirectorySizeBytes(context) / 1024 / 1024
        val maxStrangerMedia = maxStrangerMediaCacheMb.value

        val bluetoothAdapter = try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            bm?.adapter
        } catch (_: Exception) { null }

        val bleSupported = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
        val bleMultiAdv = bluetoothAdapter?.isMultipleAdvertisementSupported ?: false
        val bleEnabled = bluetoothAdapter?.isEnabled ?: false

        val recentLogs = repository.activityLogs.value.take(15)
        val latestCrash = MySocialApplication.getLatestCrashDump(context)

        return buildString {
            appendLine("=========================================")
            appendLine("MYSOCIAL OFFLINE DIAGNOSTIC TELEMETRY")
            appendLine("Generated At: $now")
            appendLine("=========================================")
            appendLine()
            appendLine("1. APPLICATION & OS INFO:")
            appendLine("   - Version: v$versionName (Build $versionCode)")
            appendLine("   - Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
            appendLine("   - Android OS: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("   - Battery Doze Exemption: ${if (isDozeExempt) "EXEMPTED (Continuous)" else "RESTRICTED (Doze active)"}")
            appendLine()
            appendLine("2. CRYPTOGRAPHIC IDENTITY & SECURITY:")
            appendLine("   - Author ID: $authorId")
            appendLine("   - Public Badge ID: $bbid")
            appendLine("   - Blocked Authors: $blockedCount")
            appendLine("   - Local Reported Posts: $reportedCount")
            appendLine()
            appendLine("3. HARDWARE RADIO CAPABILITIES:")
            appendLine("   - BLE Feature Present: $bleSupported")
            appendLine("   - Bluetooth Enabled: $bleEnabled")
            appendLine("   - Multiple Advertisement Supported: $bleMultiAdv")
            appendLine("   - Nearby Discovered Peers (Cache): ${peers.size}")
            appendLine()
            appendLine("4. DATABASE & STORAGE METRICS:")
            appendLine("   - Total Log Entries: ${stats.totalEntries}")
            appendLine("   - Stored Authors: ${stats.totalAuthors}")
            appendLine("   - Published Posts: ${stats.totalPosts}")
            appendLine("   - Media Storage Used: ${storageUsage} MB")
            appendLine("   - Stranger Media Cap: ${maxStrangerMedia} MB")
            appendLine()
            appendLine("5. RECENT ACTIVITY LOGS (Last 15 Events):")
            if (recentLogs.isEmpty()) {
                appendLine("   (No activity events logged)")
            } else {
                recentLogs.forEach { log ->
                    appendLine("   • $log")
                }
            }
            appendLine()
            if (!latestCrash.isNullOrBlank()) {
                appendLine("6. LAST PERSISTED CRASH DUMP:")
                appendLine(latestCrash)
            } else {
                appendLine("6. CRASH STATUS: Clean (No crash dumps on record)")
            }
            appendLine("=========================================")
            appendLine("End of MySocial Diagnostics Report")
        }
    }
}
