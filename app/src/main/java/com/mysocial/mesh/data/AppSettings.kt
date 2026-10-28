package com.mysocial.mesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persisted user preferences and mesh operational settings.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncIntervalSeconds = MutableStateFlow(
        prefs.getInt(KEY_SYNC_INTERVAL_SECONDS, DEFAULT_SYNC_INTERVAL_SECONDS)
    )
    val syncIntervalSeconds: StateFlow<Int> = _syncIntervalSeconds.asStateFlow()

    fun setSyncIntervalSeconds(seconds: Int) {
        val validSeconds = when (seconds) {
            5, 10, 30, 60, 300 -> seconds
            else -> DEFAULT_SYNC_INTERVAL_SECONDS
        }
        prefs.edit().putInt(KEY_SYNC_INTERVAL_SECONDS, validSeconds).apply()
        _syncIntervalSeconds.value = validSeconds
    }

    private val _isDarkMode = MutableStateFlow(
        prefs.getBoolean(KEY_DARK_MODE, false)
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _isDarkMode.value = enabled
    }

    // -------------------------------------------------------------
    // HASHTAG RADIO CHANNELS: PINNED & MUTED TAGS
    // -------------------------------------------------------------
    private val _pinnedHashtags = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_PINNED_HASHTAGS, emptySet())?.toSet() ?: emptySet()
    )
    val pinnedHashtags: StateFlow<Set<String>> = _pinnedHashtags.asStateFlow()

    fun pinHashtag(tag: String) {
        val clean = tag.trim().lowercase().removePrefix("#")
        if (clean.isBlank()) return
        val current = _pinnedHashtags.value.toMutableSet()
        current.add(clean)
        prefs.edit().putStringSet(KEY_PINNED_HASHTAGS, current).apply()
        _pinnedHashtags.value = current
        // If it was muted, un-mute it
        unmuteHashtag(clean)
    }

    fun unpinHashtag(tag: String) {
        val clean = tag.trim().lowercase().removePrefix("#")
        val current = _pinnedHashtags.value.toMutableSet()
        if (current.remove(clean)) {
            prefs.edit().putStringSet(KEY_PINNED_HASHTAGS, current).apply()
            _pinnedHashtags.value = current
        }
    }

    private val _mutedHashtags = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_MUTED_HASHTAGS, emptySet())?.toSet() ?: emptySet()
    )
    val mutedHashtags: StateFlow<Set<String>> = _mutedHashtags.asStateFlow()

    fun muteHashtag(tag: String) {
        val clean = tag.trim().lowercase().removePrefix("#")
        if (clean.isBlank()) return
        val current = _mutedHashtags.value.toMutableSet()
        current.add(clean)
        prefs.edit().putStringSet(KEY_MUTED_HASHTAGS, current).apply()
        _mutedHashtags.value = current
        // If it was pinned, un-pin it
        unpinHashtag(clean)
    }

    fun unmuteHashtag(tag: String) {
        val clean = tag.trim().lowercase().removePrefix("#")
        val current = _mutedHashtags.value.toMutableSet()
        if (current.remove(clean)) {
            prefs.edit().putStringSet(KEY_MUTED_HASHTAGS, current).apply()
            _mutedHashtags.value = current
        }
    }

    // -------------------------------------------------------------
    // MULE STORAGE RING-BUFFER LIMIT
    // -------------------------------------------------------------
    private val _maxStrangerMediaCacheMb = MutableStateFlow(
        prefs.getInt(KEY_MAX_STRANGER_MEDIA_CACHE_MB, DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB)
    )
    val maxStrangerMediaCacheMb: StateFlow<Int> = _maxStrangerMediaCacheMb.asStateFlow()

    fun setMaxStrangerMediaCacheMb(mb: Int) {
        val validMb = when (mb) {
            50, 100, 250 -> mb
            else -> DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB
        }
        prefs.edit().putInt(KEY_MAX_STRANGER_MEDIA_CACHE_MB, validMb).apply()
        _maxStrangerMediaCacheMb.value = validMb
    }

    companion object {
        private const val PREFS_NAME = "mysocial_user_settings"
        const val KEY_SYNC_INTERVAL_SECONDS = "sync_interval_seconds"
        const val DEFAULT_SYNC_INTERVAL_SECONDS = 10
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_PINNED_HASHTAGS = "pinned_hashtags"
        const val KEY_MUTED_HASHTAGS = "muted_hashtags"
        const val KEY_MAX_STRANGER_MEDIA_CACHE_MB = "max_stranger_media_cache_mb"
        const val DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB = 100

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
        }
    }
}
