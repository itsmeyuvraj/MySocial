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
        run {
            val migrated = prefs.getBoolean(KEY_DARK_MODE_V2_MIGRATED, false)
            if (!migrated) {
                prefs.edit()
                    .putBoolean(KEY_DARK_MODE, true)
                    .putBoolean(KEY_DARK_MODE_V2_MIGRATED, true)
                    .apply()
                true
            } else {
                prefs.getBoolean(KEY_DARK_MODE, true)
            }
        }
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _isDarkMode.value = enabled
    }

    // -------------------------------------------------------------
    // THEME & APPEARANCE PREFERENCES
    // -------------------------------------------------------------
    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, if (_isDarkMode.value) THEME_DARK else THEME_LIGHT) ?: THEME_DARK
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        val validMode = when (mode) {
            THEME_SYSTEM, THEME_LIGHT, THEME_DARK, THEME_AMOLED -> mode
            else -> THEME_DARK
        }
        prefs.edit().putString(KEY_THEME_MODE, validMode).apply()
        _themeMode.value = validMode
        // Sync isDarkMode state
        when (validMode) {
            THEME_LIGHT -> setDarkMode(false)
            THEME_DARK, THEME_AMOLED -> setDarkMode(true)
            THEME_SYSTEM -> { /* resolved at runtime */ }
        }
    }

    private val _accentColor = MutableStateFlow(
        prefs.getString(KEY_ACCENT_COLOR, ACCENT_BLUE) ?: ACCENT_BLUE
    )
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    fun setAccentColor(accent: String) {
        val validAccent = when (accent) {
            ACCENT_BLUE, ACCENT_EMERALD, ACCENT_PURPLE, ACCENT_ROSE, ACCENT_AMBER -> accent
            else -> ACCENT_BLUE
        }
        prefs.edit().putString(KEY_ACCENT_COLOR, validAccent).apply()
        _accentColor.value = validAccent
    }

    private val _compactFeedMode = MutableStateFlow(
        prefs.getBoolean(KEY_COMPACT_FEED_MODE, false)
    )
    val compactFeedMode: StateFlow<Boolean> = _compactFeedMode.asStateFlow()

    fun setCompactFeedMode(compact: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_FEED_MODE, compact).apply()
        _compactFeedMode.value = compact
    }

    private val _showHopBadges = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_HOP_BADGES, true)
    )
    val showHopBadges: StateFlow<Boolean> = _showHopBadges.asStateFlow()

    fun setShowHopBadges(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HOP_BADGES, show).apply()
        _showHopBadges.value = show
    }

    // -------------------------------------------------------------
    // ONBOARDING & TERMS OF SERVICE (GOOGLE PLAY COMPLIANCE)
    // -------------------------------------------------------------
    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean(KEY_COMPLETED_ONBOARDING, false)
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    fun setCompletedOnboarding(completed: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETED_ONBOARDING, completed).apply()
        _hasCompletedOnboarding.value = completed
    }

    private val _hasAcceptedTerms = MutableStateFlow(
        prefs.getBoolean(KEY_ACCEPTED_TERMS, false)
    )
    val hasAcceptedTerms: StateFlow<Boolean> = _hasAcceptedTerms.asStateFlow()

    fun setAcceptedTerms(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_ACCEPTED_TERMS, accepted).apply()
        _hasAcceptedTerms.value = accepted
    }

    // -------------------------------------------------------------
    // LOCAL CONTENT MODERATION: REPORTED POSTS
    // -------------------------------------------------------------
    private val _reportedPosts = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_REPORTED_POSTS, emptySet())?.toSet() ?: emptySet()
    )
    val reportedPosts: StateFlow<Set<String>> = _reportedPosts.asStateFlow()

    fun reportPost(entryHash: String) {
        val current = _reportedPosts.value.toMutableSet()
        current.add(entryHash)
        prefs.edit().putStringSet(KEY_REPORTED_POSTS, current).apply()
        _reportedPosts.value = current
    }

    fun isPostReported(entryHash: String): Boolean {
        return _reportedPosts.value.contains(entryHash)
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
        val validMb = if (mb in 1..250) mb else DEFAULT_MAX_STRANGER_MEDIA_CACHE_MB
        prefs.edit().putInt(KEY_MAX_STRANGER_MEDIA_CACHE_MB, validMb).apply()
        _maxStrangerMediaCacheMb.value = validMb
    }

    companion object {
        private const val PREFS_NAME = "mysocial_user_settings"
        const val KEY_SYNC_INTERVAL_SECONDS = "sync_interval_seconds"
        const val DEFAULT_SYNC_INTERVAL_SECONDS = 10
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_DARK_MODE_V2_MIGRATED = "dark_mode_v2_migrated"
        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val THEME_AMOLED = "AMOLED"

        const val KEY_ACCENT_COLOR = "accent_color"
        const val ACCENT_BLUE = "BLUE"
        const val ACCENT_EMERALD = "EMERALD"
        const val ACCENT_PURPLE = "PURPLE"
        const val ACCENT_ROSE = "ROSE"
        const val ACCENT_AMBER = "AMBER"

        const val KEY_COMPACT_FEED_MODE = "compact_feed_mode"
        const val KEY_SHOW_HOP_BADGES = "show_hop_badges"
        const val KEY_COMPLETED_ONBOARDING = "completed_onboarding"
        const val KEY_ACCEPTED_TERMS = "accepted_terms"
        const val KEY_REPORTED_POSTS = "reported_posts"
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

        fun resetInstanceForTesting() {
            instance = null
        }
    }
}
